package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;

import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.client.CelestialClient;
import com.gtnewhorizons.galaxia.client.EnumTextures;
import com.gtnewhorizons.galaxia.client.gui.GalaxiaGuiStyle;
import com.gtnewhorizons.galaxia.client.gui.station.StationManagementScreen;
import com.gtnewhorizons.galaxia.client.gui.ui.GalaxiaUi;
import com.gtnewhorizons.galaxia.client.gui.ui.GalaxiaWindow;
import com.gtnewhorizons.galaxia.client.gui.ui.WindowLayer;
import com.gtnewhorizons.galaxia.client.gui.ui.WindowSpec;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeClientState;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeFacts.DiscoveryState;
import com.gtnewhorizons.galaxia.registry.celestial.station.gui.StationGuiFactory;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.satellite.Satellite;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteKind;

record ButtonRect(int left, int top, int right, int bottom) {

    boolean contains(int x, int y) {
        return x >= left && x <= right && y >= top && y <= bottom;
    }
}

record ModalBounds(int left, int top, int right, int bottom) {}

record PendingAssetCreation(CelestialObjectKey celestialObjectKey, String displayName, CelestialAsset.Kind kind,
    CelestialAsset.Location location, Map<ItemStack, Long> requiredResources) {}

record PendingAssetRename(CelestialAsset asset) {}

record PendingAssetDestruction(CelestialAsset asset, boolean armed) {}

record PendingConstructionCancellation(CelestialAsset asset) {}

record PendingResourceTransfer(CelestialAsset asset, List<CelestialAsset> targets) {}

record SatelliteAssetRow(SatelliteKind kind, int count) {}

