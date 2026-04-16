package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.regex.Pattern;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.item.ItemStack;

import org.jetbrains.annotations.UnknownNullability;
import org.lwjgl.input.Keyboard;

import com.cleanroommc.modularui.api.UpOrDown;
import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.utils.GlStateManager;
import com.cleanroommc.modularui.value.StringValue;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.ScrollWidget;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widget.scroll.VerticalScrollData;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.client.CelestialClient;
import com.gtnewhorizons.galaxia.client.EnumColors;
import com.gtnewhorizons.galaxia.client.gui.mui.ItemPickerScreen;
import com.gtnewhorizons.galaxia.compat.GTUtility;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.core.network.LogisticsConfigUpdatePacket;
import com.gtnewhorizons.galaxia.core.network.OutpostBuildModulePacket;
import com.gtnewhorizons.galaxia.core.network.OutpostInventoryUpdatePacket;
import com.gtnewhorizons.galaxia.core.network.OutpostModuleUpdatePacket;
import com.gtnewhorizons.galaxia.core.network.OutpostRequestSyncPacket;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalTransferPlanner;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedOutpost;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.LogisticsResourceConfig;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.AllowShootingConfig;
import com.gtnewhorizons.galaxia.registry.outpost.module.AutomatedOutpostModule;
import com.gtnewhorizons.galaxia.registry.outpost.module.IHammer;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleBigHammer;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleMiner;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModulePower;
import com.gtnewhorizons.galaxia.registry.outpost.module.OutpostModuleKind;

import codechicken.nei.recipe.GuiCraftingRecipe;
import codechicken.nei.recipe.GuiUsageRecipe;

record ButtonRect(int left, int top, int right, int bottom) {

    boolean contains(int x, int y) {
        return x >= left && x <= right && y >= top && y <= bottom;
    }
}

record ModalBounds(int left, int top, int right, int bottom) {}

record PendingAssetCreation(CelestialObjectId celestialObjectId, String displayName, CelestialAsset.Kind kind,
    CelestialAsset.Location location, Map<ItemStack, Long> requiredResources) {}

record PendingAssetRename(CelestialAsset asset) {}

record PendingAssetDestruction(CelestialAsset asset, boolean armed) {}

record PendingAssetManagement(CelestialAsset asset) {}

record PendingConstructionCancellation(CelestialAsset asset) {}

record PendingResourceTransfer(CelestialAsset asset, List<StationTransferTarget> targets) {}

record StationTransferTarget(CelestialAsset.ID assetId, String displayName, CelestialObject hostBody) {}

record TransferTargetRow(StationTransferTarget target, int left, int top, int right, int bottom,
    ButtonRect sendButton) {}

record PinnedInfoRow(String label, String value, List<ItemStack> items, boolean inlineItems) {

    static PinnedInfoRow section(String label) {
        return new PinnedInfoRow(label, "", List.of(), false);
    }

    static PinnedInfoRow inlineItems(String value, List<ItemStack> items) {
        return new PinnedInfoRow("", value, items, true);
    }

    PinnedInfoRow(String label, String value) {
        this(label, value, List.of(), false);
    }

    PinnedInfoRow(String label, String value, List<ItemStack> items) {
        this(label, value, items, false);
    }
}

record MinerOreOption(String key, String displayName, ItemStack displayStack, boolean blacklisted) {}

enum InventorySortMode {
    NAME,
    AMOUNT
}

public final class AssetManagementSystem {

    public static final class OrbitalAssetSupport {

        boolean hasStoredConstructionResources(CelestialAsset asset) {
            return asset != null && asset.hasStoredConstructionResources();
        }

        boolean isManageableStationAsset(CelestialAsset asset) {
            return asset != null && asset.isManageable();
        }

        String formatAssetDisplayName(CelestialAsset asset) {
            // TODO: Localize
            return switch (asset.status()) {
                case CONSTRUCTION_SITE -> asset.displayName() + " (In construction)";
                case DECONSTRUCTION -> asset.displayName() + " (Deconstruction)";
                default -> asset.displayName();
            };
        }

        String buildConstructionInventorySummary(CelestialAsset asset) {
            if (asset.status() == CelestialAsset.Status.DECONSTRUCTION)
                return buildStoredInventorySummary(asset.constructionInventory());
            if (asset.requiredResources()
                .isEmpty()) return "Empty";
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<ItemStack, Long> required : asset.requiredResources()
                .entrySet()) {
                long storedAmount = asset.constructionInventory()
                    .getOrDefault(required.getKey(), 0L);
                if (sb.length() > 0) sb.append(", ");
                sb.append(storedAmount)
                    .append('/')
                    .append(required.getValue())
                    .append(' ')
                    .append(
                        required.getKey()
                            .getDisplayName());
            }
            return sb.toString();
        }

        List<StationTransferTarget> getTransferTargetsInSystem(CelestialObject root, CelestialObject body) {
            List<StationTransferTarget> targets = new ArrayList<>();
            if (body == null) return targets;
            for (CelestialClient.TransferTarget t : CelestialClient.getTransferTargetsInSystem(root, body)) {
                targets.add(new StationTransferTarget(t.assetId(), t.displayName(), t.hostBody()));
            }
            return targets;
        }

        String formatAssetKind(CelestialAsset.Kind kind) {
            return kind.getDisplayName();
        }

        String formatAssetLocation(CelestialAsset.Location location) {
            return location.getDisplayName();
        }

