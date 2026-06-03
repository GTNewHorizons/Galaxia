package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.item.ItemStack;

import org.jetbrains.annotations.UnknownNullability;

import com.cleanroommc.modularui.api.UpOrDown;
import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.utils.GlStateManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.ScrollWidget;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widget.scroll.VerticalScrollData;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.gtnewhorizons.galaxia.client.CelestialClient;
import com.gtnewhorizons.galaxia.client.EnumColors;
import com.gtnewhorizons.galaxia.client.gui.mui.ItemPickerScreen;
import com.gtnewhorizons.galaxia.client.gui.station.StationManagementScreen;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.LogisticsResourceConfig;

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

public final class StarmapAssetActions {

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

        void openAssetActions(OrbitalAssetUiState state, CelestialObject body) {
            if (body == null || body.objectClass() == CelestialObject.Class.GALAXY) return;
            state.openAssetActions(body);
            closePendingAssetRename(state);
        }

        void closeAssetActions(OrbitalAssetUiState state) {
            state.closeAssetActions();
            closePendingAssetRename(state);
        }

        void createBaseStation(CelestialObject body) {
            if (body == null) return;
            // TODO: Localize
            callbacks.showActionStatus("Stations must be placed with a controller block");
        }

        void triggerAssetCreation(OrbitalAssetUiState state, CelestialObject body, CelestialAsset.Kind kind,
            boolean openActionsFirst) {
            if (body == null) return;
            if (openActionsFirst) openAssetActions(state, body);
            CelestialAsset.Location location = getDefaultAssetLocation(kind);
            String displayName = buildDefaultAssetDisplayName(body, kind);
            if (kind == CelestialAsset.Kind.STATION) {
                callbacks.showActionStatus("Stations must be placed with a controller block");
                return;
            }
            if (callbacks.isCreativeBuildModeEnabled()) {
                CelestialAsset asset = CelestialAsset.create(body.id(), kind, true);
                asset.setDisplayName(displayName);
                if (CelestialClient.registerAsset(body.id(), asset)) {
                    callbacks.showActionStatus(assetSupport.formatAssetKind(kind) + " creation requested");
                } else {
                    callbacks.showActionStatus(assetSupport.formatAssetKind(kind) + " creation failed");
                }
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
                CelestialAsset asset = CelestialAsset
                    .create(state.pendingAssetCreation.celestialObjectId(), state.pendingAssetCreation.kind(), true);
                asset.setDisplayName(state.pendingAssetCreation.displayName());
                if (!CelestialClient.registerAsset(state.pendingAssetCreation.celestialObjectId(), asset)) {
                    callbacks.showActionStatus(
                        assetSupport.formatAssetKind(state.pendingAssetCreation.kind()) + " creation failed");
                    return;
                }

                callbacks.showActionStatus(
                    // TODO: Localize
                    assetSupport.formatAssetKind(state.pendingAssetCreation.kind()) + " creation requested");
            } else {
                CelestialAsset asset = CelestialAsset
                    .create(state.pendingAssetCreation.celestialObjectId(), state.pendingAssetCreation.kind(), false);
                asset.setDisplayName(state.pendingAssetCreation.displayName());
                if (!CelestialClient.registerAsset(state.pendingAssetCreation.celestialObjectId(), asset)) {
                    callbacks.showActionStatus(
                        assetSupport.formatAssetKind(state.pendingAssetCreation.kind()) + " construction failed");
                    return;
                }
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
            if (CelestialClient.destroyAsset(state.pendingAssetDestruction.asset().assetId)) {
                // TODO: Localize
                callbacks.showActionStatus("Asset destroyed");
                state.pendingAssetDestruction = null;
                return;
            }
            callbacks.showActionStatus("Destroy failed");
        }

        void openStationManagement(OrbitalAssetUiState state, CelestialAsset asset) {
            if (asset == null || !assetSupport.isManageableStationAsset(asset)) return;
            StationManagementScreen.open(asset.assetId, callbacks.isCreativeBuildModeEnabled());
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
            if (CelestialClient.startDeconstruction(state.pendingConstructionCancellation.asset().assetId)) {
                // TODO: Localize
                callbacks.showActionStatus("Construction site converted to deconstruction");
                state.pendingConstructionCancellation = null;
                return;
            }
            callbacks.showActionStatus("Construction cancellation failed");
        }

        void openPendingResourceTransfer(OrbitalAssetUiState state, CelestialObject root, CelestialAsset asset) {
            if (asset == null) return;
            state.pendingResourceTransfer = new PendingResourceTransfer(
                asset,
                assetSupport.getTransferTargetsInSystem(root, state.assetActionsBody));
        }

        void dismissPendingResourceTransfer(OrbitalAssetUiState state) {
            state.pendingResourceTransfer = null;
        }

        void sendPendingResourceTransfer(OrbitalAssetUiState state, StationTransferTarget target) {
            if (state.pendingResourceTransfer != null) {
                callbacks.createResourceTransfer(state.assetActionsBody, state.pendingResourceTransfer.asset(), target);
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
            if (CelestialClient.renameAsset(state.pendingAssetRename.asset().assetId, renamed)) {
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

        CelestialObject assetActionsBody;
        PendingAssetCreation pendingAssetCreation;
        PendingAssetDestruction pendingAssetDestruction;
        PendingConstructionCancellation pendingConstructionCancellation;
        PendingResourceTransfer pendingResourceTransfer;
        PendingAssetRename pendingAssetRename;

        boolean isAssetActionsOpen() {
            return assetActionsBody != null;
        }

        boolean hasBlockingModal() {
            return pendingAssetCreation != null || pendingAssetDestruction != null
                || pendingConstructionCancellation != null
                || pendingResourceTransfer != null
                || pendingAssetRename != null;
        }

        void openAssetActions(CelestialObject body) {
            assetActionsBody = body;
            clearTransientState();
        }

        void closeAssetActions() {
            assetActionsBody = null;
            clearTransientState();
        }

        void clearTransientState() {
            pendingAssetCreation = null;
            pendingAssetDestruction = null;
            pendingConstructionCancellation = null;
            pendingResourceTransfer = null;
            pendingAssetRename = null;
        }
    }

    public static final class StarmapAssetActionsWidget extends ParentWidget<StarmapAssetActionsWidget> {

        interface Callbacks {

            int getViewportWidth();

            int getViewportHeight();

            boolean isCreativeBuildModeEnabled();

            boolean isGT5AutomationAvailable();

            boolean canCreateBaseStation(CelestialObject body);

            boolean canCreateAutomatedStation(CelestialObject body);

            boolean canCreateAutomatedFacility(CelestialObject body);

            boolean hasStoredConstructionResources(CelestialAsset asset);

            boolean isManageableStationAsset(CelestialAsset asset);

            String formatAssetDisplayName(CelestialAsset asset);

            String buildConstructionInventorySummary(CelestialAsset asset);

            String formatAssetKind(CelestialAsset.Kind kind);

            String formatAssetLocation(CelestialAsset.Location location);

            void drawAssetIcon(CelestialAsset.Kind kind, int x, int y, int size, float alpha);

            void closeAssetActions();

            void createBaseStation(CelestialObject body);

            void triggerAssetCreation(CelestialObject body, CelestialAsset.Kind kind, boolean openActionsFirst);

            void openPendingAssetRename(CelestialAsset asset);

            void openPendingConstructionCancellation(CelestialAsset asset);

            void openPendingResourceTransfer(CelestialAsset asset);

            void openStationManagement(CelestialAsset asset);

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

            void dismissPendingModalByOutsideClick();

            void showActionStatus(String message);
        }

        private static final String PANEL_TITLE = "Manage Assets";
        private static final float ACTIONS_MODAL_WIDTH_REL = 0.70f;
        private static final float ACTIONS_MODAL_HEIGHT_REL = 0.70f;
        private static final float ACTIONS_MODAL_CENTER_REL = 0.50f;
        private static final float ACTIONS_MODAL_CENTER_ANCHOR = 0.50f;
        private static final float ACTIONS_MODAL_LEFT_REL = (1f - ACTIONS_MODAL_WIDTH_REL) / 2f;
        private static final float ACTIONS_MODAL_TOP_REL = (1f - ACTIONS_MODAL_HEIGHT_REL) / 2f;
        private static final int HEADER_HEIGHT = 28;
        private static final int CONTENT_TOP = 54;
        private static final int CONTENT_PADDING = 10;
        private static final int CONTENT_SCROLLBAR_GAP = 14;
        private static final int CONTENT_BOTTOM_PADDING = 12;
        private static final int PANEL_TITLE_X = 12;
        private static final int PANEL_TITLE_Y = 10;
        private static final int PANEL_CONTEXT_TEXT_GAP = 12;
        private static final int PANEL_RIGHT_PADDING = 40;
        private static final int PANEL_TITLE_CONTEXT_GAP = 24;
        private static final int CLOSE_BUTTON_RIGHT_INSET = 28;
        private static final int CLOSE_BUTTON_TOP = 6;
        private static final int CREATE_BUTTON_TOP = 30;
        private static final int CREATE_BUTTON_LEFT = 14;
        private static final int CREATE_BUTTON_GAP = 28;
        private static final int ROW_HEIGHT = 42;
        private static final int ROW_SPACING = 6;
        private static final int ROW_LEFT_PADDING = 4;
        private static final int ROW_WIDTH_INSET = 8;
        private static final int SECTION_HEADER_HEIGHT = 16;
        private static final int SECTION_BOTTOM_GAP = 4;
        private static final int EMPTY_ROW_TEXT_X = 8;
        private static final int ROW_ICON_X = 10;
        private static final int ROW_ICON_Y = 9;
        private static final int ROW_ICON_SLOT_SIZE = 16;
        private static final int ROW_ICON_DRAW_SIZE = 14;
        private static final int ROW_TEXT_LEFT = 32;
        private static final int ROW_NAME_Y = 4;
        private static final int ROW_DETAIL_Y = 18;
        private static final int ROW_TEXT_RIGHT_GAP = 16;
        private static final int ROW_ACTION_BUTTON_GAP = 4;
        private static final int ROW_ACTION_BUTTON_RIGHT_INSET = 34;
        private static final int ROW_SECONDARY_ACTION_OFFSET = 28;
        private static final int NAME_BUTTON_MIN_WIDTH = 8;
        private static final int NAME_BUTTON_HEIGHT = 12;
        private static final int TEXT_BASELINE_OFFSET = 1;
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
        private int lastAssetListSignature = 0;

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

        StarmapAssetActionsWidget(OrbitalAssetUiState state, Callbacks callbacks) {
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
                lastAssetListSignature = 0;
                setEnabled(false);
                size(0, 0);
                return;
            }

            setEnabled(true);
            size(callbacks.getViewportWidth(), callbacks.getViewportHeight());

            if (shouldShowPanel()) {
                int assetListSignature = computeAssetListSignature(state.assetActionsBody);
                if (assetListSignature != lastAssetListSignature) {
                    lastAssetListSignature = assetListSignature;
                    markContentDirty();
                }
            } else {
                lastAssetListSignature = 0;
            }

            // Consume item picker results even if the starmap was closed and reopened
            // between the button click and the user returning from the item picker screen.
            if (ItemPickerScreen.hasPendingPickForOutpost()) {
                CelestialAsset.ID targetId = ItemPickerScreen.getPendingForOutpostId();
                ItemStack pickedStack = ItemPickerScreen.pollPendingPickForOutpost();
                AutomatedFacility outpost = null;
                if (targetId != null && CelestialClient.getByAssetId(targetId) instanceof AutomatedFacility o) {
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
                        CelestialClient.updateLogisticsConfig(outpost.assetId, wrapper, newCfg);
                    } else if (wrapper != null) {
                        Galaxia.LOG.info(
                            "[Outpost UI] Ignored item picker add for {} on outpost {} because it is already tracked",
                            wrapper.toKey(),
                            outpost.assetId);
                    }
                    markStructureDirty();
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

        private int computeAssetListSignature(CelestialObject body) {
            if (body == null) return 0;

            List<CelestialAsset> assets = new ArrayList<>(CelestialClient.getState(body.id()));
            assets.sort(Comparator.comparing(asset -> asset.assetId.toString()));

            int result = 1;
            for (CelestialAsset asset : assets) {
                result = 31 * result + asset.assetId.hashCode();
                result = 31 * result + asset.kind.hashCode();
                result = 31 * result + asset.status()
                    .hashCode();
                result = 31 * result + asset.displayName()
                    .hashCode();
                result = 31 * result + asset.getSyncRevision();
            }
            return result;
        }

        @Override
        public void drawBackground(ModularGuiContext context, WidgetThemeEntry widgetTheme) {
            if (!shouldShowOverlay()) return;
            super.drawBackground(context, widgetTheme);
        }

        private boolean shouldShowOverlay() {
            return state.isAssetActionsOpen();
        }

        private boolean shouldShowPanel() {
            return state.isAssetActionsOpen() && !state.hasBlockingModal();
        }

        private void rebuildChildren() {
            clearMainPanelState();
            activeScrollWidget = null;
            removeAll();
            clearBounds();
            CelestialObject body = state.assetActionsBody;
            if (body == null) return;
            child(createBackdropButton());
            if (state.hasBlockingModal()) {
                buildPendingModal();
                return;
            }
            buildMainPanel(body);
            refreshMainPanelContent();
        }

        private void buildMainPanel(CelestialObject body) {
            ModalBounds bounds = calculateActionsBounds();
            updateModalBounds(bounds.left(), bounds.top(), bounds.right(), bounds.bottom());
            int modalWidth = bounds.right() - bounds.left();
            int modalHeight = bounds.bottom() - bounds.top();
            int contentTop = CONTENT_TOP + SECTION_HEADER_HEIGHT;
            int contentHeight = modalHeight - contentTop - CONTENT_BOTTOM_PADDING;
            int contentWidth = modalWidth - (CONTENT_PADDING * 2) - CONTENT_SCROLLBAR_GAP;
            scrollLeft = bounds.left() + CONTENT_PADDING;
            scrollTop = bounds.top() + contentTop;
            scrollRight = scrollLeft + contentWidth;
            scrollBottom = scrollTop + contentHeight;
            mainContentWidth = contentWidth;
            mainContentHeight = contentHeight;
            ParentWidget<?> modal = createActionsModalRoot();
            modal.child(createTitleText(PANEL_TITLE).pos(PANEL_TITLE_X, PANEL_TITLE_Y));
            int titleRight = PANEL_TITLE_X + Minecraft.getMinecraft().fontRenderer.getStringWidth(PANEL_TITLE);
            int assetNameMaxWidth = Math
                .max(0, modalWidth - PANEL_RIGHT_PADDING - (titleRight + PANEL_TITLE_CONTEXT_GAP));
            if (assetNameMaxWidth > 0) {
                String assetName = trimToWidth(body.displayName(), assetNameMaxWidth);
                int assetNameWidth = Minecraft.getMinecraft().fontRenderer.getStringWidth(assetName);
                int assetNameX = Math
                    .max(titleRight + PANEL_CONTEXT_TEXT_GAP, modalWidth - PANEL_RIGHT_PADDING - assetNameWidth);
                modal.child(
                    createBodyText(assetName, EnumColors.MAP_COLOR_TEXT_BODY.getColor())
                        .pos(assetNameX, PANEL_TITLE_Y));
            }
            modal.child(
                createGlyphButton(AssetManagerButtonGlyph.CLOSE, "Close", true, callbacks::closeAssetActions)
                    .pos(modalWidth - CLOSE_BUTTON_RIGHT_INSET, CLOSE_BUTTON_TOP));
            modal.child(
                createAssetKindButton(
                    CelestialAsset.Kind.STATION,
                    "Create Station",
                    callbacks.canCreateBaseStation(body),
                    () -> callbacks.createBaseStation(body)).pos(CREATE_BUTTON_LEFT, CREATE_BUTTON_TOP));
            modal.child(
                createAssetKindButton(
                    CelestialAsset.Kind.AUTOMATED_STATION,
                    "Create Automated Station",
                    callbacks.canCreateAutomatedStation(body),
                    () -> callbacks.triggerAssetCreation(body, CelestialAsset.Kind.AUTOMATED_STATION, false))
                        .pos(CREATE_BUTTON_LEFT + CREATE_BUTTON_GAP, CREATE_BUTTON_TOP));
            modal.child(
                createAssetKindButton(
                    CelestialAsset.Kind.AUTOMATED_OUTPOST,
                    "Create Automated Outpost",
                    callbacks.canCreateAutomatedFacility(body),
                    () -> callbacks.triggerAssetCreation(body, CelestialAsset.Kind.AUTOMATED_OUTPOST, false))
                        .pos(CREATE_BUTTON_LEFT + CREATE_BUTTON_GAP * 2, CREATE_BUTTON_TOP));
            if (!callbacks.isGT5AutomationAvailable()) {
                modal.child(
                    createBodyText("GT5U required for automated assets", EnumColors.MAP_COLOR_TEXT_MUTED.getColor())
                        .pos(CREATE_BUTTON_LEFT + CREATE_BUTTON_GAP * 3 + PANEL_CONTEXT_TEXT_GAP, 36));
            }
            modal.child(createSectionText("Assets").pos(CONTENT_PADDING + ROW_LEFT_PADDING, CONTENT_TOP));
            VerticalScrollData scrollData = new VerticalScrollData();
            mainScrollData = scrollData;
            ScrollWidget<?> scroll = new ScrollWidget<>(scrollData).pos(CONTENT_PADDING, contentTop)
                .widthRelOffset(1f, -(CONTENT_PADDING * 2) - CONTENT_SCROLLBAR_GAP)
                .heightRelOffset(1f, -(contentTop + CONTENT_BOTTOM_PADDING))
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
            CelestialObject body = state.assetActionsBody;
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

        private ModalBounds calculateActionsBounds() {
            int availableWidth = getAvailableOverlayWidth();
            int availableHeight = getAvailableOverlayHeight();
            int left = Math.round(availableWidth * ACTIONS_MODAL_LEFT_REL);
            int top = Math.round(availableHeight * ACTIONS_MODAL_TOP_REL);
            int width = Math.round(availableWidth * ACTIONS_MODAL_WIDTH_REL);
            int height = Math.round(availableHeight * ACTIONS_MODAL_HEIGHT_REL);
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
            if (deployed.isEmpty()) y += 24;
            else y += deployed.size() * ROW_HEIGHT + Math.max(0, deployed.size() - 1) * ROW_SPACING + 8;
            return y;
        }

        private void populateContent(ParentWidget<?> content, int contentWidth, List<CelestialAsset> assetState) {
            List<CelestialAsset> construction = getConstructionAssets(assetState);
            List<CelestialAsset> deployed = getOperationalAssets(assetState);
            int y = 0;
            if (!construction.isEmpty()) {
                content.child(createSectionText("Construction").pos(ROW_LEFT_PADDING, y));
                y += SECTION_HEADER_HEIGHT;
                for (CelestialAsset a : construction) {
                    content.child(createConstructionRow(a, contentWidth - ROW_WIDTH_INSET).pos(ROW_LEFT_PADDING, y));
                    y += ROW_HEIGHT + ROW_SPACING;
                }
                y += SECTION_BOTTOM_GAP;
            }
            if (deployed.isEmpty()) {
                content.child(
                    createBodyText("No deployed assets", EnumColors.MAP_COLOR_TEXT_MUTED.getColor())
                        .pos(EMPTY_ROW_TEXT_X, y));
                return;
            }
            for (CelestialAsset a : deployed) {
                content.child(createAssetRow(a, contentWidth - ROW_WIDTH_INSET).pos(ROW_LEFT_PADDING, y));
                y += ROW_HEIGHT + ROW_SPACING;
            }
        }

        private ParentWidget<?> createConstructionRow(CelestialAsset asset, int rowWidth) {
            ParentWidget<?> row = new PassiveRow().widthRelOffset(1f, -ROW_WIDTH_INSET)
                .height(ROW_HEIGHT)
                .background(
                    drawable(
                        (context, x, y, width, height) -> Gui
                            .drawRect(x, y, x + width, y + height, EnumColors.MAP_COLOR_ROW_BG.getColor())));
            row.child(
                createAssetIconWidget(asset.kind, 1.0f).pos(ROW_ICON_X, ROW_ICON_Y)
                    .size(ROW_ICON_SLOT_SIZE, ROW_ICON_SLOT_SIZE));
            boolean deconstruction = asset.status() == CelestialAsset.Status.DECONSTRUCTION;
            int actionButtonsWidth = ICON_BUTTON_SIZE;
            int textWidth = rowWidth - ROW_TEXT_LEFT - actionButtonsWidth - ROW_TEXT_RIGHT_GAP;
            row.child(createNameButton(asset, textWidth).pos(ROW_TEXT_LEFT, ROW_NAME_Y));
            row.child(
                createBodyText(
                    // TODO: Localize
                    (deconstruction ? "Stored: " : "Inventory: ") + callbacks.buildConstructionInventorySummary(asset),
                    EnumColors.MAP_COLOR_TEXT_BODY.getColor()).pos(ROW_TEXT_LEFT, ROW_DETAIL_Y)
                        .width(textWidth));
            row.child(
                createGlyphButton(
                    deconstruction ? AssetManagerButtonGlyph.SEND : AssetManagerButtonGlyph.CANCEL,
                    deconstruction ? "Send To..." : "Cancel Build",
                    // TODO: Localize
                    true,
                    () -> handleConstructionAction(asset)).pos(rowWidth - ROW_ACTION_BUTTON_RIGHT_INSET, ROW_ICON_Y));
            return row;
        }

        private ParentWidget<?> createAssetRow(CelestialAsset asset, int rowWidth) {
            ParentWidget<?> row = new PassiveRow().widthRelOffset(1f, -ROW_WIDTH_INSET)
                .height(ROW_HEIGHT)
                .background(
                    drawable(
                        (context, x, y, width, height) -> Gui
                            .drawRect(x, y, x + width, y + height, EnumColors.MAP_COLOR_ROW_BG.getColor())));
            row.child(
                createAssetIconWidget(asset.kind, 1.0f).pos(ROW_ICON_X, ROW_ICON_Y)
                    .size(ROW_ICON_SLOT_SIZE, ROW_ICON_SLOT_SIZE));
            boolean manageable = callbacks.isManageableStationAsset(asset);
            int actionButtonsWidth = manageable ? (ICON_BUTTON_SIZE * 2 + ROW_ACTION_BUTTON_GAP) : ICON_BUTTON_SIZE;
            int textWidth = rowWidth - ROW_TEXT_LEFT - actionButtonsWidth - ROW_TEXT_RIGHT_GAP;
            row.child(createNameButton(asset, textWidth).pos(ROW_TEXT_LEFT, ROW_NAME_Y));
            row.child(
                createBodyText(
                    trimToWidth(
                        callbacks.formatAssetKind(asset.kind) + " | " + callbacks.formatAssetLocation(asset.location),
                        textWidth),
                    EnumColors.MAP_COLOR_TEXT_BODY.getColor()).pos(ROW_TEXT_LEFT, ROW_DETAIL_Y)
                        .width(textWidth));
            int buttonX = rowWidth - ROW_ACTION_BUTTON_RIGHT_INSET;
            if (manageable) {
                row.child(
                    createGlyphButton(
                        AssetManagerButtonGlyph.MANAGE,
                        // TODO: Localize
                        "Manage",
                        true,
                        () -> callbacks.openStationManagement(asset))
                            .pos(buttonX - ROW_SECONDARY_ACTION_OFFSET, ROW_ICON_Y));
            }
            row.child(
                createGlyphButton(
                    AssetManagerButtonGlyph.DESTROY,
                    // TODO: Localize
                    "Destroy",
                    asset.kind == CelestialAsset.Kind.STATION ? callbacks.isCreativeBuildModeEnabled() : true,
                    () -> callbacks.openPendingAssetDestruction(asset)).pos(buttonX, ROW_ICON_Y));
            return row;
        }

        private ButtonWidget<?> createNameButton(CelestialAsset asset, int width) {
            int buttonWidth = Math.max(NAME_BUTTON_MIN_WIDTH, width);
            String text = trimToWidth(callbacks.formatAssetDisplayName(asset), buttonWidth);
            return new ScrollAwareButtonWidget().size(buttonWidth, NAME_BUTTON_HEIGHT)
                .background(IDrawable.EMPTY)
                .hoverBackground(IDrawable.EMPTY)
                .overlay(drawable((context, x, y, w, h) -> {
                    net.minecraft.client.gui.FontRenderer fr = net.minecraft.client.Minecraft
                        .getMinecraft().fontRenderer;
                    fr.drawStringWithShadow(
                        text,
                        x,
                        y + (h - fr.FONT_HEIGHT) / 2 + TEXT_BASELINE_OFFSET,
                        EnumColors.MAP_COLOR_TEXT_TITLE.getColor());
                }))
                .hoverOverlay(drawable((context, x, y, w, h) -> {
                    net.minecraft.client.gui.FontRenderer fr = net.minecraft.client.Minecraft
                        .getMinecraft().fontRenderer;
                    fr.drawStringWithShadow(
                        text,
                        x,
                        y + (h - fr.FONT_HEIGHT) / 2 + TEXT_BASELINE_OFFSET,
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

        private ParentWidget<?> createActionsModalRoot() {
            return createRelativeModalRoot(
                ACTIONS_MODAL_WIDTH_REL,
                ACTIONS_MODAL_HEIGHT_REL,
                EnumColors.MAP_COLOR_MODAL_BG.getColor(),
                EnumColors.MAP_COLOR_MODAL_ACCENT.getColor(),
                EnumColors.MAP_COLOR_MODAL_HEADER.getColor());
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
            addModalFrame(modal, backgroundColor, accentColor, headerColor);
            return modal;
        }

        private ParentWidget<?> createRelativeModalRoot(float width, float height, int backgroundColor, int accentColor,
            int headerColor) {
            ParentWidget<?> modal = new ParentWidget<>()
                .leftRel(ACTIONS_MODAL_CENTER_REL, 0, ACTIONS_MODAL_CENTER_ANCHOR)
                .topRel(ACTIONS_MODAL_CENTER_REL, 0, ACTIONS_MODAL_CENTER_ANCHOR)
                .widthRel(width)
                .heightRel(height);
            addModalFrame(modal, backgroundColor, accentColor, headerColor);
            return modal;
        }

        private void addModalFrame(ParentWidget<?> modal, int backgroundColor, int accentColor, int headerColor) {
            PassiveLayer backgroundLayer = new PassiveLayer().pos(0, 0)
                .widthRel(1f)
                .heightRel(1f)
                .background(createModalBackgroundDrawable(backgroundColor, headerColor));
            modal.child(backgroundLayer);
            modal.child(WidgetOutline.create(backgroundLayer, 3, accentColor));
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

        private ButtonWidget<?> createDangerFooterButton(String label, Runnable action) {
            return createTextButton(label, true, action, true);
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
                    fr.drawStringWithShadow(
                        label,
                        x + (w - textW) / 2,
                        y + (h - fr.FONT_HEIGHT) / 2 + TEXT_BASELINE_OFFSET,
                        color);
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
                (context, x, y, width, height) -> callbacks.drawAssetIcon(
                    kind,
                    x + (width - ROW_ICON_DRAW_SIZE) / 2,
                    y + (height - ROW_ICON_DRAW_SIZE) / 2,
                    ROW_ICON_DRAW_SIZE,
                    alpha));
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
                if (CelestialClient.cancelConstruction(asset.assetId)) {
                    callbacks.showActionStatus("Construction canceled");
                } else {
                    callbacks.showActionStatus("Construction cancel failed");
                }
                return;
            }
            if (callbacks.hasStoredConstructionResources(asset)) {
                callbacks.openPendingConstructionCancellation(asset);
                return;
            }
            if (CelestialClient.cancelConstruction(asset.assetId)) {
                callbacks.showActionStatus("Construction canceled");
            } else {
                callbacks.showActionStatus("Construction cancel failed");
            }
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