record PendingSatelliteDeletion(SatelliteKind kind, int amount) {}

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

    static List<CelestialAsset> deployedAssetRows(List<CelestialAsset> assets) {
        List<CelestialAsset> matching = new ArrayList<>();
        for (CelestialAsset asset : assets) {
            if (asset.kind == CelestialAsset.Kind.SATELLITE) continue;
            if (asset.status() == CelestialAsset.Status.OPERATIONAL) matching.add(asset);
        }
        return matching;
    }

    static List<SatelliteAssetRow> satelliteAssetRows(List<CelestialAsset> assets) {
        EnumMap<SatelliteKind, Integer> counts = new EnumMap<>(SatelliteKind.class);
        for (CelestialAsset asset : assets) {
            if (!(asset instanceof Satellite satellite)) continue;
            counts.merge(satellite.satelliteKind(), 1, Integer::sum);
        }
        List<SatelliteAssetRow> rows = new ArrayList<>();
        for (Map.Entry<SatelliteKind, Integer> entry : counts.entrySet()) {
            rows.add(new SatelliteAssetRow(entry.getKey(), entry.getValue()));
        }
        return rows;
    }

    static String formatAssetDisplayName(CelestialAsset asset) {
        return switch (asset.status()) {
            case CONSTRUCTION_SITE -> StatCollector
                .translateToLocalFormatted("galaxia.gui.orbital.asset.name.construction", asset.displayName());
            case DECONSTRUCTION -> StatCollector
                .translateToLocalFormatted("galaxia.gui.orbital.asset.name.deconstruction", asset.displayName());
            default -> asset.displayName();
        };
    }

    static String buildConstructionInventorySummary(CelestialAsset asset) {
        if (!(asset instanceof AutomatedFacility facility))
            return StatCollector.translateToLocal("galaxia.gui.orbital.asset.inventory_empty");
        if (asset.status() == CelestialAsset.Status.DECONSTRUCTION)
            return buildStoredInventorySummary(facility.getConstructionInventory());
        if (asset.requiredResources()
            .isEmpty()) return StatCollector.translateToLocal("galaxia.gui.orbital.asset.inventory_empty");
        StringBuilder summary = new StringBuilder();
        for (Map.Entry<ItemStack, Long> required : asset.requiredResources()
            .entrySet()) {
            long storedAmount = facility.getConstructionInventory()
                .getOrDefault(required.getKey(), 0L);
            if (summary.length() > 0) summary.append(", ");
            summary.append(storedAmount)
                .append('/')
                .append(required.getValue())
                .append(' ')
                .append(
                    required.getKey()
                        .getDisplayName());
        }
        return summary.toString();
    }

    private static String buildStoredInventorySummary(Map<ItemStack, Long> storedResources) {
        if (storedResources.isEmpty())
            return StatCollector.translateToLocal("galaxia.gui.orbital.asset.inventory_empty");
        StringBuilder summary = new StringBuilder();
        for (Map.Entry<ItemStack, Long> stored : storedResources.entrySet()) {
            if (summary.length() > 0) summary.append(", ");
            summary.append(stored.getValue())
                .append(' ')
                .append(
                    stored.getKey()
                        .getDisplayName());
        }
        return summary.toString();
    }

    public static final class OrbitalAssetActionController {

        interface Callbacks {

            void showActionStatus(String message);

            void beginRenameInput(String currentText);

            void endRenameInput();

            String getRenameInput();

            void createResourceTransfer(CelestialObject sourceBody, CelestialAsset sourceAsset, CelestialAsset target);
        }

        private final Callbacks callbacks;
        private final StarmapViewContext view;

        OrbitalAssetActionController(Callbacks callbacks, StarmapViewContext view) {
            this.callbacks = callbacks;
            this.view = view;
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

        void triggerAssetCreation(OrbitalAssetUiState state, CelestialObject body, CelestialAsset.Kind kind,
            boolean openActionsFirst) {
            if (body == null) return;
            if (openActionsFirst) openAssetActions(state, body);
            if (!canCreateAssetOnBody(body, kind)) {
                callbacks.showActionStatus(
                    StatCollector.translateToLocalFormatted(
                        "galaxia.gui.orbital.asset.create.unsupported_body",
                        kind.getDisplayName()));
                return;
            }
            CelestialAsset.Location location = getDefaultAssetLocation(kind);
            String displayName = buildDefaultAssetDisplayName(body, kind);
            if (kind == CelestialAsset.Kind.STATION) {
                callbacks.showActionStatus(
                    StatCollector.translateToLocal("galaxia.gui.orbital.asset.status.station_needs_controller"));
                return;
            }
            if (view.creativeBuildMode()) {
                CelestialAsset asset = CelestialAsset.create(body.key(), kind, true);
                asset.setDisplayName(displayName);
                if (CelestialClient.registerAsset(body.key(), asset)) {
                    callbacks.showActionStatus(
                        StatCollector.translateToLocalFormatted(
                            "galaxia.gui.orbital.asset.status.created",
                            kind.getDisplayName()));
                } else {
                    callbacks.showActionStatus(
                        StatCollector.translateToLocalFormatted(
                            "galaxia.gui.orbital.asset.status.create_failed",
                            kind.getDisplayName()));
                }
                return;
            }
            state.pendingAssetCreation = new PendingAssetCreation(
                body.key(),
                displayName,
                kind,
                location,
                CelestialAsset.defaultRequirements(kind));
        }

        /** Whether the body takes this kind of asset, the same check the create buttons show */
        boolean canCreateAssetOnBody(CelestialObject body, CelestialAsset.Kind kind) {
            if (requiresDiscoveredTarget(kind)
                && CelestialKnowledgeClientState.effectiveDiscoveryState(body.key()) != DiscoveryState.DISCOVERED)
                return false;
            return switch (kind) {
                case TRANSPORT_SITE -> false;
                case AUTOMATED_STATION -> body.properties()
                    .canCreateStation();
                case AUTOMATED_OUTPOST -> body.properties()
                    .canCreateOutpost();
                default -> true;
            };
        }

        private boolean requiresDiscoveredTarget(CelestialAsset.Kind kind) {
            return kind == CelestialAsset.Kind.AUTOMATED_OUTPOST || kind == CelestialAsset.Kind.SATELLITE;
        }

        void confirmPendingAssetCreation(OrbitalAssetUiState state) {
            if (state.pendingAssetCreation == null) return;
            if (view.creativeBuildMode()) {
                CelestialAsset asset = CelestialAsset
                    .create(state.pendingAssetCreation.celestialObjectKey(), state.pendingAssetCreation.kind(), true);
                asset.setDisplayName(state.pendingAssetCreation.displayName());
                if (!CelestialClient.registerAsset(state.pendingAssetCreation.celestialObjectKey(), asset)) {
                    callbacks.showActionStatus(
                        StatCollector.translateToLocalFormatted(
                            "galaxia.gui.orbital.asset.status.create_failed",
                            state.pendingAssetCreation.kind()
                                .getDisplayName()));
                    return;
                }

                callbacks.showActionStatus(
                    StatCollector.translateToLocalFormatted(
                        "galaxia.gui.orbital.asset.status.created",
                        state.pendingAssetCreation.kind()
                            .getDisplayName()));
            } else {
                CelestialAsset asset = CelestialAsset
                    .create(state.pendingAssetCreation.celestialObjectKey(), state.pendingAssetCreation.kind(), false);
                asset.setDisplayName(state.pendingAssetCreation.displayName());
                if (!CelestialClient.registerAsset(state.pendingAssetCreation.celestialObjectKey(), asset)) {
                    callbacks.showActionStatus(
                        StatCollector.translateToLocalFormatted(
                            "galaxia.gui.orbital.asset.status.plan_failed",
                            state.pendingAssetCreation.kind()
                                .getDisplayName()));
                    return;
                }
                callbacks.showActionStatus(
                    StatCollector.translateToLocalFormatted(
                        "galaxia.gui.orbital.asset.status.planned",
                        state.pendingAssetCreation.kind()
                            .getDisplayName()));
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
                callbacks
                    .showActionStatus(StatCollector.translateToLocal("galaxia.gui.orbital.asset.status.destroyed"));
                state.pendingAssetDestruction = null;
                return;
            }
            callbacks
                .showActionStatus(StatCollector.translateToLocal("galaxia.gui.orbital.asset.status.destroy_failed"));
        }

        void openStationManagement(OrbitalAssetUiState state, CelestialAsset asset) {
            if (asset == null || !asset.isManageable()) return;
            // Built stations are run from their terminal, the starmap opens the same screen
            if (asset.kind == CelestialAsset.Kind.STATION) {
                StationGuiFactory.INSTANCE.openClient(asset.assetId);
                return;
            }
            StationManagementScreen.open(asset.assetId, view.creativeBuildMode());
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
                callbacks.showActionStatus(
                    StatCollector.translateToLocal("galaxia.gui.orbital.asset.status.deconstructing"));
                state.pendingConstructionCancellation = null;
                return;
            }
            callbacks.showActionStatus(
                StatCollector.translateToLocal("galaxia.gui.orbital.asset.status.deconstruct_failed"));
        }

        void handleConstructionAction(OrbitalAssetUiState state, CelestialAsset asset) {
            if (!(asset instanceof AutomatedFacility facility)) return;
            if (asset.status() == CelestialAsset.Status.DECONSTRUCTION) {
                openPendingResourceTransfer(state, state.assetActionsBody, asset);
                return;
            }
            if (view.creativeBuildMode()) {
                cancelConstruction(asset);
                return;
            }
            if (facility.hasStoredConstructionResources()) {
                openPendingConstructionCancellation(state, facility);
                return;
            }
            cancelConstruction(asset);
        }

        private void cancelConstruction(CelestialAsset asset) {
            if (CelestialClient.cancelConstruction(asset.assetId)) {
                callbacks.showActionStatus(StatCollector.translateToLocal("galaxia.gui.orbital.asset.status.canceled"));
            } else {
                callbacks
                    .showActionStatus(StatCollector.translateToLocal("galaxia.gui.orbital.asset.status.cancel_failed"));
            }
        }

        void openPendingResourceTransfer(OrbitalAssetUiState state, CelestialObject root, CelestialAsset asset) {
            if (asset == null) return;
            state.pendingResourceTransfer = new PendingResourceTransfer(
                asset,
                CelestialClient.getTransferTargetsInSystem(root, state.assetActionsBody));
        }

        void dismissPendingResourceTransfer(OrbitalAssetUiState state) {
            state.pendingResourceTransfer = null;
        }

        void sendPendingResourceTransfer(OrbitalAssetUiState state, CelestialAsset target) {
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
                callbacks
                    .showActionStatus(StatCollector.translateToLocal("galaxia.gui.orbital.asset.status.name_empty"));
                return;
            }
            if (renamed.equals(
                state.pendingAssetRename.asset()
                    .displayName())) {
                closePendingAssetRename(state);
                return;
            }
            if (CelestialClient.renameAsset(state.pendingAssetRename.asset().assetId, renamed)) {
                callbacks.showActionStatus(StatCollector.translateToLocal("galaxia.gui.orbital.asset.status.renamed"));
                closePendingAssetRename(state);
                return;
            }
            callbacks
                .showActionStatus(StatCollector.translateToLocal("galaxia.gui.orbital.asset.status.rename_failed"));
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
            return CelestialAsset.defaultName(body.key(), kind);
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
        PendingSatelliteDeletion pendingSatelliteDeletion;

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
            pendingSatelliteDeletion = null;
        }
    }

    /**
     * Keeps the asset manager window of a body open while the map asks for it, and the dialog of whatever decision is
     * pending over it
     */
    public static final class StarmapAssetActionsWidget extends Widget<StarmapAssetActionsWidget> {

        interface Callbacks {

            boolean canCreateAutomatedStation(CelestialObject body);

            boolean canCreateAutomatedFacility(CelestialObject body);

            void drawAssetIcon(CelestialAsset.Kind kind, int x, int y, int size, float alpha);

            void closeAssetActions();

            void triggerAssetCreation(CelestialObject body, CelestialAsset.Kind kind, boolean openActionsFirst);

            void openPendingAssetRename(CelestialAsset asset);

            void openPendingConstructionCancellation(CelestialAsset asset);

            void openPendingResourceTransfer(CelestialAsset asset);

            void openStationManagement(CelestialAsset asset);

            void openPendingAssetDestruction(CelestialAsset asset);

            void deleteSatelliteAmount(CelestialObject body, SatelliteKind kind, int amount);

            void deleteSatellites(CelestialObject body, SatelliteKind kind);

            void confirmPendingAssetCreation();

            void dismissPendingAssetCreation();

            void closePendingAssetRename();

            void confirmPendingAssetRename();

            void dismissPendingAssetDestruction();

            void advancePendingAssetDestruction();

            void dismissPendingConstructionCancellation();

            void confirmPendingConstructionCancellation();

            void handleConstructionAction(CelestialAsset asset);

            void dismissPendingResourceTransfer();

            void sendPendingResourceTransfer(CelestialAsset target);

            void dismissPendingModalByOutsideClick();

            void showActionStatus(String message);

            /** Field the new name of an asset is typed into, shown in the rename dialog */
            TextFieldWidget renameField();
        }

        public static final String WINDOW = "asset.manage";
        public static final String DIALOG = "asset.dialog";
        private static final int SATELLITE_DELETE_AMOUNT = 10;
        private static final int ROW_W = 300;
        private static final int ICON_SIZE = 14;
        private static final int DIALOG_TEXT_W = 260;

        private final OrbitalAssetUiState state;
        private final Callbacks callbacks;
        private final StarmapViewContext view;

        private int structureVersion = 0;
        private int contentVersion = 0;
        private int lastStructureVersion = -1;
        private int lastContentVersion = -1;
        private int lastAssetListSignature = 0;
        private String lastDialog = "";
        private boolean closing;
        private AssetManagementTab currentTab = AssetManagementTab.ASSETS;

        StarmapAssetActionsWidget(OrbitalAssetUiState state, Callbacks callbacks, StarmapViewContext view) {
            this.state = state;
            this.callbacks = callbacks;
            this.view = view;
            size(0, 0);
        }

        public void markStructureDirty() {
            structureVersion++;
        }

        public void markContentDirty() {
            contentVersion++;
            state.pendingSatelliteDeletion = null;
        }

        /** The map never scrolls a list of this widget itself, the windows take the wheel */
        boolean isPointInScrollViewport(int localX, int localY) {
            return false;
        }

        @Override
        public void onUpdate() {
            super.onUpdate();
            WindowLayer layer = isValid() ? WindowLayer.of(this) : null;
            if (layer == null) return;
            if (!state.isAssetActionsOpen()) {
                if (layer.isOpen(WINDOW)) quietly(() -> layer.close(WINDOW));
                lastStructureVersion = -1;
                lastContentVersion = -1;
                lastAssetListSignature = 0;
                lastDialog = "";
                return;
            }
            int assetListSignature = computeAssetListSignature(state.assetActionsBody);
            if (assetListSignature != lastAssetListSignature) {
                lastAssetListSignature = assetListSignature;
                markContentDirty();
            }
            if (!layer.isOpen(WINDOW)) {
                layer.open(
                    WindowSpec.of(WINDOW, IKey.str(state.assetActionsBody.displayName()))
                        .centered()
                        .onClose(() -> { if (!closing) callbacks.closeAssetActions(); })
                        .body(this::fillManager));
                lastStructureVersion = structureVersion;
                lastContentVersion = contentVersion;
            } else if (structureVersion != lastStructureVersion || contentVersion != lastContentVersion) {
                GalaxiaWindow window = layer.get(WINDOW);
                if (window != null) window.rebuild();
                lastStructureVersion = structureVersion;
                lastContentVersion = contentVersion;
            }
            syncDialog(layer);
        }

        private void quietly(Runnable action) {
            closing = true;
            action.run();
            closing = false;
        }

        private int computeAssetListSignature(CelestialObject body) {
            if (body == null) return 0;

            List<CelestialAsset> assets = new ArrayList<>(CelestialClient.getState(body.key()));
            assets.sort(Comparator.comparing(asset -> asset.assetId.toString()));

            int result = 1;
            for (CelestialAsset asset : assets) {
                result = 31 * result + asset.assetId.hashCode();
                result = 31 * result + asset.kind.hashCode();
                result = 31 * result + asset.status()
                    .hashCode();
                result = 31 * result + asset.displayName()
                    .hashCode();
            }
            return result;
        }

        // region Manager window

        private void fillManager(GalaxiaUi.Column column) {
            CelestialObject body = state.assetActionsBody;
            if (body == null) return;
            column.row(
                createButton(
                    CelestialAsset.Kind.AUTOMATED_STATION,
                    "galaxia.asset_manager.create.automated_station",
                    () -> callbacks.canCreateAutomatedStation(body),
                    () -> callbacks.triggerAssetCreation(body, CelestialAsset.Kind.AUTOMATED_STATION, false)),
                createButton(
                    CelestialAsset.Kind.AUTOMATED_OUTPOST,
                    "galaxia.asset_manager.create.outpost",
                    () -> callbacks.canCreateAutomatedFacility(body),
                    () -> callbacks.triggerAssetCreation(body, CelestialAsset.Kind.AUTOMATED_OUTPOST, false)));
            if (!view.gt5AutomationAvailable())
                column.dim(StatCollector.translateToLocal("galaxia.asset_manager.gt5_required"));
            column.row(
                tabButton("galaxia.asset_manager.tab.assets", AssetManagementTab.ASSETS),
                tabButton("galaxia.asset_manager.tab.satellites", AssetManagementTab.SATELLITES));
            List<CelestialAsset> assetState = CelestialClient.getState(body.key());
            if (currentTab == AssetManagementTab.SATELLITES) fillSatellites(column, assetState);
            else fillAssets(column, assetState);
        }

        private ButtonWidget<?> createButton(CelestialAsset.Kind kind, String labelKey, BooleanSupplier enabled,
            Runnable action) {
            return GalaxiaUi.button(StatCollector.translateToLocal(labelKey), enabled, action)
                .name("asset.create." + kind.name());
        }

        private ButtonWidget<?> tabButton(String labelKey, AssetManagementTab tab) {
            String label = StatCollector.translateToLocal(labelKey);
            return GalaxiaUi.toggle(() -> label, GalaxiaUi.width(label) + 10, () -> currentTab == tab, () -> {
                if (currentTab == tab) return;
                currentTab = tab;
                markStructureDirty();
            })
                .name("asset.tab." + tab);
        }

        private void fillAssets(GalaxiaUi.Column column, List<CelestialAsset> assetState) {
            List<CelestialAsset> construction = getConstructionAssets(assetState);
            if (!construction.isEmpty()) {
                column.section(StatCollector.translateToLocal("galaxia.asset_manager.construction"));
                for (CelestialAsset asset : construction) column.add(constructionRow(asset));
            }
            List<CelestialAsset> deployed = deployedAssetRows(assetState);
            if (deployed.isEmpty()) {
                column.dim(StatCollector.translateToLocal("galaxia.asset_manager.none"));
                return;
            }
            column.section(StatCollector.translateToLocal("galaxia.asset_manager.deployed"));
            for (CelestialAsset asset : deployed) column.add(assetRow(asset));
        }

        private Flow constructionRow(CelestialAsset asset) {
            boolean deconstruction = asset.status() == CelestialAsset.Status.DECONSTRUCTION;
            String detail = StatCollector.translateToLocalFormatted(
                deconstruction ? "galaxia.asset_manager.stored" : "galaxia.asset_manager.inventory",
                buildConstructionInventorySummary(asset));
            ButtonWidget<?> action = GalaxiaUi
                .button(
                    StatCollector.translateToLocal(
                        deconstruction ? "galaxia.asset_manager.send" : "galaxia.asset_manager.cancel_build"),
                    () -> handleConstructionAction(asset))
                .name("asset.constructionAction." + asset.assetId);
            return row(
                asset,
                detail,
                buttonWidth(deconstruction ? "galaxia.asset_manager.send" : "galaxia.asset_manager.cancel_build"),
                action);
        }

        private Flow assetRow(CelestialAsset asset) {
            String detail = StatCollector.translateToLocalFormatted(
                "galaxia.gui.orbital.asset.kind_location",
                asset.kind.getDisplayName(),
                asset.location.getDisplayName());
            List<ButtonWidget<?>> actions = new ArrayList<>();
            int buttonsWidth = 0;
            if (asset.isManageable()) {
                buttonsWidth += buttonWidth("galaxia.asset_manager.manage");
                actions.add(
                    GalaxiaUi
                        .button(
                            StatCollector.translateToLocal("galaxia.asset_manager.manage"),
                            () -> callbacks.openStationManagement(asset))
                        .name("asset.manage." + asset.assetId));
            }
            boolean canDestroy = asset.kind != CelestialAsset.Kind.STATION || view.creativeBuildMode();
            if (canDestroy) {
                buttonsWidth += buttonWidth("galaxia.asset_manager.destroy");
                actions.add(
                    GalaxiaUi
                        .button(
                            StatCollector.translateToLocal("galaxia.asset_manager.destroy"),
                            () -> callbacks.openPendingAssetDestruction(asset))
                        .name("asset.destroy." + asset.assetId));
            }
            return row(asset, detail, buttonsWidth, actions.toArray(new ButtonWidget<?>[0]));
        }

        /** Icon, the name to click for renaming, a detail line and the buttons on the right */
        private Flow row(CelestialAsset asset, String detail, int buttonsWidth, ButtonWidget<?>... actions) {
            int textWidth = ROW_W - ICON_SIZE - GalaxiaUi.PAD * 3 - buttonsWidth;
            String name = formatAssetDisplayName(asset);
            Flow text = Flow.column()
                .coverChildren()
                .crossAxisAlignment(com.cleanroommc.modularui.utils.Alignment.CrossAxis.START)
                .child(
                    GalaxiaUi.flatButton(textWidth, 10, () -> callbacks.openPendingAssetRename(asset))
                        .overlay(
                            IKey.str(GalaxiaUi.clip(name, textWidth))
                                .color(GalaxiaGuiStyle.TEXT)
                                .alignment(com.cleanroommc.modularui.utils.Alignment.CenterLeft))
                        .hoverOverlay(
                            IKey.str(GalaxiaUi.clip(name, textWidth))
                                .color(GalaxiaGuiStyle.ACCENT)
                                .alignment(com.cleanroommc.modularui.utils.Alignment.CenterLeft))
                        .tooltip(t -> t.addLine(StatCollector.translateToLocal("galaxia.asset_manager.rename_hint"))))
                .child(GalaxiaUi.clipped(detail, textWidth, GalaxiaGuiStyle.TEXT_DIM));
            Flow line = GalaxiaUi.row()
                .width(ROW_W)
                .padding(GalaxiaUi.PAD)
                .background(EnumTextures.GUI_WELL.getImage())
                .child(
                    assetIcon(asset.kind, 1f).asWidget()
                        .size(ICON_SIZE))
                .child(text.expanded());
            for (ButtonWidget<?> action : actions) line.child(action);
            return line;
        }

        private static int buttonWidth(String labelKey) {
            return GalaxiaUi.width(StatCollector.translateToLocal(labelKey)) + 10 + GalaxiaUi.GAP;
        }

        private void fillSatellites(GalaxiaUi.Column column, List<CelestialAsset> assetState) {
            List<SatelliteAssetRow> satellites = satelliteAssetRows(assetState);
            if (satellites.isEmpty()) {
                column.dim(StatCollector.translateToLocal("galaxia.asset_manager.satellites.empty"));
                return;
            }
            for (SatelliteAssetRow satellite : satellites) {
                column.add(
                    GalaxiaUi.row()
                        .width(ROW_W)
                        .padding(GalaxiaUi.PAD)
                        .background(EnumTextures.GUI_WELL.getImage())
                        .child(
                            assetIcon(CelestialAsset.Kind.SATELLITE, 1f).asWidget()
                                .size(ICON_SIZE))
                        .child(
                            GalaxiaUi
                                .text(
                                    StatCollector.translateToLocalFormatted(
                                        "galaxia.asset_manager.satellites.row",
                                        satelliteKindLabel(satellite.kind()),
                                        satellite.count()))
                                .expanded())
                        .child(
                            satelliteDeleteButton(
                                satellite.kind(),
                                1,
                                StatCollector.translateToLocal("galaxia.asset_manager.satellites.delete_one")))
                        .child(
                            satelliteDeleteButton(
                                satellite.kind(),
                                SATELLITE_DELETE_AMOUNT,
                                StatCollector.translateToLocalFormatted(
                                    "galaxia.asset_manager.satellites.delete_amount",
                                    SATELLITE_DELETE_AMOUNT)))
                        .child(
                            satelliteDeleteButton(
                                satellite.kind(),
                                0,
                                StatCollector.translateToLocal("galaxia.asset_manager.satellites.delete_all"))));
            }
        }

        private ButtonWidget<?> satelliteDeleteButton(SatelliteKind kind, int amount, String label) {
            boolean armed = state.pendingSatelliteDeletion != null && state.pendingSatelliteDeletion.kind() == kind
                && state.pendingSatelliteDeletion.amount() == amount;
            String visible = armed ? StatCollector.translateToLocal("galaxia.asset_manager.satellites.confirm_delete")
                : label;
            return GalaxiaUi.button(
                IKey.str(visible)
                    .color(armed ? GalaxiaGuiStyle.BAD : GalaxiaGuiStyle.TEXT),
                GalaxiaUi.width(visible) + 10,
                () -> {
                    if (state.assetActionsBody == null) return;
                    if (!armed) {
                        state.pendingSatelliteDeletion = new PendingSatelliteDeletion(kind, amount);
                        markStructureDirty();
                        return;
                    }
                    state.pendingSatelliteDeletion = null;
                    if (amount == 0) callbacks.deleteSatellites(state.assetActionsBody, kind);
                    else callbacks.deleteSatelliteAmount(state.assetActionsBody, kind, amount);
                })
                .name("asset.satellite.delete." + kind + "." + amount + (armed ? ".confirm" : ""));
        }

        // endregion

        // region Dialogs

        /** Opens the dialog of the pending decision, or closes the one that is no longer pending */
        private void syncDialog(WindowLayer layer) {
            String dialog = pendingDialog();
            if (dialog.equals(lastDialog) && (dialog.isEmpty() || layer.isOpen(DIALOG))) return;
            lastDialog = dialog;
            if (layer.isOpen(DIALOG)) quietly(() -> layer.close(DIALOG));
            if (dialog.isEmpty()) return;
            layer.open(dialogSpec());
        }

        /** Which dialog is pending, with what changes its content, so a change opens it afresh */
        private String pendingDialog() {
            if (state.pendingAssetCreation != null) return "create";
            if (state.pendingAssetDestruction != null) {
                return "destroy" + (state.pendingAssetDestruction.armed() ? ":armed" : "");
            }
            if (state.pendingConstructionCancellation != null) return "cancel";
            if (state.pendingResourceTransfer != null) return "send";
            if (state.pendingAssetRename != null) return "rename";
            return "";
        }

        private WindowSpec dialogSpec() {
            if (state.pendingAssetCreation != null) {
                PendingAssetCreation creation = state.pendingAssetCreation;
                return dialog(
                    StatCollector.translateToLocalFormatted(
                        "galaxia.asset_manager.dialog.create",
                        creation.kind()
                            .getDisplayName()),
                    callbacks::dismissPendingAssetCreation).body(column -> {
                        column.text(creation.displayName());
                        column.section(StatCollector.translateToLocal("galaxia.asset_manager.dialog.resources"));
                        for (Map.Entry<ItemStack, Long> requirement : creation.requiredResources()
                            .entrySet()) {
                            column.add(
                                GalaxiaUi.row()
                                    .child(GalaxiaUi.item(requirement.getKey()))
                                    .child(
                                        GalaxiaUi.text(
                                            StatCollector.translateToLocalFormatted(
                                                "galaxia.asset_manager.dialog.resource",
                                                requirement.getValue(),
                                                requirement.getKey()
                                                    .getDisplayName()))));
                        }
                        column.add(
                            footer(
                                callbacks::dismissPendingAssetCreation,
                                StatCollector.translateToLocal("galaxia.gui.common.confirm"),
                                callbacks::confirmPendingAssetCreation));
                    });
            }
            if (state.pendingAssetDestruction != null) {
                PendingAssetDestruction destruction = state.pendingAssetDestruction;
                return dialog(
                    StatCollector.translateToLocal("galaxia.asset_manager.dialog.destroy"),
                    callbacks::dismissPendingAssetDestruction).body(column -> {
                        column.add(
                            GalaxiaUi.colored(
                                () -> StatCollector.translateToLocal("galaxia.asset_manager.dialog.irreversible"),
                                GalaxiaGuiStyle.BAD));
                        column.text(formatAssetDisplayName(destruction.asset()));
                        column.dim(
                            StatCollector.translateToLocal(
                                destruction.armed() ? "galaxia.asset_manager.dialog.destroy_again"
                                    : "galaxia.asset_manager.dialog.destroy_arm"));
                        ButtonWidget<?> cancel = GalaxiaUi
                            .button(
                                StatCollector.translateToLocal("galaxia.gui.common.back"),
                                callbacks::dismissPendingAssetDestruction)
                            .name("asset.cancel");
                        ButtonWidget<?> destroy = GalaxiaUi
                            .button(
                                IKey.str(StatCollector.translateToLocal("galaxia.asset_manager.destroy"))
                                    .color(GalaxiaGuiStyle.BAD),
                                GalaxiaUi.width(StatCollector.translateToLocal("galaxia.asset_manager.destroy")) + 10,
                                callbacks::advancePendingAssetDestruction)
                            .name("asset.confirm");
                        // The buttons swap once armed, so a double click cannot destroy by accident
                        column.add(
                            Flow.row()
                                .widthRel(1f)
                                .height(GalaxiaUi.BUTTON_H)
                                .marginTop(GalaxiaUi.GAP)
                                .mainAxisAlignment(com.cleanroommc.modularui.utils.Alignment.MainAxis.SPACE_BETWEEN)
                                .child(destruction.armed() ? destroy : cancel)
                                .child(destruction.armed() ? cancel : destroy));
                    });
            }
            if (state.pendingConstructionCancellation != null) {
                CelestialAsset asset = state.pendingConstructionCancellation.asset();
                return dialog(
                    StatCollector.translateToLocal("galaxia.asset_manager.dialog.cancel_build"),
                    callbacks::dismissPendingConstructionCancellation).body(
                        column -> column.text(formatAssetDisplayName(asset))
                            .add(
                                GalaxiaUi.colored(
                                    () -> StatCollector
                                        .translateToLocal("galaxia.asset_manager.dialog.cancel_build_hint"),
                                    GalaxiaGuiStyle.WARN))
                            .add(
                                footer(
                                    callbacks::dismissPendingConstructionCancellation,
                                    StatCollector.translateToLocal("galaxia.gui.common.confirm"),
                                    callbacks::confirmPendingConstructionCancellation)));
            }
            if (state.pendingResourceTransfer != null) {
                PendingResourceTransfer transfer = state.pendingResourceTransfer;
                return dialog(
                    StatCollector.translateToLocal("galaxia.asset_manager.dialog.send"),
                    callbacks::dismissPendingResourceTransfer).maxHeight(200)
                        .body(column -> {
                            column.text(formatAssetDisplayName(transfer.asset()));
                            column.dim(StatCollector.translateToLocal("galaxia.asset_manager.dialog.send_hint"));
                            if (transfer.targets()
                                .isEmpty()) {
                                column.dim(StatCollector.translateToLocal("galaxia.asset_manager.dialog.send_none"));
                                return;
                            }
                            for (CelestialAsset target : transfer.targets()) {
                                String bodyName = GalaxiaCelestialAPI.get(target.celestialObjectKey)
                                    .map(CelestialObject::displayName)
                                    .orElse("?");
                                column.add(
                                    GalaxiaUi.row()
                                        .width(DIALOG_TEXT_W)
                                        .padding(GalaxiaUi.PAD)
                                        .background(EnumTextures.GUI_WELL.getImage())
                                        .child(
                                            assetIcon(CelestialAsset.Kind.STATION, 1f).asWidget()
                                                .size(ICON_SIZE))
                                        .child(
                                            GalaxiaUi
                                                .clipped(
                                                    StatCollector.translateToLocalFormatted(
                                                        "galaxia.asset_manager.dialog.send_target",
                                                        target.displayName(),
                                                        bodyName),
                                                    DIALOG_TEXT_W - ICON_SIZE - 60,
                                                    GalaxiaGuiStyle.TEXT)
                                                .expanded())
                                        .child(
                                            GalaxiaUi.button(
                                                StatCollector.translateToLocal("galaxia.asset_manager.send"),
                                                () -> callbacks.sendPendingResourceTransfer(target))));
                            }
                        });
            }
            CelestialAsset renamed = state.pendingAssetRename.asset();
            return dialog(
                StatCollector.translateToLocal("galaxia.asset_manager.dialog.rename"),
                callbacks::closePendingAssetRename).body(column -> {
                    column.text(formatAssetDisplayName(renamed));
                    TextFieldWidget field = callbacks.renameField();
                    if (field != null) column.add(
                        field.size(DIALOG_TEXT_W, GalaxiaUi.BUTTON_H)
                            .background(EnumTextures.GUI_WELL.getImage()));
                    column.add(
                        footer(
                            callbacks::closePendingAssetRename,
                            StatCollector.translateToLocal("galaxia.gui.common.confirm"),
                            callbacks::confirmPendingAssetRename));
                });
        }

        private WindowSpec dialog(String title, Runnable dismiss) {
            return WindowSpec.of(DIALOG, IKey.str(title))
                .modal()
                .owner(WINDOW)
                .centered()
                .onClose(() -> { if (!closing) dismiss.run(); });
        }

        private static Flow footer(Runnable back, String confirm, Runnable onConfirm) {
            Flow footer = GalaxiaUi.footer(
                StatCollector.translateToLocal("galaxia.gui.common.back"),
                back,
                confirm,
                () -> true,
                onConfirm);
            ((Widget<?>) footer.getChildren()
                .get(0)).name("asset.cancel");
            ((Widget<?>) footer.getChildren()
                .get(1)).name("asset.confirm");
            return footer;
        }

        // endregion

        private IDrawable assetIcon(CelestialAsset.Kind kind, float alpha) {
            return (context, x, y, width, height, ignoredTheme) -> callbacks
                .drawAssetIcon(kind, x + (width - ICON_SIZE) / 2, y + (height - ICON_SIZE) / 2, ICON_SIZE, alpha);
        }

        private String satelliteKindLabel(SatelliteKind kind) {
            return StatCollector.translateToLocal(
                "galaxia.satellite.kind." + kind.name()
                    .toLowerCase(Locale.ROOT));
        }

        private List<CelestialAsset> getConstructionAssets(List<CelestialAsset> assets) {
            List<CelestialAsset> matching = new ArrayList<>();
            for (CelestialAsset asset : assets) {
                if (!(asset instanceof AutomatedFacility)) continue;
                if (asset.status() == CelestialAsset.Status.CONSTRUCTION_SITE
                    || asset.status() == CelestialAsset.Status.DECONSTRUCTION) matching.add(asset);
            }
            return matching;
        }

        private void handleConstructionAction(CelestialAsset asset) {
            callbacks.handleConstructionAction(asset);
        }

        private enum AssetManagementTab {
            ASSETS,
            SATELLITES
        }
    }
}