        private String buildStoredInventorySummary(Map<ItemStack, Long> storedResources) {
            // TODO: Localize
            if (storedResources.isEmpty()) return "Empty";
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<ItemStack, Long> stored : storedResources.entrySet()) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(stored.getValue())
                    .append(' ')
                    .append(
                        stored.getKey()
                            .getDisplayName());
            }
            return sb.toString();
        }

    }

    public static final class OrbitalAssetActionController {

        interface Callbacks {

            boolean isCreativeBuildModeEnabled();

            void showActionStatus(String message);

            void beginRenameInput(String currentText);

            void endRenameInput();

            String getRenameInput();

            void createResourceTransfer(CelestialObject sourceBody, CelestialAsset sourceAsset,
                StationTransferTarget target);
        }

        private final OrbitalAssetSupport assetSupport;
        private final Callbacks callbacks;

        OrbitalAssetActionController(OrbitalAssetSupport assetSupport, Callbacks callbacks) {
            this.assetSupport = assetSupport;
            this.callbacks = callbacks;
        }

        void openAssetManagement(OrbitalAssetUiState state, CelestialObject body) {
            if (body == null || body.objectClass() == CelestialObject.Class.GALAXY) return;
            state.openAssetManagement(body);
            closePendingAssetRename(state);
        }

        void closeAssetManagement(OrbitalAssetUiState state) {
            state.closeAssetManagement();
            closePendingAssetRename(state);
        }

        void createBaseStation(CelestialObject body) {
            if (body == null) return;
            System.out.println("here 2");
            CelestialClient.createOperationalAsset(
                body.id(),
                buildDefaultAssetDisplayName(body, CelestialAsset.Kind.STATION),
                CelestialAsset.Kind.STATION);
            // TODO: Localize
            callbacks.showActionStatus("Station created");
        }

        void triggerAssetCreation(OrbitalAssetUiState state, CelestialObject body, CelestialAsset.Kind kind,
            boolean openManagementFirst) {
            if (body == null) return;
            if (openManagementFirst) openAssetManagement(state, body);
            CelestialAsset.Location location = getDefaultAssetLocation(kind);
            String displayName = buildDefaultAssetDisplayName(body, kind);
            if (callbacks.isCreativeBuildModeEnabled()) {
                System.out.println("here 1");
                CelestialClient.createOperationalAsset(body.id(), displayName, kind);
                callbacks.showActionStatus(assetSupport.formatAssetKind(kind) + " created");
                return;
            }
            state.pendingAssetCreation = new PendingAssetCreation(
                body.id(),
                displayName,
                kind,
                location,
                CelestialAsset.defaultRequirements(kind));
        }

        void confirmPendingAssetCreation(OrbitalAssetUiState state) {
            if (state.pendingAssetCreation == null) return;
            if (callbacks.isCreativeBuildModeEnabled()) {
                CelestialClient.createOperationalAsset(
                    state.pendingAssetCreation.celestialObjectId(),
                    state.pendingAssetCreation.displayName(),
                    state.pendingAssetCreation.kind());
                callbacks
                    // TODO: Localize
                    .showActionStatus(assetSupport.formatAssetKind(state.pendingAssetCreation.kind()) + " created");
            } else {
                CelestialClient.createAssetInConstruction(
                    state.pendingAssetCreation.celestialObjectId(),
                    state.pendingAssetCreation.displayName(),
                    state.pendingAssetCreation.kind());
                callbacks.showActionStatus(
                    // TODO: Localize
                    assetSupport.formatAssetKind(state.pendingAssetCreation.kind()) + " construction planned");
            }
            state.pendingAssetCreation = null;
        }

        void dismissPendingAssetCreation(OrbitalAssetUiState state) {
            state.pendingAssetCreation = null;
        }

        void openPendingAssetRename(OrbitalAssetUiState state, CelestialAsset asset) {
            if (asset == null) return;
            state.pendingAssetRename = new PendingAssetRename(asset);
            callbacks.beginRenameInput(asset.displayName());
        }

        void closePendingAssetRename(OrbitalAssetUiState state) {
            state.pendingAssetRename = null;
            callbacks.endRenameInput();
        }

        void openPendingAssetDestruction(OrbitalAssetUiState state, CelestialAsset asset) {
            if (asset == null) return;
            state.pendingAssetDestruction = new PendingAssetDestruction(asset, false);
        }

        void dismissPendingAssetDestruction(OrbitalAssetUiState state) {
            state.pendingAssetDestruction = null;
        }

        void advancePendingAssetDestruction(OrbitalAssetUiState state) {
            if (state.pendingAssetDestruction == null) return;
            if (!state.pendingAssetDestruction.armed()) {
                state.pendingAssetDestruction = new PendingAssetDestruction(
                    state.pendingAssetDestruction.asset(),
                    true);
                return;
            }
            CelestialAssetStore.destroyAsset(state.pendingAssetDestruction.asset().assetId);
            // TODO: Localize
            callbacks.showActionStatus("Asset destroyed");
            state.pendingAssetDestruction = null;
        }

        void openPendingAssetManagement(OrbitalAssetUiState state, CelestialAsset asset) {
            if (asset == null || !assetSupport.isManageableStationAsset(asset)) return;
            state.pendingAssetManagement = new PendingAssetManagement(asset);
        }

        void closePendingAssetManagement(OrbitalAssetUiState state) {
            state.pendingAssetManagement = null;
        }

        void openPendingConstructionCancellation(OrbitalAssetUiState state, CelestialAsset asset) {
            if (asset == null) return;
            state.pendingConstructionCancellation = new PendingConstructionCancellation(asset);
        }

        void dismissPendingConstructionCancellation(OrbitalAssetUiState state) {
            state.pendingConstructionCancellation = null;
        }

        void confirmPendingConstructionCancellation(OrbitalAssetUiState state) {
            if (state.pendingConstructionCancellation == null) return;
            CelestialAssetStore.startDeconstruction(state.pendingConstructionCancellation.asset().assetId);
            // TODO: Localize
            callbacks.showActionStatus("Construction site converted to deconstruction");
            state.pendingConstructionCancellation = null;
        }

        void openPendingResourceTransfer(OrbitalAssetUiState state, CelestialObject root, CelestialAsset asset) {
            if (asset == null) return;
            state.pendingResourceTransfer = new PendingResourceTransfer(
                asset,
                assetSupport.getTransferTargetsInSystem(root, state.assetManagementBody));
        }

        void dismissPendingResourceTransfer(OrbitalAssetUiState state) {
            state.pendingResourceTransfer = null;
        }

        void sendPendingResourceTransfer(OrbitalAssetUiState state, StationTransferTarget target) {
            if (state.pendingResourceTransfer != null) {
                callbacks
                    .createResourceTransfer(state.assetManagementBody, state.pendingResourceTransfer.asset(), target);
            }
            state.pendingResourceTransfer = null;
        }

        void confirmPendingAssetRename(OrbitalAssetUiState state) {
            if (state.pendingAssetRename == null) return;
            String renamed = callbacks.getRenameInput()
                .trim();
            if (renamed.isEmpty()) {
                // TODO: Localize
                callbacks.showActionStatus("Name cannot be empty");
                return;
            }
            if (renamed.equals(
                state.pendingAssetRename.asset()
                    .displayName())) {
                closePendingAssetRename(state);
                return;
            }
            if (CelestialAssetStore.renameAsset(state.pendingAssetRename.asset().assetId, renamed)) {
                // TODO: Localize
                callbacks.showActionStatus("Asset renamed");
                closePendingAssetRename(state);
                return;
            }
            // TODO: Localize
            callbacks.showActionStatus("Rename failed");
        }

        void dismissPendingModalByOutsideClick(OrbitalAssetUiState state) {
            if (state.pendingAssetRename != null) {
                closePendingAssetRename(state);
                return;
            }
            if (state.pendingResourceTransfer != null) {
                dismissPendingResourceTransfer(state);
                return;
            }
            if (state.pendingAssetManagement != null) {
                closePendingAssetManagement(state);
                return;
            }
            if (state.pendingConstructionCancellation != null) {
                dismissPendingConstructionCancellation(state);
                return;
            }
            if (state.pendingAssetDestruction != null) {
                dismissPendingAssetDestruction(state);
                return;
            }
            if (state.pendingAssetCreation != null) dismissPendingAssetCreation(state);
        }

        private String buildDefaultAssetDisplayName(CelestialObject body, CelestialAsset.Kind kind) {
            return body.displayName() + " " + assetSupport.formatAssetKind(kind);
        }

        private CelestialAsset.Location getDefaultAssetLocation(CelestialAsset.Kind kind) {
            return kind == CelestialAsset.Kind.AUTOMATED_OUTPOST ? CelestialAsset.Location.SURFACE
                : CelestialAsset.Location.ORBIT;
        }
    }

    public static final class OrbitalAssetUiState {

        CelestialObject assetManagementBody;
        PendingAssetCreation pendingAssetCreation;
        PendingAssetDestruction pendingAssetDestruction;
        PendingConstructionCancellation pendingConstructionCancellation;
        PendingResourceTransfer pendingResourceTransfer;
        PendingAssetManagement pendingAssetManagement;
        PendingAssetRename pendingAssetRename;
        int assetManagementTab = 0;
        /** Index of the module whose configuration sub-menu is open; -1 when none. */
        int configuringModuleIndex = -1;
        boolean selectingModuleBuild = false;
        InventorySortMode inventorySortMode = InventorySortMode.NAME;
        boolean inventorySortAscending = true;
        int armedModuleDestroyIndex = -1;
        String armedDumpResourceKey;
        int modulesScrollPosition = 0;
        int inventoryScrollPosition = 0;
        int logisticsScrollPosition = 0;
        int minerConfigScrollPosition = 0;
        int buildModuleScrollPosition = 0;

        boolean isAssetManagementOpen() {
            return assetManagementBody != null;
        }

        boolean hasBlockingModal() {
            return pendingAssetCreation != null || pendingAssetDestruction != null
                || pendingConstructionCancellation != null
                || pendingResourceTransfer != null
                || pendingAssetManagement != null
                || pendingAssetRename != null;
        }

        void openAssetManagement(CelestialObject body) {
            assetManagementBody = body;
            clearTransientState();
        }

        void closeAssetManagement() {
            assetManagementBody = null;
            clearTransientState();
        }

        void clearTransientState() {
            pendingAssetCreation = null;
            pendingAssetDestruction = null;
            pendingConstructionCancellation = null;
            pendingResourceTransfer = null;
            pendingAssetManagement = null;
            pendingAssetRename = null;
            assetManagementTab = 0;
            configuringModuleIndex = -1;
            selectingModuleBuild = false;
            inventorySortMode = InventorySortMode.NAME;
            inventorySortAscending = true;
            armedModuleDestroyIndex = -1;
            armedDumpResourceKey = null;
            modulesScrollPosition = 0;
            inventoryScrollPosition = 0;
            logisticsScrollPosition = 0;
            minerConfigScrollPosition = 0;
            buildModuleScrollPosition = 0;
        }
    }

    public static final class OrbitalAssetManagementWidget extends ParentWidget<OrbitalAssetManagementWidget> {

        interface Callbacks {

            int getViewportWidth();

            int getViewportHeight();

            boolean isCreativeBuildModeEnabled();

            boolean isGT5AutomationAvailable();

            boolean canCreateBaseStation(CelestialObject body);

            boolean canCreateAutomatedStation(CelestialObject body);

            boolean canCreateAutomatedOutpost(CelestialObject body);

            boolean hasStoredConstructionResources(CelestialAsset asset);

            boolean isManageableStationAsset(CelestialAsset asset);

            String formatAssetDisplayName(CelestialAsset asset);

            String buildConstructionInventorySummary(CelestialAsset asset);

            String formatAssetKind(CelestialAsset.Kind kind);

            String formatAssetLocation(CelestialAsset.Location location);

            void drawAssetIcon(CelestialAsset.Kind kind, int x, int y, int size, float alpha);

            void closeAssetManagement();

            void createBaseStation(CelestialObject body);

            void triggerAssetCreation(CelestialObject body, CelestialAsset.Kind kind, boolean openManagementFirst);

            void openPendingAssetRename(CelestialAsset asset);

            void openPendingConstructionCancellation(CelestialAsset asset);

            void openPendingResourceTransfer(CelestialAsset asset);

            void openPendingAssetManagement(CelestialAsset asset);

            void openPendingAssetDestruction(CelestialAsset asset);

            void confirmPendingAssetCreation();

            void dismissPendingAssetCreation();

            void closePendingAssetRename();

            void confirmPendingAssetRename();

            void dismissPendingAssetDestruction();

            void advancePendingAssetDestruction();

            void dismissPendingConstructionCancellation();

            void confirmPendingConstructionCancellation();

            void dismissPendingResourceTransfer();

            void sendPendingResourceTransfer(StationTransferTarget target);

            void closePendingAssetManagement();

            void dismissPendingModalByOutsideClick();

            void showActionStatus(String message);
        }

        private static final int MODAL_MAX_WIDTH = 620;
        private static final int MODAL_MAX_HEIGHT = 440;
        private static final int MODAL_MARGIN_X = 80;
        private static final int MODAL_MARGIN_Y = 60;
        private static final int HEADER_HEIGHT = 28;
        private static final int CONTENT_TOP = 54;
        private static final int CONTENT_PADDING = 10;
        private static final int CONTENT_SCROLLBAR_GAP = 14;
        private static final int ROW_HEIGHT = 42;
        private static final int ROW_SPACING = 6;
        private static final int ICON_BUTTON_SIZE = 22;
        private static final int FOOTER_BUTTON_HEIGHT = 20;
        private static final int RENAME_INPUT_HEIGHT = 22;
        private static final int RENAME_MODAL_WIDTH = 340;
        private static final int RENAME_INPUT_PADDING = 14;

        private final OrbitalAssetUiState state;
        private final Callbacks callbacks;

        private int structureVersion = 0;
        private int contentVersion = 0;
        private int lastStructureVersion = -1;
        private int lastContentVersion = -1;
        private boolean lastOutpostStatePresent = false;
        private int lastOutpostSyncRevision = -1;
        private int deferredOutpostSyncRevision = -1;

        private int modalLeft, modalTop, modalRight, modalBottom;
        private int scrollLeft, scrollTop, scrollRight, scrollBottom;
        private ScrollWidget<?> activeScrollWidget;
        private ScrollWidget<?> mainScrollWidget;
        private ParentWidget<?> mainScrollContent;
        private VerticalScrollData mainScrollData;
        private ScrollWidget<?> modalScrollWidget;
        private VerticalScrollData modalScrollData;
        private int modalScrollPosition;
        private int mainContentWidth, mainContentHeight;
        private final List<TextFieldWidget> modalTextFields = new ArrayList<>();

        OrbitalAssetManagementWidget(OrbitalAssetUiState state, Callbacks callbacks) {
            this.state = state;
            this.callbacks = callbacks;
            setEnabled(false);
            size(0, 0);
            background(
                drawable(
                    (c, x, y, w, h) -> Gui.drawRect(x, y, x + w, y + h, EnumColors.MAP_COLOR_OVERLAY_BG.getColor())));
        }

        @Override
        public boolean canHoverThrough() {
            return true;
        }

        public void markStructureDirty() {
            structureVersion++;
        }

        public void markContentDirty() {
            contentVersion++;
        }

        boolean isPointInScrollViewport(int localX, int localY) {
            return shouldShowPanel() && localX >= scrollLeft
                && localX <= scrollRight
                && localY >= scrollTop
                && localY <= scrollBottom;
        }

        ButtonRect getRenameInputBounds() {
            if (state.pendingAssetRename == null) return null;
            ModalBounds bounds = createCenteredModalBounds(RENAME_MODAL_WIDTH, 126);
            return new ButtonRect(
                bounds.left() + RENAME_INPUT_PADDING,
                bounds.top() + CONTENT_TOP + 4,
                bounds.right() - RENAME_INPUT_PADDING,
                bounds.top() + CONTENT_TOP + 4 + RENAME_INPUT_HEIGHT);
        }

        @Override
        public void onUpdate() {
            super.onUpdate();

            boolean visible = shouldShowOverlay();
            if (!visible) {
                if (isEnabled()) {
                    removeAll();
                    scheduleResize();
                }
                clearBounds();
                clearMainPanelState();
                activeScrollWidget = null;
                lastStructureVersion = -1;
                lastContentVersion = -1;
                lastOutpostStatePresent = false;
                lastOutpostSyncRevision = -1;
                setEnabled(false);
                size(0, 0);
                return;
            }

            setEnabled(true);
            size(callbacks.getViewportWidth(), callbacks.getViewportHeight());

            // Handle asynchronous data arrival for automated outposts
            if (state.pendingAssetManagement != null) {
                boolean present = CelestialClient.getByAssetId(state.pendingAssetManagement.asset().assetId) != null;
                if (present && !lastOutpostStatePresent) {
                    markStructureDirty();
                }
                if (present) {
                    CelestialAsset asset = CelestialClient.getByAssetId(state.pendingAssetManagement.asset().assetId);
                    if (asset instanceof AutomatedOutpost outpost) {
                        if (outpost != null && outpost.getSyncRevision() != lastOutpostSyncRevision) {
                            int newRevision = outpost.getSyncRevision();
                            if (hasFocusedModalTextField()) {
                                deferredOutpostSyncRevision = newRevision;
                            } else {
                                lastOutpostSyncRevision = newRevision;
                                deferredOutpostSyncRevision = -1;
                                markStructureDirty();
                            }
                        }
                        if (deferredOutpostSyncRevision != -1 && !hasFocusedModalTextField()) {
                            lastOutpostSyncRevision = deferredOutpostSyncRevision;
                            deferredOutpostSyncRevision = -1;
                            markStructureDirty();
                        }
                    }
                } else {
                    lastOutpostSyncRevision = -1;
                    deferredOutpostSyncRevision = -1;
                }
                lastOutpostStatePresent = present;
            } else {
                lastOutpostStatePresent = false;
                lastOutpostSyncRevision = -1;
                deferredOutpostSyncRevision = -1;
            }

            // Consume item picker result — works even if the starmap was closed and reopened
            // between the button click and the user returning from the item picker screen.
            if (ItemPickerScreen.hasPendingPickForOutpost()) {
                CelestialAsset.ID targetId = ItemPickerScreen.getPendingForOutpostId();
                ItemStack pickedStack = ItemPickerScreen.pollPendingPickForOutpost();
                AutomatedOutpost outpost = null;
                if (targetId != null && CelestialClient.getByAssetId(targetId) instanceof AutomatedOutpost o) {
                    outpost = o;
                }
                if (pickedStack != null && outpost != null) {
                    ItemStackWrapper wrapper = ItemStackWrapper.of(pickedStack);
                    boolean alreadyTracked = wrapper != null && outpost.logisticsConfig.snapshot()
                        .containsKey(wrapper);
                    if (wrapper != null && !alreadyTracked) {
                        LogisticsResourceConfig newCfg = new LogisticsResourceConfig(0, 64, false, false);
                        outpost.logisticsConfig.set(wrapper, newCfg);
                        Galaxia.LOG.info(
                            "[Outpost UI] Added logistics tracked item {} to outpost {} from item picker",
                            wrapper.toKey(),
                            outpost.assetId);
                        Galaxia.GALAXIA_NETWORK
                            .sendToServer(new LogisticsConfigUpdatePacket(outpost.assetId, wrapper, newCfg));
                    } else if (wrapper != null) {
                        Galaxia.LOG.info(
                            "[Outpost UI] Ignored item picker add for {} on outpost {} because it is already tracked",
                            wrapper.toKey(),
                            outpost.assetId);
                    }
                    // Refresh the modal if the correct outpost is currently open
                    if (state.pendingAssetManagement != null
                        && state.pendingAssetManagement.asset().assetId.equals(targetId)) {
                        markStructureDirty();
                    }
                }
            }
            if (structureVersion != lastStructureVersion) {
                rebuildChildren();
                lastStructureVersion = structureVersion;
                lastContentVersion = contentVersion;
                return;
            }

            if (shouldShowPanel() && contentVersion != lastContentVersion) {
                refreshMainPanelContent();
                lastContentVersion = contentVersion;
            }
        }

        @Override
        public void drawBackground(ModularGuiContext context, WidgetThemeEntry widgetTheme) {
            if (!shouldShowOverlay()) return;
            super.drawBackground(context, widgetTheme);
        }

        private boolean shouldShowOverlay() {
            return state.isAssetManagementOpen();
        }

        private boolean shouldShowPanel() {
            return state.isAssetManagementOpen() && !state.hasBlockingModal();
        }

        private void rebuildChildren() {
            if (modalScrollData != null) {
                saveCurrentModalScrollPosition(modalScrollData.getScroll());
            }
            clearMainPanelState();
            activeScrollWidget = null;
            removeAll();
            clearBounds();
            CelestialObject body = state.assetManagementBody;
            if (body == null) return;
            child(createBackdropButton());
            if (state.hasBlockingModal()) {
                buildPendingModal();
                return;
            }
            buildMainPanel(body);
            refreshMainPanelContent();
        }

        private void saveCurrentModalScrollPosition(int scroll) {
            if (state.selectingModuleBuild) {
                state.buildModuleScrollPosition = scroll;
            } else if (state.configuringModuleIndex >= 0 && state.pendingAssetManagement != null) {
                AutomatedOutpost outpost = null;
                if (CelestialClient
                    .getByAssetId(state.pendingAssetManagement.asset().assetId) instanceof AutomatedOutpost o) {
                    outpost = o;
                }
                if (outpost != null && state.configuringModuleIndex < outpost.modules()
                    .size()) {
                    AutomatedOutpostModule module = outpost.modules()
                        .get(state.configuringModuleIndex);
                    if (module.getKind() == OutpostModuleKind.MINER) {
                        state.minerConfigScrollPosition = scroll;
                    } else {
                        state.logisticsScrollPosition = scroll;
                    }
                    return;
                }
            }
            if (state.assetManagementTab == 1) {
                state.inventoryScrollPosition = scroll;
            } else {
                state.modulesScrollPosition = scroll;
            }
        }

        private int getCurrentModalScrollPosition() {
            if (state.selectingModuleBuild) return state.buildModuleScrollPosition;
            if (state.configuringModuleIndex >= 0 && state.pendingAssetManagement != null) {
                AutomatedOutpost outpost = null;
                if (CelestialClient
                    .getByAssetId(state.pendingAssetManagement.asset().assetId) instanceof AutomatedOutpost o) {
                    outpost = o;
                }
                if (outpost != null && state.configuringModuleIndex < outpost.modules()
                    .size()) {
                    AutomatedOutpostModule module = outpost.modules()
                        .get(state.configuringModuleIndex);
                    return module.getKind() == OutpostModuleKind.MINER ? state.minerConfigScrollPosition
                        : state.logisticsScrollPosition;
                }
            }
            return state.assetManagementTab == 1 ? state.inventoryScrollPosition : state.modulesScrollPosition;
        }

        private void buildMainPanel(CelestialObject body) {
            ModalBounds bounds = calculateManagementBounds();
            updateModalBounds(bounds.left(), bounds.top(), bounds.right(), bounds.bottom());
            int modalWidth = bounds.right() - bounds.left();
            int modalHeight = bounds.bottom() - bounds.top();
            int contentHeight = modalHeight - CONTENT_TOP - 12;
            int contentWidth = modalWidth - (CONTENT_PADDING * 2) - CONTENT_SCROLLBAR_GAP;
            scrollLeft = bounds.left() + CONTENT_PADDING;
            scrollTop = bounds.top() + CONTENT_TOP;
            scrollRight = scrollLeft + contentWidth;
            scrollBottom = scrollTop + contentHeight;
            mainContentWidth = contentWidth;
            mainContentHeight = contentHeight;
            ParentWidget<?> modal = createModalRoot(bounds);
            modal.child(createTitleText("Manage Assets").pos(12, 10));
            int titleRight = 12 + Minecraft.getMinecraft().fontRenderer.getStringWidth("Manage Assets");
            int assetNameMaxWidth = Math.max(0, modalWidth - 40 - (titleRight + 24));
            if (assetNameMaxWidth > 0) {
                String assetName = trimToWidth(body.displayName(), assetNameMaxWidth);
                int assetNameWidth = Minecraft.getMinecraft().fontRenderer.getStringWidth(assetName);
                int assetNameX = Math.max(titleRight + 12, modalWidth - 40 - assetNameWidth);
                modal.child(createBodyText(assetName, EnumColors.MAP_COLOR_TEXT_BODY.getColor()).pos(assetNameX, 10));
            }
            modal.child(
                createGlyphButton(AssetManagerButtonGlyph.CLOSE, "Close", true, callbacks::closeAssetManagement)
                    .pos(modalWidth - 28, 6));
            modal.child(
                createAssetKindButton(
                    CelestialAsset.Kind.STATION,
                    "Create Station",
                    callbacks.canCreateBaseStation(body),
                    () -> callbacks.createBaseStation(body)).pos(14, 30));
            modal.child(
                createAssetKindButton(
                    CelestialAsset.Kind.AUTOMATED_STATION,
                    "Create Automated Station",
                    callbacks.canCreateAutomatedStation(body),
                    () -> callbacks.triggerAssetCreation(body, CelestialAsset.Kind.AUTOMATED_STATION, false))
                        .pos(42, 30));
            modal.child(
                createAssetKindButton(
                    CelestialAsset.Kind.AUTOMATED_OUTPOST,
                    "Create Automated Outpost",
                    callbacks.canCreateAutomatedOutpost(body),
                    () -> callbacks.triggerAssetCreation(body, CelestialAsset.Kind.AUTOMATED_OUTPOST, false))
                        .pos(70, 30));
            if (!callbacks.isGT5AutomationAvailable()) {
                modal.child(
                    createBodyText("GT5U required for automated assets", EnumColors.MAP_COLOR_TEXT_MUTED.getColor())
                        .pos(104, 36));
            }
            VerticalScrollData scrollData = new VerticalScrollData();
            mainScrollData = scrollData;
            ScrollWidget<?> scroll = new ScrollWidget<>(scrollData).pos(CONTENT_PADDING, CONTENT_TOP)
                .widthRelOffset(1f, -(CONTENT_PADDING * 2) - CONTENT_SCROLLBAR_GAP)
                .heightRelOffset(1f, -(CONTENT_TOP + 12))
                .background(
                    drawable(
                        (context, x, y, width, height) -> Gui
                            .drawRect(x, y, x + width, y + height, EnumColors.MAP_COLOR_SCROLL_BG.getColor())));
            activeScrollWidget = scroll;
            mainScrollWidget = scroll;
            ParentWidget<?> content = new ParentWidget<>().widthRel(1f)
                .height(contentHeight);
            mainScrollContent = content;
            scroll.child(content);
            content.scheduleResize();
            scroll.scheduleResize();
            modal.child(scroll);
            child(modal);
        }

        private void refreshMainPanelContent() {
            if (!shouldShowPanel() || mainScrollContent == null || mainScrollWidget == null || mainScrollData == null)
                return;
            CelestialObject body = state.assetManagementBody;
            if (body == null) return;
            List<CelestialAsset> assetState = CelestialClient.getState(body.id());
            int contentScrollSize = Math.max(mainContentHeight, computeContentHeight(assetState));
            mainScrollData.setScrollSize(contentScrollSize);
            mainScrollContent.removeAll();
            mainScrollContent.widthRel(1f)
                .height(contentScrollSize);
            populateContent(mainScrollContent, mainContentWidth, assetState);
            mainScrollContent.scheduleResize();
            mainScrollWidget.scheduleResize();
        }

        private void buildPendingModal() {
            activeScrollWidget = null;
            scrollLeft = scrollTop = scrollRight = scrollBottom = 0;
            if (state.pendingAssetCreation != null) {
                buildPendingAssetCreationModal();
                return;
            }
            if (state.pendingAssetDestruction != null) {
                buildPendingAssetDestructionModal();
                return;
            }
            if (state.pendingConstructionCancellation != null) {
                buildPendingConstructionCancellationModal();
                return;
            }
            if (state.pendingResourceTransfer != null) {
                buildPendingResourceTransferModal();
                return;
            }
            if (state.pendingAssetManagement != null) {
                buildPendingAssetManagementModal();
                return;
            }
            if (state.pendingAssetRename != null) buildPendingAssetRenameModal();
        }

        private void buildPendingAssetCreationModal() {
            PendingAssetCreation creation = state.pendingAssetCreation;
            if (creation == null) return;
            int height = 150 + Math.max(
                0,
                creation.requiredResources()
                    .size() - 2)
                * 12;
            ModalBounds bounds = createCenteredModalBounds(320, height);
            updateModalBounds(bounds.left(), bounds.top(), bounds.right(), bounds.bottom());
            ParentWidget<?> modal = createModalRoot(bounds);
            modal.child(
                createAssetIconWidget(creation.kind(), 1.0f).pos(12, 10)
                    .size(18, 18));
            modal.child(createTitleText("Confirm " + callbacks.formatAssetKind(creation.kind())).pos(36, 10));
            modal.child(createBodyText(creation.displayName(), EnumColors.MAP_COLOR_TEXT_BODY.getColor()).pos(36, 28));
            modal.child(createSectionText("Required resources").pos(12, 52));
            int resourceY = 68;
            for (Map.Entry<ItemStack, Long> requirement : creation.requiredResources()
                .entrySet()) {
                modal.child(
                    createBodyText(
                        "- " + requirement.getValue()
                            + " "
                            + requirement.getKey()
                                .getDisplayName(),
                        EnumColors.MAP_COLOR_TEXT_BODY.getColor()).pos(16, resourceY));
                resourceY += 12;
            }
            addFooterButtons(
                modal,
                bounds,
                "Cancel",
                callbacks::dismissPendingAssetCreation,
                "Confirm",
                callbacks::confirmPendingAssetCreation,
                false);
            child(modal);
        }

        private void buildPendingAssetRenameModal() {
            if (state.pendingAssetRename == null) return;
            ModalBounds bounds = createCenteredModalBounds(RENAME_MODAL_WIDTH, 126);
            updateModalBounds(bounds.left(), bounds.top(), bounds.right(), bounds.bottom());
            ParentWidget<?> modal = createModalRoot(bounds);
            modal.child(createTitleText("Rename Asset").pos(12, 10));
            modal.child(
                createBodyText(
                    callbacks.formatAssetDisplayName(state.pendingAssetRename.asset()),
                    EnumColors.MAP_COLOR_TEXT_BODY.getColor()).pos(12, 28));
            modal.child(
                createBodyText("New name", EnumColors.MAP_COLOR_TEXT_MUTED.getColor()).pos(RENAME_INPUT_PADDING, 42));
            modal.child(drawable((context, x, y, width, height) -> {
                Gui.drawRect(x, y, x + width, y + height, EnumColors.MAP_COLOR_RENAME_INPUT_BG.getColor());
                Gui.drawRect(x, y, x + width, y + 1, EnumColors.MAP_COLOR_RENAME_BORDER.getColor());
                Gui.drawRect(x, y + height - 1, x + width, y + height, EnumColors.MAP_COLOR_RENAME_BORDER.getColor());
                Gui.drawRect(x, y, x + 1, y + height, EnumColors.MAP_COLOR_RENAME_BORDER.getColor());
                Gui.drawRect(x + width - 1, y, x + width, y + height, EnumColors.MAP_COLOR_RENAME_BORDER.getColor());
            }).asWidget()
                .pos(RENAME_INPUT_PADDING, CONTENT_TOP + 4)
                .size(312, RENAME_INPUT_HEIGHT));
            addFooterButtons(
                modal,
                bounds,
                "Cancel",
                callbacks::closePendingAssetRename,
                "Confirm",
                callbacks::confirmPendingAssetRename,
                false);
            child(modal);
        }

        private void buildPendingAssetDestructionModal() {
            PendingAssetDestruction destruction = state.pendingAssetDestruction;
            if (destruction == null) return;
            ModalBounds bounds = createCenteredModalBounds(360, 150);
            updateModalBounds(bounds.left(), bounds.top(), bounds.right(), bounds.bottom());
            int modalWidth = bounds.right() - bounds.left();
            ParentWidget<?> modal = createModalRoot(
                bounds.left(),
                bounds.top(),
                bounds.right(),
                bounds.bottom(),
                EnumColors.MAP_COLOR_MODAL_DANGER_BG.getColor(),
                EnumColors.MAP_COLOR_MODAL_DANGER_ACCENT.getColor(),
                -1);
            modal.child(
                createCenteredLargeText("THIS IS IRREVERSIBLE", 1.45f, EnumColors.MAP_COLOR_TEXT_DANGER.getColor())
                    .pos(12, 16)
                    .size(modalWidth - 24, 22));
            modal.child(
                createBodyText("You are about to destroy:", EnumColors.MAP_COLOR_TEXT_BODY.getColor()).pos(18, 52));
            modal.child(
                createBodyText(
                    callbacks.formatAssetDisplayName(destruction.asset()),
                    EnumColors.MAP_COLOR_TEXT_TITLE.getColor()).pos(18, 68));
            modal.child(
                createBodyText(
                    destruction.armed() ? "Click Destroy again to confirm." : "Press Destroy to arm confirmation.",
                    EnumColors.MAP_COLOR_TEXT_DANGER_BODY.getColor()).pos(18, 92));
            int cancelX = destruction.armed() ? (modalWidth - 18 - 130) : 18;
            int destroyX = destruction.armed() ? 18 : (modalWidth - 18 - 130);
            modal.child(
                createFooterButton("Cancel", true, callbacks::dismissPendingAssetDestruction)
                    .pos(cancelX, bounds.bottom() - bounds.top() - 34)
                    .size(130, FOOTER_BUTTON_HEIGHT));
            modal.child(
                createDangerFooterButton("Destroy", callbacks::advancePendingAssetDestruction)
                    .pos(destroyX, bounds.bottom() - bounds.top() - 34)
                    .size(130, FOOTER_BUTTON_HEIGHT));
            child(modal);
        }

        private void buildPendingConstructionCancellationModal() {
            if (state.pendingConstructionCancellation == null) return;
            ModalBounds bounds = createCenteredModalBounds(360, 124);
            updateModalBounds(bounds.left(), bounds.top(), bounds.right(), bounds.bottom());
            ParentWidget<?> modal = createModalRoot(
                bounds.left(),
                bounds.top(),
                bounds.right(),
                bounds.bottom(),
                EnumColors.MAP_COLOR_MODAL_WARNING_BG.getColor(),
                EnumColors.MAP_COLOR_MODAL_WARNING_ACCENT.getColor());
            modal.child(createTitleText("Cancel Construction?").pos(12, 10));
            modal.child(
                createBodyText(
                    callbacks.formatAssetDisplayName(state.pendingConstructionCancellation.asset()),
                    EnumColors.MAP_COLOR_TEXT_BODY.getColor()).pos(12, 28));
            modal.child(
                createBodyText(
                    "Stored resources will be moved into deconstruction recovery.",
                    EnumColors.MAP_COLOR_TEXT_WARNING.getColor()).pos(12, 54));
            addFooterButtons(
                modal,
                bounds,
                "Cancel",
                callbacks::dismissPendingConstructionCancellation,
                "Confirm",
                callbacks::confirmPendingConstructionCancellation,
                false);
            child(modal);
        }

        private void buildPendingResourceTransferModal() {
            PendingResourceTransfer transfer = state.pendingResourceTransfer;
            if (transfer == null) return;
            int height = Math.min(
                280,
                120 + transfer.targets()
                    .size() * 42);
            ModalBounds bounds = createCenteredModalBounds(420, height);
            updateModalBounds(bounds.left(), bounds.top(), bounds.right(), bounds.bottom());
            ParentWidget<?> modal = createModalRoot(bounds);
            modal.child(createTitleText("Send Resources To").pos(12, 10));
            modal.child(
                createBodyText(
                    callbacks.formatAssetDisplayName(transfer.asset()),
                    EnumColors.MAP_COLOR_TEXT_BODY.getColor()).pos(12, 28));
            modal.child(
                createBodyText(
                    "Requires an orbital rocket with enough capacity.",
                    EnumColors.MAP_COLOR_TEXT_MUTED.getColor()).pos(12, 46));
            modal.child(
                createFooterButton("Close", true, callbacks::dismissPendingResourceTransfer)
                    .pos(bounds.right() - bounds.left() - 96, 8)
                    .size(78, FOOTER_BUTTON_HEIGHT));
            if (transfer.targets()
                .isEmpty()) {
                modal.child(
                    createBodyText("No stations available in this system", EnumColors.MAP_COLOR_TEXT_MUTED.getColor())
                        .pos(16, 74));
                child(modal);
                return;
            }
            int rowTop = 66;
            for (int i = 0; i < transfer.targets()
                .size(); i++) {
                StationTransferTarget target = transfer.targets()
                    .get(i);
                int currentTop = rowTop + i * 42;
                modal.child(
                    drawable(
                        (context, x, y, width, h) -> Gui
                            .drawRect(x, y, x + width, y + h, EnumColors.MAP_COLOR_ROW_BG.getColor())).asWidget()
                                .pos(14, currentTop)
                                .size(bounds.right() - bounds.left() - 28, 36));
                modal.child(
                    createAssetIconWidget(CelestialAsset.Kind.STATION, 1.0f).pos(24, currentTop + 9)
                        .size(16, 16));
                modal.child(
                    createBodyText(target.displayName(), EnumColors.MAP_COLOR_TEXT_TITLE.getColor())
                        .pos(46, currentTop + 6));
                modal.child(
                    createBodyText(
                        target.hostBody()
                            .displayName(),
                        EnumColors.MAP_COLOR_TEXT_BODY.getColor()).pos(46, currentTop + 18));
                modal.child(
                    createFooterButton("Send", true, () -> callbacks.sendPendingResourceTransfer(target))
                        .pos(bounds.right() - bounds.left() - 92, currentTop + 8)
                        .size(72, FOOTER_BUTTON_HEIGHT));
            }
            child(modal);
        }

        private void buildPendingAssetManagementModal() {
            if (state.pendingAssetManagement == null) return;
            CelestialAsset asset = state.pendingAssetManagement.asset();

            if (asset.kind != CelestialAsset.Kind.AUTOMATED_OUTPOST) {
                ModalBounds bounds = createCenteredModalBounds(360, 150);
                updateModalBounds(bounds.left(), bounds.top(), bounds.right(), bounds.bottom());
                ParentWidget<?> modal = createModalRoot(bounds);
                modal.child(
                    createAssetIconWidget(asset.kind, 1.0f).pos(12, 10)
                        .size(18, 18));
                // TODO: Localize
                modal.child(createTitleText("Manage Station").pos(36, 10));
                modal.child(
                    createBodyText(callbacks.formatAssetDisplayName(asset), EnumColors.MAP_COLOR_TEXT_BODY.getColor())
                        .pos(36, 28));
                modal.child(
                    // TODO: Localize
                    createBodyText("This panel is not implemented yet.", EnumColors.MAP_COLOR_TEXT_MUTED.getColor())
                        .pos(14, 62));
                // TODO: Localize
                modal.child(
                    createFooterButton("Close", true, callbacks::closePendingAssetManagement)
                        .pos(bounds.right() - bounds.left() - 18 - 110, 8)
                        .size(110, FOOTER_BUTTON_HEIGHT));
                child(modal);
                return;
            }

            AutomatedOutpost outpost = null;
            if (asset instanceof AutomatedOutpost o) {
                outpost = o;
            }
            ModalBounds bounds = createCenteredModalBounds(MODAL_MAX_WIDTH, MODAL_MAX_HEIGHT);
            updateModalBounds(bounds.left(), bounds.top(), bounds.right(), bounds.bottom());
            ParentWidget<?> modal = createModalRoot(bounds);

            if (outpost == null) {
                Galaxia.GALAXIA_NETWORK.sendToServer(new OutpostRequestSyncPacket(asset.assetId));
                // TODO: Localize
                modal.child(createTitleText("Manage Outpost").pos(12, 10));
                modal.child(createBodyText("Loading data...", EnumColors.MAP_COLOR_TEXT_MUTED.getColor()).pos(12, 50));
            } else {
                if (state.configuringModuleIndex >= 0 && state.configuringModuleIndex < outpost.modules()
                    .size()) {
                    AutomatedOutpostModule module = outpost.modules()
                        .get(state.configuringModuleIndex);
                    if (module instanceof IHammer) {
                        buildLogisticsSubMenu(modal, outpost);
                    } else if (module.getKind() == OutpostModuleKind.MINER) {
                        buildMinerConfigSubMenu(modal, outpost, module);
                    } else if (module.getKind() == OutpostModuleKind.POWER) {
                        buildPowerConfigSubMenu(modal, outpost, module);
                    }
                } else if (state.assetManagementTab == 0) {
                    buildModulesTab(modal, outpost);
                } else {
                    buildInventoryTab(modal, outpost);
                }
            }

            modal.child(
                createGlyphButton(AssetManagerButtonGlyph.CLOSE, "Close", true, callbacks::closePendingAssetManagement)
                    .pos(bounds.right() - bounds.left() - 28, 6));
            child(modal);
        }

        private void buildModulesTab(ParentWidget<?> modal, AutomatedOutpost outpost) {
            int modalWidth = Math.max(520, modalRight - modalLeft);
            int visibleHeight = Math.max(220, (modalBottom - modalTop) - 102);
            int destroyWidth = 58;
            int disableWidth = 58;
            int configureWidth = 48;
            int rightPadding = 46;
            int gap = 8;
            int destroyX = modalWidth - rightPadding - destroyWidth;
            int disableX = destroyX - gap - disableWidth;
            int configureX = disableX - gap - configureWidth;
            modal.child(createTitleText("Manage Outpost").pos(12, 10));
            modal.child(
                createBodyText(
                    callbacks.formatAssetDisplayName(state.pendingAssetManagement.asset()),
                    EnumColors.MAP_COLOR_TEXT_BODY.getColor()).pos(12, 28));
            modal.child(
                createBodyText(buildPowerSummary(outpost), EnumColors.MAP_COLOR_TEXT_TITLE.getColor())
                    .pos(Math.max(210, modalWidth - 250), 28));

            // Tab switcher
            modal.child(createTabButton("Modules", state.assetManagementTab == 0, () -> {
                state.assetManagementTab = 0;
                state.selectingModuleBuild = false;
                clearArmedDestructiveActions();
                markStructureDirty();
            }).pos(12, 45)
                .size(96, 18));
            modal.child(createTabButton("Inventory", state.assetManagementTab == 1, () -> {
                state.assetManagementTab = 1;
                state.selectingModuleBuild = false;
                clearArmedDestructiveActions();
                markStructureDirty();
            }).pos(112, 45)
                .size(96, 18));

            // ── Build toolbar: one small button per module type ──────────────
            modal.child(createFooterButton("Build Module", true, () -> {
                state.selectingModuleBuild = true;
                clearArmedDestructiveActions();
                markStructureDirty();
            }).pos(12, 68)
                .size(140, 18));

            // ── Installed modules (scrollable) ───────────────────────────────
            VerticalScrollData scrollData = new VerticalScrollData();
            ScrollWidget<?> scroll = new ScrollWidget<>(scrollData).pos(12, 96)
                .widthRelOffset(1f, -24)
                .heightRelOffset(1f, -106)
                .background(
                    drawable(
                        (c, x, y, w, h) -> Gui
                            .drawRect(x, y, x + w, y + h, EnumColors.MAP_COLOR_SCROLL_BG.getColor())));
            scroll.setEnabled(!state.selectingModuleBuild);
            modalScrollData = scrollData;
            modalScrollWidget = scroll;
            activeScrollWidget = state.selectingModuleBuild ? null : scroll;
            ParentWidget<?> content = new ParentWidget<>().widthRel(1f);
            int y = 0;

            List<AutomatedOutpostModule> modules = outpost.modules();
            for (int i = 0; i < modules.size(); i++) {
                AutomatedOutpostModule m = modules.get(i);
                final int index = i;
                ParentWidget<?> row = new ParentWidget<>().pos(0, y)
                    .widthRel(1f)
                    .height(44)
                    .background(
                        drawable(
                            (c, x, y1, w, h) -> Gui
                                .drawRect(x, y1, x + w, y1 + h, EnumColors.MAP_COLOR_ROW_BG.getColor())));

                row.child(
                    createBodyText(
                        m.getKind()
                            .getDisplayName(),
                        EnumColors.MAP_COLOR_TEXT_TITLE.getColor()).pos(8, 6));

                boolean isHammer = m.getKind() == OutpostModuleKind.HAMMER
                    || m.getKind() == OutpostModuleKind.BIG_HAMMER;
                boolean isConfigurable = isHammer || m.getKind() == OutpostModuleKind.MINER
                    || m.getKind() == OutpostModuleKind.POWER;
                boolean operational = m.status() != AutomatedOutpostModule.Status.IN_CONSTRUCTION;
                boolean isDisabled = m.status() == AutomatedOutpostModule.Status.DISABLED;

                if (!operational) {
                    row.child(
                        createBodyText(
                            "Building... " + (int) (m.getConstructionProgress() * 100) + "%",
                            EnumColors.MAP_COLOR_TEXT_MUTED.getColor()).pos(8, 22));
                    row.child(drawable((c, x, y1, w, h) -> {
                        Gui.drawRect(x, y1, x + 100, y1 + 4, 0xFF333333);
                        Gui.drawRect(x, y1, x + (int) (m.getConstructionProgress() * 100), y1 + 4, 0xFF00FF00);
                    }).asWidget()
                        .pos(130, 24)
                        .size(100, 4));
                } else {
                    String statusLabel = isDisabled ? "Disabled" : "Active";
                    String powerLabel = m.getKind() == OutpostModuleKind.POWER
                        ? "Generating power: " + (isDisabled ? 0 : -m.getDisplayedPowerEuPerTick()) + " EU/t"
                        : "Power: " + Math.max(0L, m.getDisplayedPowerEuPerTick()) + " EU/t";
                    row.child(
                        createBodyText(statusLabel + " | " + powerLabel, EnumColors.MAP_COLOR_TEXT_BODY.getColor())
                            .pos(8, 22));
                }
                if (isConfigurable) {
                    row.child(createConfigureButton("Cfg", operational, () -> {
                        state.configuringModuleIndex = index;
                        markStructureDirty();
                    }).pos(configureX, 12)
                        .size(configureWidth, 20));
                }
                row.child(createDisableButton(isDisabled ? "Enable" : "Disable", operational, () -> {
                    OutpostModuleUpdatePacket.Action action = isDisabled ? OutpostModuleUpdatePacket.Action.ENABLE
                        : OutpostModuleUpdatePacket.Action.DISABLE;
                    state.armedModuleDestroyIndex = -1;
                    Galaxia.GALAXIA_NETWORK
                        .sendToServer(OutpostModuleUpdatePacket.action(outpost.assetId, index, action));
                }).pos(disableX, 12)
                    .size(disableWidth, 20));
                boolean armedDestroy = state.armedModuleDestroyIndex == index;
                row.child(createTwoStageDestructiveButton("Destroy", armedDestroy, true, () -> {
                    if (state.armedModuleDestroyIndex == index) {
                        state.armedModuleDestroyIndex = -1;
                        Galaxia.GALAXIA_NETWORK.sendToServer(
                            OutpostModuleUpdatePacket
                                .action(outpost.assetId, index, OutpostModuleUpdatePacket.Action.DESTROY));
                    } else {
                        state.armedModuleDestroyIndex = index;
                        markStructureDirty();
                    }
                }).pos(destroyX, 12)
                    .size(destroyWidth, 20));
                content.child(row);
                y += 50;
            }

            if (modules.isEmpty()) {
                content.child(
                    createBodyText(
                        "No modules installed. Use the buttons above to build one.",
                        EnumColors.MAP_COLOR_TEXT_MUTED.getColor()).pos(8, 8));
            }

            int contentHeight = Math.max(visibleHeight, y + 8);
            scrollData.setScrollSize(contentHeight);
            content.height(contentHeight);
            scroll.child(content);
            scrollData.scrollTo(scroll.getScrollArea(), getCurrentModalScrollPosition());
            content.scheduleResize();
            scroll.scheduleResize();
            modal.child(scroll);
            if (state.selectingModuleBuild) {
                buildModuleSelectionOverlay(modal, outpost);
            }
        }

        private void buildModuleSelectionOverlay(ParentWidget<?> modal, AutomatedOutpost outpost) {
            final int modalWidth = Math.max(520, modalRight - modalLeft);
            final int modalHeight = Math.max(360, modalBottom - modalTop);
            final int pickerLeft = 24;
            final int pickerTop = 40;
            final int pickerRight = modalWidth - 24;
            final int pickerBottom = modalHeight - 24;
            final int pickerWidth = pickerRight - pickerLeft;
            final int pickerHeight = pickerBottom - pickerTop;
            final int scrollX = 10;
            final int scrollY = 58;
            final int scrollHeight = pickerHeight - scrollY - 10;

            ParentWidget<?> backdrop = new ParentWidget<>().pos(0, 0)
                .widthRel(1f)
                .heightRel(1f)
                .background(drawable((c, x, y, w, h) -> Gui.drawRect(x, y, x + w, y + h, 0xAA081018)));
            backdrop.child(createBackdropButton());
            modal.child(backdrop);

            ParentWidget<?> picker = createModalRoot(pickerLeft, pickerTop, pickerRight, pickerBottom);
            picker.child(createTitleText("Build Module").pos(12, 10));
            picker.child(
                createBodyText("Choose a module to build on this outpost.", EnumColors.MAP_COLOR_TEXT_BODY.getColor())
                    .pos(12, 28));
            picker.child(createFooterButton("Close", true, () -> {
                state.selectingModuleBuild = false;
                markStructureDirty();
            }).pos(pickerWidth - 12 - 78, 8)
                .size(78, FOOTER_BUTTON_HEIGHT));

            // TODO: Localize
            boolean isAutomatedOutpost = state.pendingAssetManagement.asset().kind
                == CelestialAsset.Kind.AUTOMATED_OUTPOST;
            VerticalScrollData scrollData = new VerticalScrollData();
            ScrollWidget<?> scroll = new ScrollWidget<>(scrollData).pos(scrollX, scrollY)
                .widthRelOffset(1f, -(scrollX * 2))
                .heightRelOffset(1f, -(scrollY + 10))
                .background(
                    drawable(
                        (c, x, y, w, h) -> Gui
                            .drawRect(x, y, x + w, y + h, EnumColors.MAP_COLOR_SCROLL_BG.getColor())));
            scroll.setEnabled(true);
            modalScrollData = scrollData;
            modalScrollWidget = scroll;
            activeScrollWidget = scroll;
            scrollLeft = modalLeft + pickerLeft + scrollX;
            scrollTop = modalTop + pickerTop + scrollY;
            scrollRight = modalLeft + pickerRight - scrollX;
            scrollBottom = scrollTop + scrollHeight;
            ParentWidget<?> content = new ParentWidget<>().widthRel(1f);
            int y = 0;
            for (OutpostModuleKind kind : OutpostModuleKind.values()) {
                boolean buildEnabled = kind != OutpostModuleKind.MINER || isAutomatedOutpost;
                ParentWidget<?> row = new ParentWidget<>().pos(0, y)
                    .widthRel(1f)
                    .height(92)
                    .background(
                        drawable(
                            (c, x, y1, w, h) -> Gui
                                .drawRect(x, y1, x + w, y1 + h, EnumColors.MAP_COLOR_ROW_BG.getColor())));
                row.child(createBodyText(kind.getDisplayName(), EnumColors.MAP_COLOR_TEXT_TITLE.getColor()).pos(8, 8));
                row.child(
                    createBodyText(buildModuleDescription(kind), EnumColors.MAP_COLOR_TEXT_BODY.getColor()).pos(8, 24)
                        .size(Math.max(240, pickerWidth - 170), 24));
                row.child(
                    createBodyText(buildModuleStats(kind), EnumColors.MAP_COLOR_TEXT_MUTED.getColor()).pos(8, 50)
                        .size(Math.max(240, pickerWidth - 170), 12));
                row.child(
                    createBodyText(buildModuleCost(kind), EnumColors.MAP_COLOR_TEXT_MUTED.getColor()).pos(8, 64)
                        .size(Math.max(250, pickerWidth - 160), 20));
                row.child(createFooterButton(buildEnabled ? "Build" : "Unavailable", buildEnabled, () -> {
                    Galaxia.GALAXIA_NETWORK.sendToServer(
                        new OutpostBuildModulePacket(outpost.assetId, kind, callbacks.isCreativeBuildModeEnabled()));
                    state.selectingModuleBuild = false;
                    markStructureDirty();
                }).pos(pickerWidth - 100, 34)
                    .size(78, 20));
                content.child(row);
                y += 98;
            }
            int contentHeight = Math.max(scrollHeight, y + 4);
            scrollData.setScrollSize(contentHeight);
            content.height(contentHeight);
            scroll.child(content);
            scrollData.scrollTo(scroll.getScrollArea(), getCurrentModalScrollPosition());
            content.scheduleResize();
            scroll.scheduleResize();
            picker.child(scroll);
            modal.child(picker);
        }

        private void buildInventoryTab(ParentWidget<?> modal, AutomatedOutpost outpost) {
            int modalWidth = Math.max(520, modalRight - modalLeft);
            int visibleHeight = Math.max(220, (modalBottom - modalTop) - 80);
            modal.child(createTitleText("Manage Outpost").pos(12, 10));
            modal.child(
                createBodyText(
                    callbacks.formatAssetDisplayName(state.pendingAssetManagement.asset()),
                    EnumColors.MAP_COLOR_TEXT_BODY.getColor()).pos(12, 28));
            modal.child(
                createBodyText(buildPowerSummary(outpost), EnumColors.MAP_COLOR_TEXT_TITLE.getColor())
                    .pos(Math.max(210, modalWidth - 250), 28));

            // Tab switcher
            modal.child(createTabButton("Modules", state.assetManagementTab == 0, () -> {
                state.assetManagementTab = 0;
                state.selectingModuleBuild = false;
                clearArmedDestructiveActions();
                markStructureDirty();
            }).pos(12, 45)
                .size(96, 18));
            modal.child(createTabButton("Inventory", state.assetManagementTab == 1, () -> {
                state.assetManagementTab = 1;
                state.selectingModuleBuild = false;
                clearArmedDestructiveActions();
                markStructureDirty();
            }).pos(112, 45)
                .size(96, 18));

            VerticalScrollData scrollData = new VerticalScrollData();
            ScrollWidget<?> scroll = new ScrollWidget<>(scrollData).pos(12, 74)
                .widthRelOffset(1f, -24)
                .heightRelOffset(1f, -86)
                .background(
                    drawable(
                        (c, x, y, w, h) -> Gui
                            .drawRect(x, y, x + w, y + h, EnumColors.MAP_COLOR_SCROLL_BG.getColor())));
            modalScrollData = scrollData;
            modalScrollWidget = scroll;
            activeScrollWidget = scroll;
            ParentWidget<?> content = new ParentWidget<>().widthRel(1f);
            int sortAmountX = modalWidth - 120 - 28;
            int sortNameX = sortAmountX - 98;
            modal.child(createInventorySortButton("Name", sortNameX, 46, InventorySortMode.NAME).size(90, 16));
            modal.child(createInventorySortButton("Amount", sortAmountX, 46, InventorySortMode.AMOUNT).size(90, 16));
            int y = 0;
            for (Map.Entry<ItemStackWrapper, Long> entry : getSortedInventoryEntries(outpost)) {
                ParentWidget<?> row = new ParentWidget<>().pos(5, y)
                    .widthRelOffset(1f, -10)
                    .height(30)
                    .background(
                        drawable(
                            (c, x, y1, w, h) -> Gui
                                .drawRect(x, y1, x + w, y1 + h, EnumColors.MAP_COLOR_ROW_BG.getColor())));

                ItemStack stack = entry.getKey()
                    .toStack(1);
                row.child(
                    createInventoryItemWidget(stack, 16).pos(5, 7)
                        .size(16, 16));
                row.child(
                    createBodyText(stack.getDisplayName(), EnumColors.MAP_COLOR_TEXT_BODY.getColor()).pos(30, 10));
                row.child(
                    createBodyText(formatAmount(entry.getValue()), EnumColors.MAP_COLOR_TEXT_TITLE.getColor())
                        .pos(360, 10));
                boolean armedDump = entry.getKey()
                    .toKey()
                    .equals(state.armedDumpResourceKey);
                row.child(createTwoStageDestructiveButton("Dump", armedDump, true, () -> {
                    String resourceKey = entry.getKey()
                        .toKey();
                    if (resourceKey.equals(state.armedDumpResourceKey)) {
                        state.armedDumpResourceKey = null;
                        Galaxia.GALAXIA_NETWORK
                            .sendToServer(OutpostInventoryUpdatePacket.remove(outpost.assetId, entry.getKey()));
                    } else {
                        state.armedDumpResourceKey = resourceKey;
                        markStructureDirty();
                    }
                }).pos(420, 5)
                    .size(50, 20));
                content.child(row);
                y += 35;
            }
            int contentHeight = Math.max(visibleHeight, y + 4);
            scrollData.setScrollSize(contentHeight);
            content.height(contentHeight);
            scroll.child(content);
            scrollData.scrollTo(scroll.getScrollArea(), getCurrentModalScrollPosition());
            content.scheduleResize();
            scroll.scheduleResize();
            modal.child(scroll);
        }

        private Widget<?> createItemWidget(ItemStack stack, int size) {
            ItemStack displayStack = stack.copy();
            return drawable((context, x, y, width, height) -> drawGuiItemStack(displayStack, x, y, size)).asWidget()
                .tooltip(t -> t.addLine(displayStack.getDisplayName()));
        }

        private ButtonWidget<?> createInventoryItemWidget(ItemStack stack, int size) {
            ItemStack displayStack = stack.copy();
            return new ScrollAwareButtonWidget().background(IDrawable.EMPTY)
                .hoverBackground(createRectFrameDrawable(0x22000000, EnumColors.MAP_COLOR_MODAL_ACCENT.getColor()))
                .overlay(drawable((context, x, y, width, height) -> drawGuiItemStack(displayStack, x, y, size)))
                .tooltip(t -> {
                    t.addLine(displayStack.getDisplayName());
                    t.addLine("LMB/R: Recipe");
                    t.addLine("RMB/U: Usage");
                })
                .onMousePressed(mouseButton -> {
                    if (mouseButton == 0) {
                        GuiCraftingRecipe.openRecipeGui("item", displayStack.copy());
                        return true;
                    }
                    if (mouseButton == 1) {
                        GuiUsageRecipe.openRecipeGui("item", displayStack.copy());
                        return true;
                    }
                    return true;
                })
                .onKeyPressed((typedChar, keyCode) -> {
                    if (keyCode == Keyboard.KEY_R) {
                        return GuiCraftingRecipe.openRecipeGui("item", displayStack.copy());
                    }
                    if (keyCode == Keyboard.KEY_U) {
                        return GuiUsageRecipe.openRecipeGui("item", displayStack.copy());
                    }
                    return false;
                });
        }

        private ButtonWidget<?> createInventorySortButton(String label, int x, int y, InventorySortMode mode) {
            boolean active = state.inventorySortMode == mode;
            String suffix = active ? (state.inventorySortAscending ? " ▲" : " ▼") : "";
            return createTextButton(label + suffix, true, () -> {
                if (state.inventorySortMode == mode) {
                    state.inventorySortAscending = !state.inventorySortAscending;
                } else {
                    state.inventorySortMode = mode;
                    state.inventorySortAscending = true;
                }
                markStructureDirty();
            }, false).pos(x, y);
        }

        private List<Map.Entry<ItemStackWrapper, Long>> getSortedInventoryEntries(AutomatedOutpost outpost) {
            List<Map.Entry<ItemStackWrapper, Long>> entries = new ArrayList<>(
                outpost.inventory.snapshot()
                    .entrySet());
            Comparator<Map.Entry<ItemStackWrapper, Long>> comparator;
            if (state.inventorySortMode == InventorySortMode.AMOUNT) {
                comparator = Comparator.<Map.Entry<ItemStackWrapper, Long>>comparingLong(Map.Entry::getValue)
                    .thenComparing(
                        entry -> entry.getKey()
                            .toStack(1)
                            .getDisplayName(),
                        String.CASE_INSENSITIVE_ORDER);
            } else {
                comparator = Comparator.<Map.Entry<ItemStackWrapper, Long>, String>comparing(
                    entry -> entry.getKey()
                        .toStack(1)
                        .getDisplayName(),
                    String.CASE_INSENSITIVE_ORDER)
                    .thenComparingLong(Map.Entry::getValue);
            }
            entries.sort(state.inventorySortAscending ? comparator : comparator.reversed());
            return entries;
        }

        private TextFieldWidget createIntegerValueWidget(IntSupplier getter, IntConsumer setter, int min, int max) {
            TextFieldWidget field = new TextFieldWidget().setMaxLength(9)
                .setPattern(Pattern.compile("[0-9]*"))
                .setDefaultNumber(min)
                .setNumbers(min, max)
                .setFormatAsInteger(true)
                .acceptsExpressions(false)
                .autoUpdateOnChange(false)
                .setTextColor(EnumColors.MAP_COLOR_TEXT_TITLE.getColor())
                .hintColor(EnumColors.MAP_COLOR_TEXT_MUTED.getColor())
                .background(
                    createRectFrameDrawable(
                        EnumColors.MAP_COLOR_BTN_ENABLED_DEFAULT.getColor(),
                        EnumColors.MAP_COLOR_BTN_BORDER_ENABLED.getColor()))
                .value(new StringValue.Dynamic(() -> String.valueOf(getter.getAsInt()), text -> {
                    int parsed = min;
                    if (text != null && !text.isEmpty()) {
                        try {
                            parsed = Integer.parseInt(text);
                        } catch (NumberFormatException ignored) {
                            parsed = getter.getAsInt();
                        }
                    }
                    setter.accept(Math.max(min, Math.min(max, parsed)));
                }))
                .setFocusOnGuiOpen(false);
            modalTextFields.add(field);
            return field;
        }

        private TextFieldWidget createDecimalValueWidget(DoubleSupplier getter, DoubleConsumer setter, double min,
            double max, boolean integerFormat) {
            TextFieldWidget field = new TextFieldWidget().setMaxLength(12)
                .setPattern(Pattern.compile("[0-9]*(\\.[0-9]*)?"))
                .acceptsExpressions(false)
                .autoUpdateOnChange(false)
                .setTextColor(EnumColors.MAP_COLOR_TEXT_TITLE.getColor())
                .hintColor(EnumColors.MAP_COLOR_TEXT_MUTED.getColor())
                .background(
                    createRectFrameDrawable(
                        EnumColors.MAP_COLOR_BTN_ENABLED_DEFAULT.getColor(),
                        EnumColors.MAP_COLOR_BTN_BORDER_ENABLED.getColor()))
                .value(
                    new StringValue.Dynamic(
                        () -> formatEditableThreshold(getter.getAsDouble(), integerFormat),
                        text -> {
                            double parsed = min;
                            if (text != null && !text.isEmpty() && !".".equals(text)) {
                                try {
                                    parsed = Double.parseDouble(text);
                                } catch (NumberFormatException ignored) {
                                    parsed = getter.getAsDouble();
                                }
                            }
                            setter.accept(Math.max(min, Math.min(max, parsed)));
                        }))
                .setFocusOnGuiOpen(false);
            modalTextFields.add(field);
            return field;
        }

        private String formatEditableThreshold(double value, boolean integerFormat) {
            if (!Double.isFinite(value)) return "0";
            if (integerFormat) return String.format("%.0f", value);
            return String.format("%.1f", value);
        }

        private void drawGuiItemStack(ItemStack stack, int x, int y, int size) {
            Minecraft mc = Minecraft.getMinecraft();
            float scale = size / 16.0f;
            com.cleanroommc.modularui.utils.GlStateManager.pushMatrix();
            com.cleanroommc.modularui.utils.GlStateManager.translate(x, y, 200f);
            com.cleanroommc.modularui.utils.GlStateManager.scale(scale, scale, 1f);
            com.cleanroommc.modularui.utils.GlStateManager.color(1f, 1f, 1f, 1f);
            org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL12.GL_RESCALE_NORMAL);
            org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_ALPHA_TEST);
            net.minecraft.client.renderer.RenderHelper.enableGUIStandardItemLighting();
            org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
            net.minecraft.client.renderer.entity.RenderItem ri = net.minecraft.client.renderer.entity.RenderItem
                .getInstance();
            float previousZ = ri.zLevel;
            ri.zLevel = 200f;
            net.minecraft.client.renderer.OpenGlHelper
                .setLightmapTextureCoords(net.minecraft.client.renderer.OpenGlHelper.lightmapTexUnit, 240f, 240f);
            ri.renderItemAndEffectIntoGUI(mc.fontRenderer, mc.getTextureManager(), stack, 0, 0);
            ri.zLevel = previousZ;
            net.minecraft.client.renderer.RenderHelper.disableStandardItemLighting();
            org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_LIGHTING);
            org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_COLOR_MATERIAL);
            org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
            org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL12.GL_RESCALE_NORMAL);
            com.cleanroommc.modularui.utils.GlStateManager.color(1f, 1f, 1f, 1f);
            com.cleanroommc.modularui.utils.GlStateManager.popMatrix();
        }

        /**
         * Renders the logistics routing configuration for a HAMMER or BIG_HAMMER module.
         *
         * <p>
         * Shows all explicitly configured items in a scrollable list. Each row follows
         * the layout: [Icon] [Name] [Amount] [−][Reserve][+] [Import] [Export].
         *
         * <p>
         * The reserve value is displayed as a text widget between the decrement/increment
         * buttons, while the buttons themselves send a {@link LogisticsConfigUpdatePacket}.
         */
        private void buildLogisticsSubMenu(ParentWidget<?> modal, AutomatedOutpost outpost) {
            int visibleHeight = Math.max(220, (modalBottom - modalTop) - 106);
            List<AutomatedOutpostModule> modules = outpost.modules();
            AutomatedOutpostModule module = (state.configuringModuleIndex >= 0
                && state.configuringModuleIndex < modules.size()) ? modules.get(state.configuringModuleIndex) : null;
            String moduleLabel = module != null ? module.getKind()
                .getDisplayName() : "HAMMER";
            boolean isBigHammer = module instanceof ModuleBigHammer;
            IHammer hammer = (IHammer) module;

            // Extract current shooting config and planetary flag from live module data
            AllowShootingConfig shootingCfg = hammer.getConfig();
            boolean planetaryHandling = hammer.getPlanetaryHandling();
            OrbitalTransferPlanner.RoutePriority routePriority = hammer.getRoutePriority();
            AllowShootingConfig.Mode currentMode = shootingCfg.mode();
            double currentThreshold = shootingCfg.threshold();

            modal.child(createTitleText("Logistics: " + moduleLabel).pos(12, 10));
            modal.child(createFooterButton("Back", true, () -> {
                state.configuringModuleIndex = -1;
                markStructureDirty();
            }).pos(12, 32)
                .size(60, 20));

            // ── Add Item button: opens a separate NEI-enabled screen to pick an item ──
            modal.child(createFooterButton("Add Item", true, () -> {
                ItemPickerScreen.setPendingForOutpost(outpost.assetId);
                ItemPickerScreen.FACTORY.openClient();
            }).pos(90, 32)
                .size(80, 18));

            // ── Module settings row ───────────────────────────────────────────
            modal.child(createBodyText("Shooting:", EnumColors.MAP_COLOR_TEXT_MUTED.getColor()).pos(12, 56));
            String modeLabel = switch (currentMode) {
                case ALWAYS -> "Always";
                case WHEN_DV_UNDER -> "dV\u003c";
                case WHEN_TOF_UNDER -> "TOF\u003c";
            };
            final int modIdx = state.configuringModuleIndex;
            modal.child(createFooterButton(modeLabel, module != null, () -> {
                AllowShootingConfig.Mode next = switch (currentMode) {
                    case ALWAYS -> AllowShootingConfig.Mode.WHEN_DV_UNDER;
                    case WHEN_DV_UNDER -> AllowShootingConfig.Mode.WHEN_TOF_UNDER;
                    case WHEN_TOF_UNDER -> AllowShootingConfig.Mode.ALWAYS;
                };
                applyShootingModeUpdate(hammer, outpost, modIdx, isBigHammer, next, currentThreshold);
                markStructureDirty();
            }).pos(74, 52)
                .size(56, 18));

            if (currentMode != AllowShootingConfig.Mode.ALWAYS) {
                double step = currentMode == AllowShootingConfig.Mode.WHEN_DV_UNDER ? 1.0 : 3600.0;
                modal.child(createFooterButton("-", module != null, () -> {
                    double newT = Math.max(0.0, currentThreshold - step);
                    applyShootingThresholdUpdate(hammer, outpost, modIdx, isBigHammer, currentMode, newT);
                    markStructureDirty();
                }).pos(136, 52)
                    .size(18, 18));
                modal.child(
                    createDecimalValueWidget(
                        () -> getCurrentShootingThreshold(hammer, isBigHammer),
                        value -> applyShootingThresholdUpdate(hammer, outpost, modIdx, isBigHammer, currentMode, value),
                        0.0,
                        999999999.0,
                        currentMode == AllowShootingConfig.Mode.WHEN_TOF_UNDER).pos(158, 52)
                            .size(44, 18));
                modal.child(createFooterButton("+", module != null, () -> {
                    double newT = currentThreshold + step;
                    applyShootingThresholdUpdate(hammer, outpost, modIdx, isBigHammer, currentMode, newT);
                    markStructureDirty();
                }).pos(206, 52)
                    .size(18, 18));
            }

            if (isBigHammer) {
                modal.child(createBodyText("Planetary:", EnumColors.MAP_COLOR_TEXT_MUTED.getColor()).pos(234, 56));
                modal.child(createFooterButton(planetaryHandling ? "ON" : "OFF", true, () -> {
                    ModuleBigHammer bh = (ModuleBigHammer) hammer;
                    bh.setPlanetaryHandling(!planetaryHandling);
                    Galaxia.GALAXIA_NETWORK.sendToServer(
                        OutpostModuleUpdatePacket.config(
                            outpost.assetId,
                            modIdx,
                            OutpostModuleUpdatePacket.ConfigAction.SET_PLANETARY_HANDLING,
                            !planetaryHandling));
                    markStructureDirty();
                }).pos(296, 52)
                    .size(34, 18));
            }

            // ── Column header labels ──────────────────────────────────────────
            modal.child(createBodyText("Priority:", EnumColors.MAP_COLOR_TEXT_MUTED.getColor()).pos(12, 78));
            modal.child(
                createFooterButton(
                    routePriority == OrbitalTransferPlanner.RoutePriority.PRIORITIZE_DV ? "dV" : "TOF",
                    hammer != null,
                    () -> {
                        applyRoutePriorityUpdate(hammer, outpost, modIdx, routePriority.toggled());
                        markStructureDirty();
                    }).pos(74, 74)
                        .size(56, 18));

            int hdrY = 102;
            modal.child(createBodyText("Item", EnumColors.MAP_COLOR_TEXT_MUTED.getColor()).pos(26, hdrY));
            modal.child(createBodyText("Inventory", EnumColors.MAP_COLOR_TEXT_MUTED.getColor()).pos(186, hdrY));
            modal.child(createBodyText("Reserve", EnumColors.MAP_COLOR_TEXT_MUTED.getColor()).pos(280, hdrY));
            modal.child(createBodyText("Min Pkg", EnumColors.MAP_COLOR_TEXT_MUTED.getColor()).pos(365, hdrY));
            modal.child(createBodyText("Import", EnumColors.MAP_COLOR_TEXT_MUTED.getColor()).pos(430, hdrY));
            modal.child(createBodyText("Export", EnumColors.MAP_COLOR_TEXT_MUTED.getColor()).pos(468, hdrY));
            modal.child(createBodyText("Rm", EnumColors.MAP_COLOR_TEXT_MUTED.getColor()).pos(510, hdrY));

            // ── Scrollable item rows ─────────────────────────────────────────
            VerticalScrollData scrollData = new VerticalScrollData();
            ScrollWidget<?> scroll = new ScrollWidget<>(scrollData).pos(10, 116)
                .widthRelOffset(1f, -20)
                .heightRelOffset(1f, -126)
                .background(
                    drawable(
                        (c, x, y, w, h) -> Gui
                            .drawRect(x, y, x + w, y + h, EnumColors.MAP_COLOR_SCROLL_BG.getColor())));
            modalScrollData = scrollData;
            modalScrollWidget = scroll;
            activeScrollWidget = scroll;
            ParentWidget<?> content = new ParentWidget<>().widthRel(1f);

            Map<ItemStackWrapper, LogisticsResourceConfig> configSnapshot = outpost.logisticsConfig.snapshot();

            int rowY = 0;
            for (Map.Entry<ItemStackWrapper, LogisticsResourceConfig> entry : configSnapshot.entrySet()) {
                final ItemStackWrapper wrapper = entry.getKey();
                final LogisticsResourceConfig cfg = entry.getValue();
                final ItemStack displayStack = wrapper.toStack(1);
                long currentAmount = outpost.inventory.getAmount(wrapper);

                ParentWidget<?> row = new ParentWidget<>().pos(4, rowY)
                    .widthRelOffset(1f, -8)
                    .height(28)
                    .background(
                        drawable(
                            (c, x, y1, w, h) -> Gui
                                .drawRect(x, y1, x + w, y1 + h, EnumColors.MAP_COLOR_ROW_BG.getColor())));

                // Icon
                row.child(
                    createItemWidget(displayStack, 16).pos(4, 6)
                        .size(16, 16));
                // Name (truncated)
                String name = displayStack.getDisplayName();
                row.child(createBodyText(name, EnumColors.MAP_COLOR_TEXT_BODY.getColor()).pos(24, 8));
                // Current amount in inventory
                row.child(
                    createBodyText(formatAmount(currentAmount), EnumColors.MAP_COLOR_TEXT_TITLE.getColor())
                        .pos(182, 8));
                // Reserve: [−] value [+]
                row.child(createFooterButton("-", true, () -> {
                    int newRes = Math.max(0, cfg.minReserve() - 1);
                    LogisticsResourceConfig updated = cfg.withMinReserve(newRes);
                    outpost.logisticsConfig.set(wrapper, updated);
                    Galaxia.GALAXIA_NETWORK
                        .sendToServer(new LogisticsConfigUpdatePacket(outpost.assetId, wrapper, updated));
                    markStructureDirty();
                }).pos(246, 4)
                    .size(18, 20));
                row.child(
                    createIntegerValueWidget(
                        () -> outpost.logisticsConfig.get(wrapper)
                            .minReserve(),
                        value -> {
                            LogisticsResourceConfig current = outpost.logisticsConfig.get(wrapper);
                            LogisticsResourceConfig updated = current.withMinReserve(value);
                            outpost.logisticsConfig.set(wrapper, updated);
                            Galaxia.GALAXIA_NETWORK
                                .sendToServer(new LogisticsConfigUpdatePacket(outpost.assetId, wrapper, updated));
                        },
                        0,
                        999999).pos(268, 4)
                            .size(36, 20));
                row.child(createFooterButton("+", true, () -> {
                    int newRes = cfg.minReserve() + 1;
                    LogisticsResourceConfig updated = cfg.withMinReserve(newRes);
                    outpost.logisticsConfig.set(wrapper, updated);
                    Galaxia.GALAXIA_NETWORK
                        .sendToServer(new LogisticsConfigUpdatePacket(outpost.assetId, wrapper, updated));
                    markStructureDirty();
                }).pos(306, 4)
                    .size(18, 20));
                // Minimum package size
                row.child(createFooterButton("-", true, () -> {
                    int newPkg = Math.max(1, cfg.orderSize() - 1);
                    LogisticsResourceConfig updated = cfg.withOrderSize(newPkg);
                    outpost.logisticsConfig.set(wrapper, updated);
                    Galaxia.GALAXIA_NETWORK
                        .sendToServer(new LogisticsConfigUpdatePacket(outpost.assetId, wrapper, updated));
                    markStructureDirty();
                }).pos(330, 4)
                    .size(18, 20));
                row.child(
                    createIntegerValueWidget(
                        () -> outpost.logisticsConfig.get(wrapper)
                            .orderSize(),
                        value -> {
                            LogisticsResourceConfig current = outpost.logisticsConfig.get(wrapper);
                            LogisticsResourceConfig updated = current.withOrderSize(value);
                            outpost.logisticsConfig.set(wrapper, updated);
                            Galaxia.GALAXIA_NETWORK
                                .sendToServer(new LogisticsConfigUpdatePacket(outpost.assetId, wrapper, updated));
                        },
                        1,
                        999999).pos(352, 4)
                            .size(36, 20));
                row.child(createFooterButton("+", true, () -> {
                    int newPkg = cfg.orderSize() + 1;
                    LogisticsResourceConfig updated = cfg.withOrderSize(newPkg);
                    outpost.logisticsConfig.set(wrapper, updated);
                    Galaxia.GALAXIA_NETWORK
                        .sendToServer(new LogisticsConfigUpdatePacket(outpost.assetId, wrapper, updated));
                    markStructureDirty();
                }).pos(390, 4)
                    .size(18, 20));
                // Import toggle
                row.child(createFooterButton(cfg.isImportEnabled() ? "ON" : "OFF", true, () -> {
                    LogisticsResourceConfig updated = cfg.withImportEnabled(!cfg.isImportEnabled());
                    outpost.logisticsConfig.set(wrapper, updated);
                    Galaxia.GALAXIA_NETWORK
                        .sendToServer(new LogisticsConfigUpdatePacket(outpost.assetId, wrapper, updated));
                    markStructureDirty();
                }).pos(416, 4)
                    .size(34, 20));
                // Export toggle
                row.child(createFooterButton(cfg.isSupplyEnabled() ? "ON" : "OFF", true, () -> {
                    LogisticsResourceConfig updated = cfg.withSupplyEnabled(!cfg.isSupplyEnabled());
                    outpost.logisticsConfig.set(wrapper, updated);
                    Galaxia.GALAXIA_NETWORK
                        .sendToServer(new LogisticsConfigUpdatePacket(outpost.assetId, wrapper, updated));
                    markStructureDirty();
                }).pos(454, 4)
                    .size(34, 20));
                row.child(createFooterButton("X", true, () -> {
                    outpost.logisticsConfig.reset(wrapper);
                    Galaxia.GALAXIA_NETWORK.sendToServer(LogisticsConfigUpdatePacket.remove(outpost.assetId, wrapper));
                    markStructureDirty();
                }).pos(502, 4)
                    .size(18, 20));

                content.child(row);
                rowY += 32;
            }

            if (configSnapshot.isEmpty()) {
                content.child(
                    createBodyText(
                        "No items tracked. Use the 'Add Item' button to start.",
                        EnumColors.MAP_COLOR_TEXT_MUTED.getColor()).pos(8, 8));
            }

            int contentHeight = Math.max(visibleHeight, rowY + 8);
            scrollData.setScrollSize(contentHeight);
            content.height(contentHeight);
            scroll.child(content);
            scrollData.scrollTo(scroll.getScrollArea(), getCurrentModalScrollPosition());
            content.scheduleResize();
            scroll.scheduleResize();
            modal.child(scroll);
        }

        private void applyShootingModeUpdate(IHammer module, AutomatedOutpost outpost, int modIdx, boolean isBigHammer,
            AllowShootingConfig.Mode newMode, double threshold) {
            if (module == null) return;
            AllowShootingConfig newCfg = new AllowShootingConfig(newMode, threshold);
            module.setConfig(newCfg);
            Galaxia.GALAXIA_NETWORK.sendToServer(
                OutpostModuleUpdatePacket.config(
                    outpost.assetId,
                    modIdx,
                    OutpostModuleUpdatePacket.ConfigAction.SET_ALLOW_SHOOTING_MODE,
                    newMode));
        }

        private void applyShootingThresholdUpdate(IHammer module, AutomatedOutpost outpost, int modIdx,
            boolean isBigHammer, AllowShootingConfig.Mode mode, double newThreshold) {
            if (module == null) return;
            AllowShootingConfig newCfg = new AllowShootingConfig(mode, newThreshold);
            module.setConfig(newCfg);
            Galaxia.GALAXIA_NETWORK.sendToServer(
                OutpostModuleUpdatePacket.config(
                    outpost.assetId,
                    modIdx,
                    OutpostModuleUpdatePacket.ConfigAction.SET_ALLOW_SHOOTING_THRESHOLD,
                    newThreshold));
        }

        private double getCurrentShootingThreshold(IHammer module, boolean isBigHammer) {
            if (module == null) return 0.0;
            return module.getConfig()
                .threshold();
        }

        private void applyRoutePriorityUpdate(IHammer module, AutomatedOutpost outpost, int modIdx,
            OrbitalTransferPlanner.RoutePriority priority) {
            if (module == null || priority == null) return;
            module.setPriority(priority);
            Galaxia.GALAXIA_NETWORK.sendToServer(
                OutpostModuleUpdatePacket.config(
                    outpost.assetId,
                    modIdx,
                    OutpostModuleUpdatePacket.ConfigAction.SET_ROUTE_PRIORITY,
                    priority));
        }

        private void buildMinerConfigSubMenu(ParentWidget<?> modal, AutomatedOutpost outpost,
            AutomatedOutpostModule module) {
            int visibleHeight = Math.max(220, (modalBottom - modalTop) - 88);
            ModuleMiner miner = (ModuleMiner) module;
            List<MinerOreOption> options = buildMinerOreOptions(outpost, miner);

            modal.child(createTitleText("Miner Configuration").pos(12, 10));
            modal.child(createFooterButton("Back", true, () -> {
                state.configuringModuleIndex = -1;
                markStructureDirty();
            }).pos(12, 32)
                .size(60, 20));
            modal.child(
                createFooterButton(miner.getCopySettingsToOtherMiners() ? "Copy: ON" : "Copy Settings", true, () -> {
                    miner.withCopySettingsToOtherMiners(!miner.getCopySettingsToOtherMiners());
                    Galaxia.GALAXIA_NETWORK.sendToServer(
                        OutpostModuleUpdatePacket.config(
                            outpost.assetId,
                            state.configuringModuleIndex,
                            OutpostModuleUpdatePacket.ConfigAction.SET_MINER_COPY_SETTINGS,
                            miner.getCopySettingsToOtherMiners()));
                    markStructureDirty();
                }).pos(82, 32)
                    .size(100, 20)
                    .tooltip(t -> t.addLine("Apply settings to all other miners")));
            modal.child(createBodyText("Planetary ores", EnumColors.MAP_COLOR_TEXT_MUTED.getColor()).pos(12, 58));
            modal.child(createBodyText("Blacklist", EnumColors.MAP_COLOR_TEXT_MUTED.getColor()).pos(428, 58));

            VerticalScrollData scrollData = new VerticalScrollData();
            ScrollWidget<?> scroll = new ScrollWidget<>(scrollData).pos(10, 78)
                .widthRelOffset(1f, -20)
                .heightRelOffset(1f, -88)
                .background(
                    drawable(
                        (c, x, y, w, h) -> Gui
                            .drawRect(x, y, x + w, y + h, EnumColors.MAP_COLOR_SCROLL_BG.getColor())));
            modalScrollData = scrollData;
            modalScrollWidget = scroll;
            activeScrollWidget = scroll;
            ParentWidget<?> content = new ParentWidget<>().widthRel(1f);
            int rowY = 0;
            for (MinerOreOption option : options) {
                ParentWidget<?> row = new ParentWidget<>().pos(4, rowY)
                    .widthRelOffset(1f, -8)
                    .height(28)
                    .background(
                        drawable(
                            (c, x, y1, w, h) -> Gui
                                .drawRect(x, y1, x + w, y1 + h, EnumColors.MAP_COLOR_ROW_BG.getColor())));
                if (option.displayStack() != null) {
                    row.child(
                        createItemWidget(option.displayStack(), 16).pos(4, 6)
                            .size(16, 16));
                } else {
                    row.child(drawable((c, x, y1, w, h) -> {
                        Gui.drawRect(x, y1, x + w, y1 + h, EnumColors.MAP_COLOR_MODAL_HEADER.getColor());
                        Gui.drawRect(x, y1, x + w, y1 + 1, EnumColors.MAP_COLOR_MODAL_ACCENT.getColor());
                        Gui.drawRect(x, y1 + h - 1, x + w, y1 + h, EnumColors.MAP_COLOR_MODAL_ACCENT.getColor());
                        Gui.drawRect(x, y1, x + 1, y1 + h, EnumColors.MAP_COLOR_MODAL_ACCENT.getColor());
                        Gui.drawRect(x + w - 1, y1, x + w, y1 + h, EnumColors.MAP_COLOR_MODAL_ACCENT.getColor());
                    }).asWidget()
                        .pos(4, 6)
                        .size(16, 16));
                }
                row.child(
                    createBodyText(option.displayName(), EnumColors.MAP_COLOR_TEXT_BODY.getColor()).pos(24, 8)
                        .width(360));
                row.child(createCheckboxButton(option.blacklisted(), () -> {
                    boolean blacklisted = miner.isBlacklisted(option.key());
                    if (blacklisted) {
                        miner.withRemovedBlacklist(option.key());
                    } else {
                        miner.withAddedBlacklist(option.key());
                    }
                    Galaxia.GALAXIA_NETWORK.sendToServer(
                        OutpostModuleUpdatePacket.config(
                            outpost.assetId,
                            state.configuringModuleIndex,
                            blacklisted ? OutpostModuleUpdatePacket.ConfigAction.REMOVE_MINER_BLACKLIST
                                : OutpostModuleUpdatePacket.ConfigAction.ADD_MINER_BLACKLIST,
                            option.key()));
                    markStructureDirty();
                }).pos(434, 4)
                    .size(20, 20));
                content.child(row);
                rowY += 32;
            }
            if (options.isEmpty()) {
                content.child(
                    createBodyText("No ores available on this body.", EnumColors.MAP_COLOR_TEXT_MUTED.getColor())
                        .pos(8, 8));
            }
            int contentHeight = Math.max(visibleHeight, rowY + 8);
            scrollData.setScrollSize(contentHeight);
            content.height(contentHeight);
            scroll.child(content);
            scrollData.scrollTo(scroll.getScrollArea(), getCurrentModalScrollPosition());
            content.scheduleResize();
            scroll.scheduleResize();
            modal.child(scroll);
        }

        private List<MinerOreOption> buildMinerOreOptions(AutomatedOutpost outpost, ModuleMiner minerData) {
            return GalaxiaCelestialAPI.get(outpost.celestialObjectId)
                .map(
                    body -> body.properties()
                        .hasGtOreVeinOres() ? buildGtMinerOreOptions(body, minerData)
                            : buildVanillaMinerOreOptions(body, minerData))
                .orElse(List.of());
        }

        private List<MinerOreOption> buildGtMinerOreOptions(CelestialObject body, ModuleMiner minerData) {
            Map<String, MinerOreOption> options = new LinkedHashMap<>();
            body.properties()
                .gtOreVeinOres()
                .forEach(oreName -> {
                    if (oreName == null || oreName.isEmpty() || options.containsKey(oreName)) return;
                    ItemStack displayStack = GTUtility.getRawOreStack(oreName);
                    String displayName = displayStack != null ? displayStack.getDisplayName() : oreName;
                    options.put(
                        oreName,
                        new MinerOreOption(oreName, displayName, displayStack, minerData.isBlacklisted(oreName)));
                });
            return new ArrayList<>(options.values());
        }

        private List<MinerOreOption> buildVanillaMinerOreOptions(CelestialObject body, ModuleMiner minerData) {
            List<MinerOreOption> options = new ArrayList<>();
            for (ItemStack ore : body.properties()
                .ores()) {
                if (ore == null) continue;
                ItemStackWrapper wrapper = ItemStackWrapper.of(ore);
                if (wrapper == null) continue;
                ItemStack displayStack = ore.copy();
                displayStack.stackSize = 1;
                options.add(
                    new MinerOreOption(
                        wrapper.toKey(),
                        displayStack.getDisplayName(),
                        displayStack,
                        minerData.isBlacklisted(wrapper.toKey())));
            }
            return options;
        }

        private void buildPowerConfigSubMenu(ParentWidget<?> modal, AutomatedOutpost outpost,
            AutomatedOutpostModule module) {
            modal.child(createTitleText("Power Configuration").pos(12, 10));
            modal.child(createFooterButton("Back", true, () -> {
                state.configuringModuleIndex = -1;
                markStructureDirty();
            }).pos(12, 32)
                .size(60, 20));
            modal.child(createBodyText("No settings yet.", EnumColors.MAP_COLOR_TEXT_BODY.getColor()).pos(12, 70));
            modal.child(
                createBodyText(
                    "Generating power: " + ModulePower.GENERATION_EU_PER_TICK + " EU/t",
                    EnumColors.MAP_COLOR_TEXT_TITLE.getColor()).pos(12, 92));
        }

        private String formatAmount(long amount) {
            if (amount < 1000) return String.valueOf(amount);
            if (amount < 1000000) return (amount / 1000) + "k";
            return (amount / 1000000) + "M";
        }

        private String buildPowerSummary(AutomatedOutpost outpost) {
            long generationPerTick = AutomatedOutpost.PASSIVE_GENERATION;
            long drawPerTick = 0L;
            for (AutomatedOutpostModule module : outpost.modules()) {
                if (!module.isOperational()) continue;
                long power = module.getDisplayedPowerEuPerTick();
                if (power < 0) generationPerTick -= power;
                else drawPerTick += power;
            }
            long netPerSecond = (generationPerTick - drawPerTick) * 20L;
            String sign = netPerSecond >= 0 ? "+" : "";
            return "Power: " + outpost
                .getEnergyStored() + "/" + AutomatedOutpost.MAX_ENERGY + " " + sign + netPerSecond + "/s";
        }

        private String buildModuleDescription(OutpostModuleKind kind) {
            return switch (kind) {
                case HAMMER -> "Balances item reserves and exports excess inventory to other stations.";
                case BIG_HAMMER -> "Heavy logistics launcher for larger interstation packages.";
                case MINER -> "Generates one ore per second from this body's available deposits.";
                case POWER -> "Adds extra power generation to support modules and logistics.";
            };
        }

        private String buildModuleStats(OutpostModuleKind kind) {
            var data = OutpostModuleKind.forKind(kind);
            String powerLine = kind == OutpostModuleKind.POWER
                ? "Generates " + (-data.getDisplayedPowerEuPerTick()) + " EU/t"
                : "Consumes " + data.getDisplayedPowerEuPerTick() + " EU/t";
            String restrictionLine = kind == OutpostModuleKind.MINER ? "Only on Automated Outposts" : "Buildable here";
            return powerLine + " | Cap " + data.baseEnergyCapacity + " EU | " + restrictionLine;
        }

        private String buildModuleCost(OutpostModuleKind kind) {
            var data = OutpostModuleKind.forKind(kind);
            StringBuilder sb = new StringBuilder("Cost: ");
            boolean first = true;
            for (Map.Entry<ItemStack, Long> entry : data.getConstructionCost()
                .entrySet()) {
                ItemStack stack = entry.getKey();
                if (!first) sb.append(", ");
                sb.append(entry.getValue())
                    .append(' ')
                    .append(stack == null ? entry.getKey() : stack.getDisplayName());
                first = false;
            }
            return sb.toString();
        }

        private void addFooterButtons(ParentWidget<?> modal, ModalBounds bounds, String cancelLabel,
            Runnable cancelAction, String confirmLabel, Runnable confirmAction, boolean confirmDanger) {
            int btnWidth = 110;
            int modalWidth = bounds.right() - bounds.left();
            int btnY = bounds.bottom() - bounds.top() - 34;
            modal.child(
                createFooterButton(cancelLabel, true, cancelAction).pos(18, btnY)
                    .size(btnWidth, FOOTER_BUTTON_HEIGHT));
            if (confirmDanger) {
                modal.child(
                    createDangerFooterButton(confirmLabel, confirmAction).pos(modalWidth - 18 - btnWidth, btnY)
                        .size(btnWidth, FOOTER_BUTTON_HEIGHT));
            } else {
                modal.child(
                    createFooterButton(confirmLabel, true, confirmAction).pos(modalWidth - 18 - btnWidth, btnY)
                        .size(btnWidth, FOOTER_BUTTON_HEIGHT));
            }
        }

        private ModalBounds calculateManagementBounds() {
            int availableWidth = getAvailableOverlayWidth();
            int availableHeight = getAvailableOverlayHeight();
            int width = Math.min(MODAL_MAX_WIDTH, availableWidth - MODAL_MARGIN_X);
            int height = Math.min(MODAL_MAX_HEIGHT, availableHeight - MODAL_MARGIN_Y);
            int left = (availableWidth - width) / 2;
            int top = (availableHeight - height) / 2;
            return new ModalBounds(left, top, left + width, top + height);
        }

        private ModalBounds createCenteredModalBounds(int width, int height) {
            int left = (getAvailableOverlayWidth() - width) / 2;
            int top = (getAvailableOverlayHeight() - height) / 2;
            return new ModalBounds(left, top, left + width, top + height);
        }

        private int computeContentHeight(@UnknownNullability List<CelestialAsset> assetState) {
            List<CelestialAsset> construction = getConstructionAssets(assetState);
            List<CelestialAsset> deployed = getOperationalAssets(assetState);
            int y = 0;
            if (!construction.isEmpty()) {
                y += 16;
                y += construction.size() * ROW_HEIGHT + Math.max(0, construction.size() - 1) * ROW_SPACING;
                y += 4;
            }
            y += 16;
            if (deployed.isEmpty()) y += 24;
            else y += deployed.size() * ROW_HEIGHT + Math.max(0, deployed.size() - 1) * ROW_SPACING + 8;
            return y;
        }

        private void populateContent(ParentWidget<?> content, int contentWidth, List<CelestialAsset> assetState) {
            List<CelestialAsset> construction = getConstructionAssets(assetState);
            List<CelestialAsset> deployed = getOperationalAssets(assetState);
            int y = 0;
            if (!construction.isEmpty()) {
                content.child(createSectionText("Construction").pos(4, y));
                y += 16;
                for (CelestialAsset a : construction) {
                    content.child(createConstructionRow(a, contentWidth - 8).pos(4, y));
                    y += ROW_HEIGHT + ROW_SPACING;
                }
                y += 4;
            }
            content.child(createSectionText("Assets").pos(4, y));
            y += 16;
            if (deployed.isEmpty()) {
                content
                    .child(createBodyText("No deployed assets", EnumColors.MAP_COLOR_TEXT_MUTED.getColor()).pos(8, y));
                return;
            }
            for (CelestialAsset a : deployed) {
                content.child(createAssetRow(a, contentWidth - 8).pos(4, y));
                y += ROW_HEIGHT + ROW_SPACING;
            }
        }

        private ParentWidget<?> createConstructionRow(CelestialAsset asset, int rowWidth) {
            ParentWidget<?> row = new PassiveRow().widthRelOffset(1f, -8)
                .height(ROW_HEIGHT)
                .background(
                    drawable(
                        (context, x, y, width, height) -> Gui
                            .drawRect(x, y, x + width, y + height, EnumColors.MAP_COLOR_ROW_BG.getColor())));
            row.child(
                createAssetIconWidget(asset.kind, 1.0f).pos(10, 9)
                    .size(16, 16));
            boolean deconstruction = asset.status() == CelestialAsset.Status.DECONSTRUCTION;
            int actionButtonsWidth = ICON_BUTTON_SIZE;
            int textWidth = rowWidth - 32 - actionButtonsWidth - 16;
            row.child(createNameButton(asset, textWidth).pos(32, 4));
            row.child(
                createBodyText(
                    // TODO: Localize
                    (deconstruction ? "Stored: " : "Inventory: ") + callbacks.buildConstructionInventorySummary(asset),
                    EnumColors.MAP_COLOR_TEXT_BODY.getColor()).pos(32, 18)
                        .width(textWidth));
            row.child(
                createGlyphButton(
                    deconstruction ? AssetManagerButtonGlyph.SEND : AssetManagerButtonGlyph.CANCEL,
                    deconstruction ? "Send To..." : "Cancel Build",
                    // TODO: Localize
                    true,
                    () -> handleConstructionAction(asset)).pos(rowWidth - 34, 9));
            return row;
        }

        private ParentWidget<?> createAssetRow(CelestialAsset asset, int rowWidth) {
            ParentWidget<?> row = new PassiveRow().widthRelOffset(1f, -8)
                .height(ROW_HEIGHT)
                .background(
                    drawable(
                        (context, x, y, width, height) -> Gui
                            .drawRect(x, y, x + width, y + height, EnumColors.MAP_COLOR_ROW_BG.getColor())));
            row.child(
                createAssetIconWidget(asset.kind, 1.0f).pos(10, 9)
                    .size(16, 16));
            boolean manageable = callbacks.isManageableStationAsset(asset);
            int actionButtonsWidth = manageable ? (ICON_BUTTON_SIZE * 2 + 4) : ICON_BUTTON_SIZE;
            int textWidth = rowWidth - 32 - actionButtonsWidth - 16;
            row.child(createNameButton(asset, textWidth).pos(32, 4));
            row.child(
                createBodyText(
                    trimToWidth(
                        callbacks.formatAssetKind(asset.kind) + " | " + callbacks.formatAssetLocation(asset.location),
                        textWidth),
                    EnumColors.MAP_COLOR_TEXT_BODY.getColor()).pos(32, 16)
                        .width(textWidth));
            int buttonX = rowWidth - 34;
            if (manageable) {
                row.child(
                    createGlyphButton(
                        AssetManagerButtonGlyph.MANAGE,
                        // TODO: Localize
                        "Manage",
                        true,
                        () -> callbacks.openPendingAssetManagement(asset)).pos(buttonX - 28, 9));
            }
            row.child(
                createGlyphButton(
                    AssetManagerButtonGlyph.DESTROY,
                    // TODO: Localize
                    "Destroy",
                    true,
                    () -> callbacks.openPendingAssetDestruction(asset)).pos(buttonX, 9));
            return row;
        }

        private ButtonWidget<?> createNameButton(CelestialAsset asset, int width) {
            String text = trimToWidth(callbacks.formatAssetDisplayName(asset), Math.max(8, width));
            return new ScrollAwareButtonWidget().size(Math.max(8, width), 12)
                .background(IDrawable.EMPTY)
                .hoverBackground(IDrawable.EMPTY)
                .overlay(drawable((context, x, y, w, h) -> {
                    net.minecraft.client.gui.FontRenderer fr = net.minecraft.client.Minecraft
                        .getMinecraft().fontRenderer;
                    fr.drawStringWithShadow(
                        text,
                        x,
                        y + (h - fr.FONT_HEIGHT) / 2 + 1,
                        EnumColors.MAP_COLOR_TEXT_TITLE.getColor());
                }))
                .hoverOverlay(drawable((context, x, y, w, h) -> {
                    net.minecraft.client.gui.FontRenderer fr = net.minecraft.client.Minecraft
                        .getMinecraft().fontRenderer;
                    fr.drawStringWithShadow(
                        text,
                        x,
                        y + (h - fr.FONT_HEIGHT) / 2 + 1,
                        EnumColors.MAP_COLOR_MODAL_ACCENT.getColor());
                }))
                .onMousePressed(mouseButton -> {
                    if (mouseButton != 0) return true;
                    callbacks.openPendingAssetRename(asset);
                    return true;
                });
        }

        private boolean forwardActiveScroll(UpOrDown direction, int amount) {
            return activeScrollWidget != null && activeScrollWidget.onMouseScroll(direction, amount);
        }

        private ButtonWidget<?> createBackdropButton() {
            return new BackdropButtonWidget().pos(0, 0)
                .widthRel(1f)
                .heightRel(1f)
                .background(IDrawable.EMPTY)
                .hoverBackground(IDrawable.EMPTY)
                .onMousePressed(mouseButton -> true);
        }

        private ParentWidget<?> createModalRoot(ModalBounds bounds) {
            return createModalRoot(
                bounds.left(),
                bounds.top(),
                bounds.right(),
                bounds.bottom(),
                EnumColors.MAP_COLOR_MODAL_BG.getColor(),
                EnumColors.MAP_COLOR_MODAL_ACCENT.getColor());
        }

        private ParentWidget<?> createModalRoot(int left, int top, int right, int bottom) {
            return createModalRoot(
                left,
                top,
                right,
                bottom,
                EnumColors.MAP_COLOR_MODAL_BG.getColor(),
                EnumColors.MAP_COLOR_MODAL_ACCENT.getColor());
        }

        private ParentWidget<?> createModalRoot(int left, int top, int right, int bottom, int backgroundColor,
            int accentColor) {
            return createModalRoot(
                left,
                top,
                right,
                bottom,
                backgroundColor,
                accentColor,
                EnumColors.MAP_COLOR_MODAL_HEADER.getColor());
        }

        private ParentWidget<?> createModalRoot(int left, int top, int right, int bottom, int backgroundColor,
            int accentColor, int headerColor) {
            ParentWidget<?> modal = new ParentWidget<>().pos(left, top)
                .size(right - left, bottom - top);
            PassiveLayer backgroundLayer = new PassiveLayer().pos(0, 0)
                .widthRel(1f)
                .heightRel(1f)
                .background(createModalBackgroundDrawable(backgroundColor, headerColor));
            modal.child(backgroundLayer);
            modal.child(WidgetOutline.create(backgroundLayer, 3, accentColor));
            return modal;
        }

        private TextWidget<?> createTitleText(String text) {
            return new TextWidget<>(IKey.str(text)).color(EnumColors.MAP_COLOR_TEXT_TITLE.getColor())
                .shadow(true);
        }

        private TextWidget<?> createSectionText(String text) {
            return new TextWidget<>(IKey.str(text)).color(EnumColors.MAP_COLOR_TEXT_SECTION.getColor())
                .shadow(true);
        }

        private TextWidget<?> createBodyText(String text, int color) {
            return new TextWidget<>(IKey.str(text)).color(color)
                .shadow(true);
        }

        private ButtonWidget<?> createAssetKindButton(CelestialAsset.Kind kind, String tooltip, boolean enabled,
            Runnable action) {
            return createIconButton(kind, AssetManagerButtonGlyph.NONE, tooltip, enabled, action);
        }

        private ButtonWidget<?> createGlyphButton(AssetManagerButtonGlyph glyph, String tooltip, boolean enabled,
            Runnable action) {
            return createIconButton(null, glyph, tooltip, enabled, action);
        }

        private ButtonWidget<?> createIconButton(CelestialAsset.Kind iconKind, AssetManagerButtonGlyph glyph,
            String tooltip, boolean enabled, Runnable action) {
            ButtonWidget<?> button = new ScrollAwareButtonWidget().size(ICON_BUTTON_SIZE, ICON_BUTTON_SIZE)
                .background(createButtonBackground(enabled, false))
                .hoverBackground(createButtonBackground(enabled, true))
                .tooltip(t -> t.addLine(tooltip))
                .onMousePressed(mouseButton -> {
                    if (mouseButton != 0 || !enabled) return true;
                    action.run();
                    return true;
                });
            if (iconKind != null) button.overlay(createAssetIconDrawable(iconKind, enabled ? 1.0f : 0.45f));
            else button.overlay(
                createGlyphDrawable(
                    glyph,
                    enabled ? EnumColors.MAP_COLOR_TEXT_TITLE.getColor()
                        : EnumColors.MAP_COLOR_TEXT_BTN_DISABLED.getColor()));
            return button;
        }

        private ButtonWidget<?> createFooterButton(String label, boolean enabled, Runnable action) {
            return createTextButton(label, enabled, action, false);
        }

        private ButtonWidget<?> createTabButton(String label, boolean active, Runnable action) {
            return createColoredButton(
                label,
                true,
                action,
                active ? EnumColors.MAP_COLOR_BTN_DISABLED.getColor()
                    : EnumColors.MAP_COLOR_BTN_ENABLED_DEFAULT.getColor(),
                active ? EnumColors.MAP_COLOR_BTN_DISABLED.getColor()
                    : EnumColors.MAP_COLOR_BTN_ENABLED_HOVERED.getColor(),
                active ? EnumColors.MAP_COLOR_BTN_BORDER_DISABLED.getColor()
                    : EnumColors.MAP_COLOR_BTN_BORDER_ENABLED.getColor());
        }

        private ButtonWidget<?> createDangerFooterButton(String label, Runnable action) {
            return createTextButton(label, true, action, true);
        }

        private ButtonWidget<?> createConfigureButton(String label, boolean enabled, Runnable action) {
            return createColoredButton(
                label,
                enabled,
                action,
                EnumColors.MAP_COLOR_BTN_CONFIGURE_DEFAULT.getColor(),
                EnumColors.MAP_COLOR_BTN_CONFIGURE_HOVERED.getColor(),
                EnumColors.MAP_COLOR_BTN_CONFIGURE_BORDER.getColor());
        }

        private ButtonWidget<?> createDisableButton(String label, boolean enabled, Runnable action) {
            return createColoredButton(
                label,
                enabled,
                action,
                EnumColors.MAP_COLOR_BTN_DISABLE_DEFAULT.getColor(),
                EnumColors.MAP_COLOR_BTN_DISABLE_HOVERED.getColor(),
                EnumColors.MAP_COLOR_BTN_DISABLE_BORDER.getColor());
        }

        private ButtonWidget<?> createDestroyModuleButton(boolean enabled, Runnable action) {
            return createColoredButton(
                "Destroy",
                enabled,
                action,
                EnumColors.MAP_COLOR_BTN_DESTROY_DEFAULT.getColor(),
                EnumColors.MAP_COLOR_BTN_DESTROY_HOVERED.getColor(),
                EnumColors.MAP_COLOR_BTN_DESTROY_BORDER.getColor());
        }

        private ButtonWidget<?> createTwoStageDestructiveButton(String label, boolean armed, boolean enabled,
            Runnable action) {
            if (armed) {
                return createColoredButton(
                    label,
                    enabled,
                    action,
                    EnumColors.MAP_COLOR_BTN_DANGER_DEFAULT.getColor(),
                    EnumColors.MAP_COLOR_BTN_DANGER_HOVERED.getColor(),
                    EnumColors.MAP_COLOR_BTN_DANGER_BORDER.getColor());
            }
            return createColoredButton(
                label,
                enabled,
                action,
                EnumColors.MAP_COLOR_BTN_DESTROY_DEFAULT.getColor(),
                EnumColors.MAP_COLOR_BTN_DESTROY_HOVERED.getColor(),
                EnumColors.MAP_COLOR_BTN_DESTROY_BORDER.getColor());
        }

        private ButtonWidget<?> createCheckboxButton(boolean checked, Runnable action) {
            String label = checked ? "X" : "";
            return new ScrollAwareButtonWidget()
                .background(
                    createRectFrameDrawable(
                        EnumColors.MAP_COLOR_BTN_ENABLED_DEFAULT.getColor(),
                        EnumColors.MAP_COLOR_BTN_BORDER_ENABLED.getColor()))
                .hoverBackground(
                    createRectFrameDrawable(
                        EnumColors.MAP_COLOR_BTN_ENABLED_HOVERED.getColor(),
                        EnumColors.MAP_COLOR_BTN_BORDER_ENABLED.getColor()))
                .overlay(drawable((context, x, y, w, h) -> {
                    org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_TEXTURE_2D);
                    com.cleanroommc.modularui.utils.GlStateManager.color(1f, 1f, 1f, 1f);
                    net.minecraft.client.gui.FontRenderer fr = net.minecraft.client.Minecraft
                        .getMinecraft().fontRenderer;
                    int textW = fr.getStringWidth(label);
                    fr.drawStringWithShadow(
                        label,
                        x + (w - textW) / 2,
                        y + (h - fr.FONT_HEIGHT) / 2 + 1,
                        EnumColors.MAP_COLOR_TEXT_TITLE.getColor());
                }))
                .onMousePressed(mouseButton -> {
                    if (mouseButton != 0) return true;
                    action.run();
                    return true;
                });
        }

        private ButtonWidget<?> createColoredButton(String label, boolean enabled, Runnable action, int defaultBg,
            int hoverBg, int border) {
            return new ScrollAwareButtonWidget()
                .background(
                    createRectFrameDrawable(
                        enabled ? defaultBg : EnumColors.MAP_COLOR_BTN_DISABLED.getColor(),
                        enabled ? border : EnumColors.MAP_COLOR_BTN_BORDER_DISABLED.getColor()))
                .hoverBackground(
                    createRectFrameDrawable(
                        enabled ? hoverBg : EnumColors.MAP_COLOR_BTN_DISABLED.getColor(),
                        enabled ? border : EnumColors.MAP_COLOR_BTN_BORDER_DISABLED.getColor()))
                .overlay(drawable((context, x, y, w, h) -> {
                    org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_TEXTURE_2D);
                    com.cleanroommc.modularui.utils.GlStateManager.color(1f, 1f, 1f, 1f);
                    net.minecraft.client.gui.FontRenderer fr = net.minecraft.client.Minecraft
                        .getMinecraft().fontRenderer;
                    int textW = fr.getStringWidth(label);
                    fr.drawStringWithShadow(
                        label,
                        x + (w - textW) / 2,
                        y + (h - fr.FONT_HEIGHT) / 2 + 1,
                        enabled ? EnumColors.MAP_COLOR_TEXT_BTN_ENABLED.getColor()
                            : EnumColors.MAP_COLOR_TEXT_BTN_DISABLED.getColor());
                }))
                .onMousePressed(mouseButton -> {
                    if (mouseButton != 0 || !enabled) return true;
                    action.run();
                    return true;
                });
        }

        private ButtonWidget<?> createTextButton(String label, boolean enabled, Runnable action, boolean danger) {
            return new ScrollAwareButtonWidget().background(createTextButtonBackground(enabled, false, danger))
                .hoverBackground(createTextButtonBackground(enabled, true, danger))
                .overlay(drawable((context, x, y, w, h) -> {
                    org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_TEXTURE_2D);
                    com.cleanroommc.modularui.utils.GlStateManager.color(1f, 1f, 1f, 1f);
                    net.minecraft.client.gui.FontRenderer fr = net.minecraft.client.Minecraft
                        .getMinecraft().fontRenderer;
                    int textW = fr.getStringWidth(label);
                    int color = enabled ? EnumColors.MAP_COLOR_TEXT_BTN_ENABLED.getColor()
                        : EnumColors.MAP_COLOR_TEXT_BTN_DISABLED.getColor();
                    fr.drawStringWithShadow(label, x + (w - textW) / 2, y + (h - fr.FONT_HEIGHT) / 2 + 1, color);
                }))
                .onMousePressed(mouseButton -> {
                    if (mouseButton != 0 || !enabled) return true;
                    action.run();
                    return true;
                });
        }

        private IDrawable createButtonBackground(boolean enabled, boolean hovered) {
            int bg = !enabled ? EnumColors.MAP_COLOR_BTN_DISABLED.getColor()
                : hovered ? EnumColors.MAP_COLOR_BTN_ENABLED_HOVERED.getColor()
                    : EnumColors.MAP_COLOR_BTN_ENABLED_DEFAULT.getColor();
            int border = enabled ? EnumColors.MAP_COLOR_BTN_BORDER_ENABLED.getColor()
                : EnumColors.MAP_COLOR_BTN_BORDER_DISABLED.getColor();
            return createRectFrameDrawable(bg, border);
        }

        private IDrawable createTextButtonBackground(boolean enabled, boolean hovered, boolean danger) {
            if (danger) {
                int bg = hovered ? EnumColors.MAP_COLOR_BTN_DANGER_HOVERED.getColor()
                    : EnumColors.MAP_COLOR_BTN_DANGER_DEFAULT.getColor();
                return createRectFrameDrawable(bg, EnumColors.MAP_COLOR_BTN_DANGER_BORDER.getColor());
            }
            return createButtonBackground(enabled, hovered);
        }

        private IDrawable createRectFrameDrawable(int backgroundColor, int borderColor) {
            return drawable((context, x, y, width, height) -> {
                Gui.drawRect(x, y, x + width, y + height, backgroundColor);
                Gui.drawRect(x, y, x + width, y + 1, borderColor);
                Gui.drawRect(x, y + height - 1, x + width, y + height, borderColor);
                Gui.drawRect(x, y, x + 1, y + height, borderColor);
                Gui.drawRect(x + width - 1, y, x + width, y + height, borderColor);
            });
        }

        private IDrawable createAssetIconDrawable(CelestialAsset.Kind kind, float alpha) {
            return drawable(
                (context, x, y, width, height) -> callbacks
                    .drawAssetIcon(kind, x + (width - 14) / 2, y + (height - 14) / 2, 14, alpha));
        }

        private Widget<?> createAssetIconWidget(CelestialAsset.Kind kind, float alpha) {
            return createAssetIconDrawable(kind, alpha).asWidget();
        }

        private IDrawable createGlyphDrawable(AssetManagerButtonGlyph glyph, int color) {
            return drawable((context, x, y, width, height) -> drawGlyph(x, y, width, height, glyph, color));
        }

        private Widget<?> createCenteredLargeText(String text, float scale, int color) {
            return drawable((context, x, y, width, height) -> {
                Minecraft mc = Minecraft.getMinecraft();
                GlStateManager.pushMatrix();
                GlStateManager.translate(x + width / 2f, y, 0);
                GlStateManager.scale(scale, scale, 1f);
                float textWidth = mc.fontRenderer.getStringWidth(text);
                mc.fontRenderer.drawStringWithShadow(text, Math.round(-textWidth / 2f), 0, color);
                GlStateManager.popMatrix();
            }).asWidget();
        }

        private void drawGlyph(int x, int y, int width, int height, AssetManagerButtonGlyph glyph, int color) {
            int centerX = x + width / 2;
            int centerY = y + height / 2;
            switch (glyph) {
                case CANCEL, DESTROY, CLOSE -> drawGlyphX(centerX, centerY, 5, color);
                case SEND -> drawGlyphSend(centerX, centerY, color);
                case MANAGE -> drawGlyphManage(centerX, centerY, color);
                case NONE -> {}
            }
        }

        private void drawGlyphX(int cx, int cy, int radius, int color) {
            for (int i = -radius; i <= radius; i++) {
                Gui.drawRect(cx + i, cy + i, cx + i + 1, cy + i + 1, color);
                Gui.drawRect(cx + i, cy - i, cx + i + 1, cy - i + 1, color);
            }
        }

        private void drawGlyphSend(int cx, int cy, int color) {
            Gui.drawRect(cx - 5, cy - 1, cx + 3, cy + 1, color);
            Gui.drawRect(cx + 2, cy - 3, cx + 3, cy + 4, color);
            Gui.drawRect(cx + 3, cy - 2, cx + 4, cy + 3, color);
            Gui.drawRect(cx + 4, cy - 1, cx + 5, cy + 2, color);
            Gui.drawRect(cx + 5, cy, cx + 6, cy + 1, color);
        }

        private void drawGlyphManage(int cx, int cy, int color) {
            Gui.drawRect(cx - 5, cy - 4, cx + 6, cy - 3, color);
            Gui.drawRect(cx - 5, cy, cx + 6, cy + 1, color);
            Gui.drawRect(cx - 5, cy + 4, cx + 6, cy + 5, color);
        }

        private IDrawable createModalBackgroundDrawable(int backgroundColor, int headerColor) {
            return drawable((context, x, y, width, height) -> {
                Gui.drawRect(x, y, x + width, y + height, backgroundColor);
                if (headerColor >= 0) Gui.drawRect(x, y, x + width, y + HEADER_HEIGHT, headerColor);
            });
        }

        private List<CelestialAsset> getConstructionAssets(List<CelestialAsset> assets) {
            List<CelestialAsset> matching = new ArrayList<>();
            for (CelestialAsset asset : assets) {
                if (asset.status() == CelestialAsset.Status.CONSTRUCTION_SITE
                    || asset.status() == CelestialAsset.Status.DECONSTRUCTION) matching.add(asset);
            }
            return matching;
        }

        private List<CelestialAsset> getOperationalAssets(List<CelestialAsset> assets) {
            List<CelestialAsset> matching = new ArrayList<>();
            for (CelestialAsset asset : assets) {
                if (asset.status() == CelestialAsset.Status.OPERATIONAL) matching.add(asset);
            }
            return matching;
        }

        private void handleConstructionAction(CelestialAsset asset) {
            if (asset.status() == CelestialAsset.Status.DECONSTRUCTION) {
                callbacks.openPendingResourceTransfer(asset);
                return;
            }
            if (callbacks.isCreativeBuildModeEnabled()) {
                CelestialAssetStore.cancelConstruction(asset.assetId);
                callbacks.showActionStatus("Construction canceled");
                return;
            }
            if (callbacks.hasStoredConstructionResources(asset)) {
                callbacks.openPendingConstructionCancellation(asset);
                return;
            }
            CelestialAssetStore.cancelConstruction(asset.assetId);
            callbacks.showActionStatus("Construction canceled");
        }

        private void updateModalBounds(int left, int top, int right, int bottom) {
            modalLeft = left;
            modalTop = top;
            modalRight = right;
            modalBottom = bottom;
        }

        private void clearBounds() {
            modalLeft = modalTop = modalRight = modalBottom = 0;
            scrollLeft = scrollTop = scrollRight = scrollBottom = 0;
        }

        private int getAvailableOverlayWidth() {
            int width = getArea().width;
            if (hasParent()) width = Math.max(width, getParentArea().width - Math.max(0, getArea().rx));
            return width;
        }

        private int getAvailableOverlayHeight() {
            int height = getArea().height;
            if (hasParent()) height = Math.max(height, getParentArea().height - Math.max(0, getArea().ry));
            return height;
        }

        private void clearMainPanelState() {
            mainScrollWidget = null;
            mainScrollContent = null;
            mainScrollData = null;
            modalScrollWidget = null;
            modalScrollData = null;
            modalTextFields.clear();
            mainContentWidth = 0;
            mainContentHeight = 0;
        }

        private void clearArmedDestructiveActions() {
            state.armedModuleDestroyIndex = -1;
            state.armedDumpResourceKey = null;
        }

        private boolean hasFocusedModalTextField() {
            if (getContext() == null) return false;
            for (TextFieldWidget field : modalTextFields) {
                if (field != null && field.isValid() && getContext().isFocused(field)) {
                    return true;
                }
            }
            return false;
        }

        private String trimToWidth(String text, int width) {
            return Minecraft.getMinecraft().fontRenderer.trimStringToWidth(text, width);
        }

        private IDrawable drawable(DrawableCommand drawCommand) {
            return (context, x, y, width, height, widgetTheme) -> drawCommand.draw(context, x, y, width, height);
        }

        private enum AssetManagerButtonGlyph {
            NONE,
            CLOSE,
            CANCEL,
            SEND,
            DESTROY,
            MANAGE
        }

        private final class PassiveRow extends ParentWidget<PassiveRow> {

            @Override
            public boolean canHover() {
                return false;
            }

            @Override
            public boolean canHoverThrough() {
                return true;
            }
        }

        private final class ScrollAwareButtonWidget extends ButtonWidget<ScrollAwareButtonWidget> {

            @Override
            public boolean onMouseScroll(UpOrDown scrollDirection, int amount) {
                return super.onMouseScroll(scrollDirection, amount) || forwardActiveScroll(scrollDirection, amount);
            }
        }

        private final class BackdropButtonWidget extends ButtonWidget<BackdropButtonWidget> {

            @Override
            public boolean onMouseScroll(UpOrDown scrollDirection, int amount) {
                return true;
            }
        }
    }
}
