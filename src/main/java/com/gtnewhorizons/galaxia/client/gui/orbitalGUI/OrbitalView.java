package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import static com.gtnewhorizons.galaxia.api.GalaxiaAPI.isGregTech5UnofficialNewHorizonsLoaded;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import com.cleanroommc.modularui.api.UpOrDown;
import com.cleanroommc.modularui.api.widget.IGuiAction;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.utils.GlStateManager;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.client.CelestialClient;
import com.gtnewhorizons.galaxia.client.EnumColors;
import com.gtnewhorizons.galaxia.client.EnumTextures;
import com.gtnewhorizons.galaxia.client.gui.station.StationManagementScreen;
import com.gtnewhorizons.galaxia.compat.teams.GTTeamsCompat;
import com.gtnewhorizons.galaxia.core.network.StarmapActionSyncHandler;
import com.gtnewhorizons.galaxia.core.network.StarmapActionSyncHandler.SatelliteMutationOperation;
import com.gtnewhorizons.galaxia.core.profiling.HammerTrajectoryLoadSample;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidStarmapProjection;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryCapability;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryClientState;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryScanSnapshot;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalMechanics;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalParams;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalTransferPlanner;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.LogisticsDelivery;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteBandwidthFormatter;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteDataKey;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteKind;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteNetworkClientState;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteNetworkState;

public class OrbitalView {

    public static final class RenderTickState {

        private static float lastPartialTicks = 0.0F;

        private RenderTickState() {}

        public static float getLastPartialTicks() {
            return lastPartialTicks;
        }

        public static void setLastPartialTicks(float partialTicks) {
            lastPartialTicks = partialTicks;
        }
    }

    record OrbitalLayerTransitionState(CelestialObject pendingTarget, CelestialObject pendingAnchor,
        double pendingStartZoom, double pendingTargetZoom, Phase phase, CelestialObject activeTarget,
        CelestialObject activeAnchor, double activeStartZoom, double activeTargetZoom, float activeStartSpriteSize,
        float activeTargetSpriteSize) {

        enum Phase {
            NONE,
            SYSTEM_PRE_CUT,
            SYSTEM_POST_CUT,
            GALAXY_PRE_CUT,
            GALAXY_POST_CUT
        }

        OrbitalLayerTransitionState() {
            this(null, null, 0, 0, Phase.NONE, null, null, 0, 0, 0f, 0f);
        }

        boolean hasPending() {
            return pendingTarget != null && pendingAnchor != null;
        }

        boolean isActive() {
            return phase != Phase.NONE;
        }

        OrbitalLayerTransitionState beginPending(CelestialObject target, CelestialObject anchor, double startZoom,
            double targetZoom) {
            return new OrbitalLayerTransitionState(
                target,
                anchor,
                startZoom,
                targetZoom,
                phase,
                activeTarget,
                activeAnchor,
                activeStartZoom,
                activeTargetZoom,
                activeStartSpriteSize,
                activeTargetSpriteSize);
        }

        OrbitalLayerTransitionState clearPending() {
            return new OrbitalLayerTransitionState(
                null,
                null,
                0,
                0,
                phase,
                activeTarget,
                activeAnchor,
                activeStartZoom,
                activeTargetZoom,
                activeStartSpriteSize,
                activeTargetSpriteSize);
        }

        OrbitalLayerTransitionState beginActive(Phase nextPhase, CelestialObject target, CelestialObject anchor,
            double startZoom, double targetZoom, float startSpriteSize, float targetSpriteSize) {
            return new OrbitalLayerTransitionState(
                pendingTarget,
                pendingAnchor,
                pendingStartZoom,
                pendingTargetZoom,
                nextPhase,
                target,
                anchor,
                startZoom,
                targetZoom,
                startSpriteSize,
                targetSpriteSize);
        }

        OrbitalLayerTransitionState clearActive() {
            return new OrbitalLayerTransitionState(
                pendingTarget,
                pendingAnchor,
                pendingStartZoom,
                pendingTargetZoom,
                Phase.NONE,
                null,
                null,
                0,
                0,
                0f,
                0f);
        }

        OrbitalLayerTransitionState clear() {
            return new OrbitalLayerTransitionState(null, null, 0, 0, Phase.NONE, null, null, 0, 0, 0f, 0f);
        }
    }

    public static final class OrbitalContextMenuState {

        private CelestialObject body;
        private int x, y;

        boolean isOpen() {
            return body != null;
        }

        CelestialObject body() {
            return body;
        }

        int x() {
            return x;
        }

        int y() {
            return y;
        }

        void open(CelestialObject body, int x, int y) {
            this.body = body;
            this.x = x;
            this.y = y;
        }

        void close() {
            body = null;
        }
    }

    public static final class OrbitalViewState {

        double cameraX, cameraY, zoomLevel, targetCameraX, targetCameraY, targetZoomLevel, isometricProgress,
            targetIsometricProgress;

        OrbitalViewState(double initialZoom) {
            this.zoomLevel = initialZoom;
            this.targetZoomLevel = initialZoom;
        }

        void step(double lerpSpeed) {
            cameraX = lerp(cameraX, targetCameraX, lerpSpeed);
            cameraY = lerp(cameraY, targetCameraY, lerpSpeed);
            zoomLevel = lerp(zoomLevel, targetZoomLevel, lerpSpeed);
            isometricProgress = lerp(isometricProgress, targetIsometricProgress, lerpSpeed);
        }

        void snap(double threshold) {
            if (Math.abs(cameraX - targetCameraX) < threshold) cameraX = targetCameraX;
            if (Math.abs(cameraY - targetCameraY) < threshold) cameraY = targetCameraY;
            if (Math.abs(zoomLevel - targetZoomLevel) < threshold) zoomLevel = targetZoomLevel;
            if (Math.abs(isometricProgress - targetIsometricProgress) < threshold)
                isometricProgress = targetIsometricProgress;
        }

        void reset(boolean resetCameraToOrigin) {
            isometricProgress = 0.0;
            targetIsometricProgress = 0.0;
            if (resetCameraToOrigin) setCamera(0.0, 0.0);
        }

        void setCamera(double x, double y) {
            cameraX = x;
            cameraY = y;
            targetCameraX = x;
            targetCameraY = y;
        }

        void syncCameraToTarget() {
            cameraX = targetCameraX;
            cameraY = targetCameraY;
        }

        void syncToTargets() {
            cameraX = targetCameraX;
            cameraY = targetCameraY;
            zoomLevel = targetZoomLevel;
            isometricProgress = targetIsometricProgress;
        }

        private static double lerp(double a, double b, double t) {
            return a + (b - a) * t;
        }
    }

    public static final class OrbitalWorldStateCache {

        private final Map<CelestialObjectKey, BodyWorldState> states = new HashMap<>();
        private double cachedTime = Double.NaN;
        private int rebuildVersion = 0;

        void ensure(CelestialObject root, double globalTime) {
            if (root == null) {
                states.clear();
                cachedTime = Double.NaN;
                return;
            }
            if (!states.isEmpty() && Double.compare(cachedTime, globalTime) == 0) return;
            rebuild(root, globalTime);
        }

        double[] getWorldPosition(CelestialObject body) {
            BodyWorldState state = getState(body);
            if (state == null) return null;
            return new double[] { state.worldX, state.worldY };
        }

        double[] getWorldVelocity(CelestialObject body) {
            BodyWorldState state = getState(body);
            if (state == null) return null;
            return new double[] { state.worldVx, state.worldVy };
        }

        CelestialObject getParent(CelestialObject body) {
            BodyWorldState state = getState(body);
            return state == null ? null : state.parent;
        }

        private BodyWorldState getState(CelestialObject body) {
            return body == null || body.key() == null ? null : states.get(body.key());
        }

        private void rebuild(CelestialObject root, double globalTime) {
            rebuildVersion++;
            populate(root, null, new OrbitalMechanics.OrbitalState(0.0, 0.0, 0.0, 0.0), globalTime);
            states.entrySet()
                .removeIf(entry -> entry.getValue().rebuildStamp != rebuildVersion);
            cachedTime = globalTime;
        }

        private void populate(CelestialObject body, CelestialObject parent, OrbitalMechanics.OrbitalState worldState,
            double globalTime) {
            recordState(body, parent, worldState);
            for (CelestialObject child : CelestialClient.getChildren(body)) {
                OrbitalMechanics.OrbitalState childWorldState = OrbitalMechanics
                    .resolveChildWorldState(body, child, worldState, globalTime);
                populate(child, body, childWorldState, globalTime);
            }
        }

        void recordState(CelestialObject body, CelestialObject parent, OrbitalMechanics.OrbitalState worldState) {
            if (body.key() == null) return;
            BodyWorldState cachedState = states.get(body.key());
            if (cachedState == null) {
                cachedState = new BodyWorldState();
                states.put(body.key(), cachedState);
            }
            cachedState.set(parent, worldState.x(), worldState.y(), worldState.vx(), worldState.vy(), rebuildVersion);
        }

        static boolean usesAbsolutePosition(CelestialObject parent, CelestialObject child) {
            return OrbitalMechanics.usesAbsolutePosition(parent, child);
        }

        static OrbitalMechanics.OrbitalState resolveChildWorldState(CelestialObject parent, CelestialObject child,
            double parentWX, double parentWY, double globalTime) {
            return OrbitalMechanics.resolveChildWorldState(
                parent,
                child,
                new OrbitalMechanics.OrbitalState(parentWX, parentWY, 0.0, 0.0),
                globalTime);
        }

        static double[] calculatePosition(OrbitalParams p, double t) {
            OrbitalMechanics.OrbitalState state = OrbitalMechanics
                .calculateOrbitalState(p, OrbitalMechanics.resolveAttractorMu(null, p), t);
            return new double[] { state.x(), state.y() };
        }

        private static final class BodyWorldState {

            private CelestialObject parent;
            private double worldX;
            private double worldY;
            private double worldVx;
            private double worldVy;
            private int rebuildStamp = 0;

            void set(CelestialObject parent, double worldX, double worldY, double worldVx, double worldVy,
                int rebuildStamp) {
                this.parent = parent;
                this.worldX = worldX;
                this.worldY = worldY;
                this.worldVx = worldVx;
                this.worldVy = worldVy;
                this.rebuildStamp = rebuildStamp;
            }
        }
    }

    // Static so state persists across GUI close/reopen cycles (client-side only, never sent to server).
    private static final InterplanetaryTransferSystem.OrbitalTransferSimulatorState transferSimulatorState = new InterplanetaryTransferSystem.OrbitalTransferSimulatorState();
    private static final InterplanetaryTransferSystem.OrbitalTransferState clientSimulatedTransferState = new InterplanetaryTransferSystem.OrbitalTransferState();
    private static boolean creativeBuildModePersisted = false;

    static InterplanetaryTransferSystem.OrbitalTransferState clientSimulatedTransferState() {
        return clientSimulatedTransferState;
    }

    public static class OrbitalMapWidget extends Widget<OrbitalMapWidget> {

        @FunctionalInterface
        public interface BodySelectionListener {

            void onBodySelected(CelestialObject body);
        }

        private final CelestialObject root;
        private CelestialObject viewRoot;
        private CelestialObject initialLayer;
        private BodySelectionListener bodySelectionListener;
        private OrbitalScene.OrbitalSceneFrame sceneFrame = new OrbitalScene.OrbitalSceneFrame();
        private final OrbitalViewState viewState = new OrbitalViewState(-0.8);
        private final OrbitalWorldStateCache worldStateCache = new OrbitalWorldStateCache();
        private boolean dragging = false;
        private double lastMouseX, lastMouseY;
        private final OrbitalClock clock = new OrbitalClock(
            OrbitalTransferPlanner.OSU_PER_SECOND,
            SERVER_OSU_PER_SECOND);
        private final InterplanetaryTransferSystem.MutableTransferPoint focusedTransferPoint = new InterplanetaryTransferSystem.MutableTransferPoint();
        private final float[] isoScratchPos = new float[2];
        private CelestialObject focusedBody = null;
        private CelestialObject hoveredBody = null;
        private InterplanetaryTransferJob focusedTransfer = null;
        private boolean isFollowing = false;
        private CelestialObject pendingFocusBody = null;
        private boolean dragEnabledForCurrentPress = false;
        private CelestialObject pressedBodyCandidate = null;
        private boolean debugOverlayEnabled = false;
        private int pressMouseX, pressMouseY;
        private final OrbitalContextMenuState contextMenuState = new OrbitalContextMenuState();
        private String actionStatusMessage = "";
        private long actionStatusExpiresAt = 0L;
        private final StarmapAssetActions.OrbitalAssetSupport assetSupport = new StarmapAssetActions.OrbitalAssetSupport();
        private final InterplanetaryTransferSystem.OrbitalTransferSupport transferSupport = new InterplanetaryTransferSystem.OrbitalTransferSupport();
        private final StarmapAssetActions.OrbitalAssetActionController assetActionController;
        private final StarmapAssetActions.OrbitalAssetUiState assetUiState = new StarmapAssetActions.OrbitalAssetUiState();
        private final StarmapAssetActions.StarmapAssetActionsWidget assetActionsWidget;
        private final InterplanetaryTransferSystem.OrbitalTransferState transferState = new InterplanetaryTransferSystem.OrbitalTransferState();
        private final InterplanetaryTransferSystem.OrbitalTransferRenderer transferRenderer;
        private final InterplanetaryTransferSystem.OrbitalTransferTooltipWidget transferTooltipWidget;
        private final InterplanetaryTransferSystem.OrbitalTransferSimulatorWidget transferSimulatorWidget;
        private final OrbitalScene.OrbitalSceneRenderer sceneRenderer;
        private final OrbitalPinnedInfoContentBuilder pinnedInfoContentBuilder = new OrbitalPinnedInfoContentBuilder();
        private final OrbitalPinnedInfoContentBuilder.OrbitalPinnedInfoWidget pinnedInfoWidget;
        private final OrbitalContextMenuWidget contextMenuWidget;
        private final LogisticsSignalsWidget signalsWidget;
        private boolean signalsOpen = false;
        private final SolarSystemAssetPanelWidget assetsPanelWidget;
        private boolean assetsPanelOpen = false;
        private boolean transfersHidden = false;
        private boolean satelliteNetworkHidden = false;
        private final SatelliteNetworkOverlay satelliteNetworkOverlay = new SatelliteNetworkOverlay();
        private final OrbitalScene.OrbitalSceneFrameBuilder sceneFrameBuilder;
        private int lastRenderedLogisticsTaskRevision = Integer.MIN_VALUE;
        private int lastRenderedLogisticsClockRevision = Integer.MIN_VALUE;
        private TextFieldWidget renameField = null;
        private boolean creativeBuildMode = creativeBuildModePersisted;
        private final OrbitalPlanetTrackingController planetTrackingController = new OrbitalPlanetTrackingController();
        private boolean guiActionsRegistered = false;
        private OrbitalLayerTransitionState transitionState = new OrbitalLayerTransitionState();
        private static final double SERVER_OSU_PER_SECOND = OrbitalTransferPlanner.OSU_PER_SECOND;
        private static final double LERP_SPEED = 0.045;
        private static final double PENDING_LAYER_CENTER_LERP_SPEED = 0.08;
        private static final double LAYER_SWITCH_LERP_SPEED = 0.036;
        private static final float ISO_BASE_CUBE_SIZE = 42f;
        private static final float ISO_SPACING = 90f;
        private static final float ISO_OFFSET = 110f;
        private static final float ISO_Y_OFFSET = 20f;
        private static final double CONVERGE_THRESHOLD = 0.001;
        private static final double PENDING_LAYER_SWITCH_CAMERA_THRESHOLD = 1.5;
        private static final double LAYER_SWITCH_CONVERGE_THRESHOLD = 0.03;
        private static final int CLICK_DRAG_THRESHOLD = 6;
        private static final float MAP_ICON_BASE_SCALE = 18f;
        private static final float MAP_ICON_ZOOM_SCALE = 0.8f;
        private static final float GALAXY_MAP_STAR_SPRITE_SIZE = 0.5f;
        private static final double SYSTEM_DEPARTURE_EXTENT_MULTIPLIER = 24.0;

        public OrbitalMapWidget(CelestialObject root) {
            this.root = root;
            this.viewRoot = root;
            this.initialLayer = root;
            this.assetActionController = new StarmapAssetActions.OrbitalAssetActionController(
                assetSupport,
                new StarmapAssetActions.OrbitalAssetActionController.Callbacks() {

                    @Override
                    public boolean isCreativeBuildModeEnabled() {
                        return OrbitalMapWidget.this.isCreativeBuildModeEnabled();
                    }

                    @Override
                    public void showActionStatus(String message) {
                        OrbitalMapWidget.this.showActionStatus(message);
                    }

                    @Override
                    public void beginRenameInput(String currentText) {
                        if (renameField == null) return;
                        renameField.setText(currentText);
                        if (renameField.isValid()) getContext().focus(renameField);
                    }

                    @Override
                    public void endRenameInput() {
                        if (renameField != null && renameField.isValid() && getContext().isFocused(renameField))
                            getContext().removeFocus();
                    }

                    @Override
                    public String getRenameInput() {
                        return renameField == null ? "" : renameField.getText();
                    }

                    @Override
                    public void createResourceTransfer(CelestialObject sourceBody, CelestialAsset sourceAsset,
                        StationTransferTarget target) {
                        OrbitalMapWidget.this.createResourceTransfer(sourceBody, sourceAsset, target);
                    }
                });
            this.assetActionsWidget = new StarmapAssetActions.StarmapAssetActionsWidget(
                assetUiState,
                new StarmapAssetActions.StarmapAssetActionsWidget.Callbacks() {

                    @Override
                    public int getViewportWidth() {
                        return OrbitalMapWidget.this.getArea().width;
                    }

                    @Override
                    public int getViewportHeight() {
                        return OrbitalMapWidget.this.getArea().height;
                    }

                    @Override
                    public void closeAssetActions() {
                        assetActionController.closeAssetActions(assetUiState);
                        transferSimulatorState.resetSelection();
                        assetActionsWidget.markStructureDirty();
                    }

                    @Override
                    public boolean isCreativeBuildModeEnabled() {
                        return OrbitalMapWidget.this.isCreativeBuildModeEnabled();
                    }

                    @Override
                    public boolean isGT5AutomationAvailable() {
                        return OrbitalMapWidget.this.isGT5AutomationAvailable();
                    }

                    @Override
                    public boolean canCreateBaseStation(CelestialObject body) {
                        return OrbitalMapWidget.this.canCreateBaseStation(body);
                    }

                    @Override
                    public boolean canCreateAutomatedStation(CelestialObject body) {
                        return OrbitalMapWidget.this.canCreateAutomatedStation(body);
                    }

                    @Override
                    public boolean canCreateAutomatedFacility(CelestialObject body) {
                        return OrbitalMapWidget.this.canCreateAutomatedFacility(body);
                    }

                    @Override
                    public boolean hasStoredConstructionResources(CelestialAsset asset) {
                        return assetSupport.hasStoredConstructionResources(asset);
                    }

                    @Override
                    public boolean isManageableStationAsset(CelestialAsset asset) {
                        return assetSupport.isManageableStationAsset(asset);
                    }

                    @Override
                    public String formatAssetDisplayName(CelestialAsset asset) {
                        return assetSupport.formatAssetDisplayName(asset);
                    }

                    @Override
                    public String buildConstructionInventorySummary(CelestialAsset asset) {
                        return assetSupport.buildConstructionInventorySummary(asset);
                    }

                    @Override
                    public String formatAssetKind(CelestialAsset.Kind kind) {
                        return assetSupport.formatAssetKind(kind);
                    }

                    @Override
                    public String formatAssetLocation(CelestialAsset.Location location) {
                        return assetSupport.formatAssetLocation(location);
                    }

                    @Override
                    public void drawAssetIcon(CelestialAsset.Kind kind, int x, int y, int size, float alpha) {
                        OrbitalMapWidget.this.drawAssetIcon(kind, x, y, size, alpha);
                    }

                    @Override
                    public void createBaseStation(CelestialObject body) {
                        assetActionController.createBaseStation(body);
                        assetActionsWidget.markContentDirty();
                    }

                    @Override
                    public void triggerAssetCreation(CelestialObject body, CelestialAsset.Kind kind,
                        boolean openActionsFirst) {
                        assetActionController.triggerAssetCreation(assetUiState, body, kind, openActionsFirst);
                        assetActionsWidget.markStructureDirty();
                        assetActionsWidget.markContentDirty();
                    }

                    @Override
                    public void openPendingAssetRename(CelestialAsset asset) {
                        assetActionController.openPendingAssetRename(assetUiState, asset);
                        assetActionsWidget.markStructureDirty();
                    }

                    @Override
                    public void openPendingConstructionCancellation(CelestialAsset asset) {
                        assetActionController.openPendingConstructionCancellation(assetUiState, asset);
                        assetActionsWidget.markStructureDirty();
                    }

                    @Override
                    public void openPendingResourceTransfer(CelestialAsset asset) {
                        assetActionController.openPendingResourceTransfer(assetUiState, root, asset);
                        assetActionsWidget.markStructureDirty();
                    }

                    @Override
                    public void openStationManagement(CelestialAsset asset) {
                        assetActionController.openStationManagement(assetUiState, asset);
                        assetActionsWidget.markStructureDirty();
                    }

                    @Override
                    public void openPendingAssetDestruction(CelestialAsset asset) {
                        assetActionController.openPendingAssetDestruction(assetUiState, asset);
                        assetActionsWidget.markStructureDirty();
                    }

                    @Override
                    public void deleteSatelliteAmount(CelestialObject body, SatelliteKind kind, int amount) {
                        OrbitalMapWidget.this.deleteSatelliteAmount(body, kind, amount);
                        assetActionsWidget.markContentDirty();
                    }

                    @Override
                    public void deleteSatellites(CelestialObject body, SatelliteKind kind) {
                        OrbitalMapWidget.this.deleteSatellites(body, kind);
                        assetActionsWidget.markContentDirty();
                    }

                    @Override
                    public void confirmPendingAssetCreation() {
                        assetActionController.confirmPendingAssetCreation(assetUiState);
                        assetActionsWidget.markStructureDirty();
                        assetActionsWidget.markContentDirty();
                    }

                    @Override
                    public void dismissPendingAssetCreation() {
                        assetActionController.dismissPendingAssetCreation(assetUiState);
                        assetActionsWidget.markStructureDirty();
                    }

                    @Override
                    public void closePendingAssetRename() {
                        assetActionController.closePendingAssetRename(assetUiState);
                        assetActionsWidget.markStructureDirty();
                    }

                    @Override
                    public void confirmPendingAssetRename() {
                        assetActionController.confirmPendingAssetRename(assetUiState);
                        assetActionsWidget.markStructureDirty();
                        assetActionsWidget.markContentDirty();
                    }

                    @Override
                    public void dismissPendingAssetDestruction() {
                        assetActionController.dismissPendingAssetDestruction(assetUiState);
                        assetActionsWidget.markStructureDirty();
                    }

                    @Override
                    public void advancePendingAssetDestruction() {
                        assetActionController.advancePendingAssetDestruction(assetUiState);
                        assetActionsWidget.markStructureDirty();
                        assetActionsWidget.markContentDirty();
                    }

                    @Override
                    public void dismissPendingConstructionCancellation() {
                        assetActionController.dismissPendingConstructionCancellation(assetUiState);
                        assetActionsWidget.markStructureDirty();
                    }

                    @Override
                    public void confirmPendingConstructionCancellation() {
                        assetActionController.confirmPendingConstructionCancellation(assetUiState);
                        assetActionsWidget.markStructureDirty();
                        assetActionsWidget.markContentDirty();
                    }

                    @Override
                    public void handleConstructionAction(CelestialAsset asset) {
                        assetActionController.handleConstructionAction(assetUiState, asset);
                        assetActionsWidget.markStructureDirty();
                        assetActionsWidget.markContentDirty();
                    }

                    @Override
                    public void dismissPendingResourceTransfer() {
                        assetActionController.dismissPendingResourceTransfer(assetUiState);
                        assetActionsWidget.markStructureDirty();
                    }

                    @Override
                    public void sendPendingResourceTransfer(StationTransferTarget target) {
                        assetActionController.sendPendingResourceTransfer(assetUiState, target);
                        assetActionsWidget.markStructureDirty();
                    }

                    @Override
                    public void dismissPendingModalByOutsideClick() {
                        assetActionController.dismissPendingModalByOutsideClick(assetUiState);
                        assetActionsWidget.markStructureDirty();
                    }

                    @Override
                    public void showActionStatus(String message) {
                        OrbitalMapWidget.this.showActionStatus(message);
                    }
                });
            this.transferRenderer = new InterplanetaryTransferSystem.OrbitalTransferRenderer(
                new InterplanetaryTransferSystem.OrbitalTransferRenderer.Callbacks() {

                    @Override
                    public float worldToScreenX(double worldX) {
                        return OrbitalMapWidget.this.worldToScreenX(worldX);
                    }

                    @Override
                    public float worldToScreenY(double worldY) {
                        return OrbitalMapWidget.this.worldToScreenY(worldY);
                    }

                    @Override
                    public double[] getWorldPosition(CelestialObject body) {
                        return OrbitalMapWidget.this.getAbsoluteWorldPos(body);
                    }

                    @Override
                    public double getServerOrbitalTime() {
                        return OrbitalMapWidget.this.getServerOrbitalTime();
                    }

                    @Override
                    public boolean isBodyRendered(CelestialObject body) {
                        return OrbitalMapWidget.this.isTransferEndpointRendered(body);
                    }
                });
            this.transferTooltipWidget = new InterplanetaryTransferSystem.OrbitalTransferTooltipWidget(
                new InterplanetaryTransferSystem.OrbitalTransferTooltipWidget.Callbacks() {

                    @Override
                    public InterplanetaryTransferJob getHoveredTransfer() {
                        InterplanetaryTransferJob simulatedTransfer = clientSimulatedTransferState.hoveredTransfer();
                        return simulatedTransfer == null ? transferState.hoveredTransfer() : simulatedTransfer;
                    }

                    @Override
                    public int getTooltipMouseX() {
                        return clientSimulatedTransferState.hoveredTransfer() == null ? transferState.hoverX()
                            : clientSimulatedTransferState.hoverX();
                    }

                    @Override
                    public int getTooltipMouseY() {
                        return clientSimulatedTransferState.hoveredTransfer() == null ? transferState.hoverY()
                            : clientSimulatedTransferState.hoverY();
                    }

                    @Override
                    public int getViewportWidth() {
                        return OrbitalMapWidget.this.getArea().width;
                    }

                    @Override
                    public int getViewportHeight() {
                        return OrbitalMapWidget.this.getArea().height;
                    }

                    @Override
                    public double getCurrentTime() {
                        return clock.time();
                    }

                    @Override
                    public double getTimeScale() {
                        return clock.timeScale();
                    }

                    @Override
                    public double getServerOrbitalTime() {
                        return OrbitalMapWidget.this.getServerOrbitalTime();
                    }
                });
            this.transferSimulatorWidget = new InterplanetaryTransferSystem.OrbitalTransferSimulatorWidget(
                transferSimulatorState,
                new InterplanetaryTransferSystem.OrbitalTransferSimulatorWidget.Callbacks() {

                    @Override
                    public int getViewportWidth() {
                        return OrbitalMapWidget.this.getArea().width;
                    }

                    @Override
                    public int getViewportHeight() {
                        return OrbitalMapWidget.this.getArea().height;
                    }

                    @Override
                    public void closeTransferSimulator() {
                        transferSimulatorState.close();
                    }

                    @Override
                    public void beginTransferPick(InterplanetaryTransferSystem.TransferPickMode pickMode) {
                        if (!isCreativeBuildModeEnabled()) {
                            transferSimulatorState.close();
                            return;
                        }
                        if (viewRoot.objectClass() != CelestialObject.Class.STAR) {
                            showActionStatus("Open a star system first");
                            transferSimulatorState.cancelPick();
                            return;
                        }
                        transferSimulatorState.beginPick(pickMode);
                        closeContextMenu();
                        showActionStatus(
                            pickMode == InterplanetaryTransferSystem.TransferPickMode.ORIGIN ? "Pick transfer origin"
                                : "Pick transfer destination");
                    }

                    @Override
                    public CelestialObject getCurrentSystemBody() {
                        return viewRoot.objectClass() == CelestialObject.Class.STAR ? viewRoot : null;
                    }

                    @Override
                    public void onPreviewNeeded() {
                        InterplanetaryTransferSystem.updatePreview(transferSimulatorState, root, clock.time());
                    }

                    @Override
                    public void dispatchTransfer() {
                        dispatchSimulatedTransfer();
                    }

                    @Override
                    public void runLambertStressTest() {
                        runTransferPlannerStressTest();
                    }

                    @Override
                    public double getTimeScale() {
                        return clock.timeScale();
                    }
                });
            this.sceneRenderer = new OrbitalScene.OrbitalSceneRenderer(
                new OrbitalScene.OrbitalSceneRenderer.Callbacks() {

                    @Override
                    public double getScale() {
                        return OrbitalMapWidget.this.getScale();
                    }

                    @Override
                    public float worldToScreenX(double wx) {
                        return OrbitalMapWidget.this.worldToScreenX(wx);
                    }

                    @Override
                    public float worldToScreenY(double wy) {
                        return OrbitalMapWidget.this.worldToScreenY(wy);
                    }

                    @Override
                    public ResourceLocation getRenderTexture(CelestialObject body) {
                        return OrbitalMapWidget.this.getRenderTexture(body);
                    }

                    @Override
                    public float getDisplaySpriteSize(CelestialObject body) {
                        return OrbitalMapWidget.this.getDisplaySpriteSize(body);
                    }

                    @Override
                    public float getSelectionBoxRadius(OrbitalScene.ScreenBodyBounds bounds) {
                        return OrbitalMapWidget.this.getSelectionBoxRadius(bounds);
                    }

                    @Override
                    public ResourceLocation getAssetIconTexture(CelestialAsset.Kind kind) {
                        return OrbitalMapWidget.this.getAssetIconTexture(kind);
                    }
                });
            this.pinnedInfoWidget = new OrbitalPinnedInfoContentBuilder.OrbitalPinnedInfoWidget(
                new OrbitalPinnedInfoContentBuilder.OrbitalPinnedInfoWidget.Callbacks() {

                    @Override
                    public CelestialObject getPinnedInfoBody() {
                        return OrbitalMapWidget.this.getPinnedInfoBody();
                    }

                    @Override
                    public int getViewportWidth() {
                        return OrbitalMapWidget.this.getArea().width;
                    }

                    @Override
                    public int getViewportHeight() {
                        return OrbitalMapWidget.this.getArea().height;
                    }

                    @Override
                    public void buildSignatureInto(StringBuilder buf, CelestialObject body, int width, int height) {
                        pinnedInfoContentBuilder.buildSignatureInto(buf, body, width, height);
                    }

                    @Override
                    public List<PinnedInfoRow> buildRows(CelestialObject body) {
                        return pinnedInfoContentBuilder.buildRows(body);
                    }
                });
            this.contextMenuWidget = new OrbitalContextMenuWidget(
                contextMenuState,
                new OrbitalContextMenuWidget.Callbacks() {

                    @Override
                    public int getViewportWidth() {
                        return OrbitalMapWidget.this.getArea().width;
                    }

                    @Override
                    public int getViewportHeight() {
                        return OrbitalMapWidget.this.getArea().height;
                    }

                    @Override
                    public void openAssetActions(CelestialObject body) {
                        assetActionController.openAssetActions(assetUiState, body);
                        assetActionsWidget.markStructureDirty();
                    }

                    @Override
                    public boolean canDebugSatellites(CelestialObject body) {
                        return OrbitalMapWidget.this.canDebugSatellites(body);
                    }

                    @Override
                    public void addSatellite(CelestialObject body, SatelliteKind kind) {
                        OrbitalMapWidget.this.addSatellite(body, kind);
                    }

                    @Override
                    public void deleteSatellites(CelestialObject body, SatelliteKind kind) {
                        OrbitalMapWidget.this.deleteSatellites(body, kind);
                    }

                    @Override
                    public void closeContextMenu() {
                        OrbitalMapWidget.this.closeContextMenu();
                    }
                });
            this.sceneFrameBuilder = new OrbitalScene.OrbitalSceneFrameBuilder(
                new OrbitalScene.OrbitalSceneFrameBuilder.Callbacks() {

                    @Override
                    public double[] getViewOrigin(CelestialObject viewRoot) {
                        return OrbitalMapWidget.this.getAbsoluteWorldPos(viewRoot);
                    }

                    @Override
                    public void fillResolvedBodyDrawState(OrbitalScene.ResolvedBodyDrawState out, CelestialObject body,
                        CelestialObject parent, double worldX, double worldY, float labelAlpha) {
                        OrbitalMapWidget.this.fillResolvedBodyDrawState(out, body, parent, worldX, worldY, labelAlpha);
                    }

                    @Override
                    public boolean shouldTraverseChildren(CelestialObject body) {
                        return OrbitalMapWidget.this.shouldTraverseChildren(body);
                    }

                    @Override
                    public float getInteractionRadius(float renderedRadius) {
                        return OrbitalMapWidget.this.getInteractionRadius(renderedRadius);
                    }

                    @Override
                    public boolean isOnScreen(float sx, float sy, float radius) {
                        return OrbitalMapWidget.this.isOnScreen(sx, sy, radius);
                    }
                });
            this.signalsWidget = new LogisticsSignalsWidget(root, () -> this.viewRoot, () -> this.signalsOpen);
            this.assetsPanelWidget = new SolarSystemAssetPanelWidget(
                root,
                () -> this.viewRoot,
                () -> this.assetsPanelOpen,
                assetId -> StationManagementScreen.open(assetId, isCreativeBuildModeEnabled()));
        }

        public OrbitalMapWidget withInitialLayer(CelestialObject layerRoot) {
            this.initialLayer = layerRoot == null ? root : layerRoot;
            return this;
        }

        public OrbitalMapWidget setBodySelectionListener(BodySelectionListener listener) {
            this.bodySelectionListener = listener;
            return this;
        }

        public OrbitalMapWidget attachRenameField(TextFieldWidget field) {
            this.renameField = field;
            return this;
        }

        public StarmapAssetActions.StarmapAssetActionsWidget createAssetActionsWidget() {
            return assetActionsWidget;
        }

        public OrbitalPinnedInfoContentBuilder.OrbitalPinnedInfoWidget createPinnedInfoWidget() {
            return pinnedInfoWidget;
        }

        public OrbitalContextMenuWidget createContextMenuWidget() {
            return contextMenuWidget;
        }

        public InterplanetaryTransferSystem.OrbitalTransferTooltipWidget createTransferTooltipWidget() {
            return transferTooltipWidget;
        }

        public InterplanetaryTransferSystem.OrbitalTransferSimulatorWidget createTransferSimulatorWidget() {
            return transferSimulatorWidget;
        }

        public LogisticsSignalsWidget createSignalsWidget() {
            return signalsWidget;
        }

        public boolean isSignalsOpen() {
            return signalsOpen;
        }

        public void toggleSignals() {
            signalsOpen = !signalsOpen;
        }

        public SolarSystemAssetPanelWidget createAssetsPanelWidget() {
            return assetsPanelWidget;
        }

        public boolean isAssetsPanelOpen() {
            return assetsPanelOpen;
        }

        public void toggleAssetsPanel() {
            assetsPanelOpen = !assetsPanelOpen;
        }

        public boolean areTransfersHidden() {
            return transfersHidden;
        }

        public void toggleTransfersHidden() {
            transfersHidden = !transfersHidden;
        }

        public boolean areHiddenObjectsShown() {
            if (!isCreativeBuildModeEnabled()) {
                // Hidden asteroid reveal is a debug-only visualization. Drop the
                // flag immediately when permissions/mode no longer allow it.
                CelestialClient.setShowHiddenAsteroidObjects(false);
                return false;
            }
            return CelestialClient.showHiddenAsteroidObjects();
        }

        public void toggleHiddenObjectsShown() {
            if (!isCreativeBuildModeEnabled()) {
                // Keep the client flag authoritative to avoid stale hidden nodes
                // remaining visible after leaving debug mode.
                CelestialClient.setShowHiddenAsteroidObjects(false);
                return;
            }
            CelestialClient.toggleShowHiddenAsteroidObjects();
        }

        public void toggleClickMode() {
            setClickMode(
                getClickMode() == OrbitalMapClickMode.HIERARCHY ? OrbitalMapClickMode.FOLLOW
                    : OrbitalMapClickMode.HIERARCHY);
        }

        public boolean isSatelliteNetworkHidden() {
            return satelliteNetworkHidden;
        }

        public void toggleSatelliteNetworkHidden() {
            satelliteNetworkHidden = !satelliteNetworkHidden;
        }

        public void showLayer(CelestialObject layerRoot) {
            CelestialObject targetLayer = layerRoot == null ? root : layerRoot;
            if (this.viewRoot == targetLayer) return;
            clearLayerSwitchState();
            closeContextMenu();
            assetActionController.closeAssetActions(assetUiState);
            transferSimulatorState.resetSelection();
            CelestialObject anchorBody = null;
            if (this.viewRoot == root && targetLayer.objectClass() == CelestialObject.Class.STAR)
                anchorBody = targetLayer;
            else if (this.viewRoot.objectClass() == CelestialObject.Class.STAR && targetLayer == root)
                anchorBody = this.viewRoot;
            if (anchorBody != null) {
                double transitionTargetZoom = targetLayer == root ? getSystemDepartureZoom(anchorBody)
                    : getGalaxyCutZoom(anchorBody);
                transitionState = transitionState
                    .beginPending(targetLayer, anchorBody, viewState.zoomLevel, transitionTargetZoom);
                pendingFocusBody = null;
                viewState.targetIsometricProgress = 0.0;
                centerOnBody(anchorBody);
                viewState.targetZoomLevel = transitionTargetZoom;
                return;
            }
            applyLayerSwitch(targetLayer, targetLayer);
        }

        public CelestialObject getViewRoot() {
            return viewRoot;
        }

        /** Returns the currently focused (selected) celestial body, or {@code null} if none. */
        public CelestialObject getFocusedBody() {
            return focusedBody;
        }

        public boolean isCreativeModeAvailable() {
            return Minecraft.getMinecraft().thePlayer != null
                && Minecraft.getMinecraft().thePlayer.capabilities.isCreativeMode;
        }

        public boolean isCreativeBuildModeEnabled() {
            return creativeBuildMode && isCreativeModeAvailable();
        }

        public void toggleCreativeBuildMode() {
            if (!isCreativeModeAvailable()) {
                creativeBuildMode = false;
                creativeBuildModePersisted = false;
                transferSimulatorState.close();
                return;
            }
            creativeBuildMode = !creativeBuildMode;
            creativeBuildModePersisted = creativeBuildMode;
            if (!creativeBuildMode) {
                transferSimulatorState.close();
                CelestialClient.setShowHiddenAsteroidObjects(false);
            }
            showActionStatus("Creative build mode " + (creativeBuildMode ? "enabled" : "disabled"));
        }

        public boolean isTransferSimulatorOpen() {
            return transferSimulatorState.isOpen();
        }

        public boolean isDebugOverlayEnabled() {
            return debugOverlayEnabled;
        }

        public void toggleTransferSimulator() {
            if (!isCreativeBuildModeEnabled()) {
                transferSimulatorState.close();
                return;
            }
            if (transferSimulatorState.isOpen()) {
                transferSimulatorState.close();
                showActionStatus("Transfer simulator closed");
                return;
            }
            transferSimulatorState.open();
            showActionStatus("Transfer simulator opened");
        }

        @Override
        public void onInit() {
            super.onInit();
            CelestialObject startingLayer = initialLayer == null ? root : initialLayer;
            resetForLayer(startingLayer);
            this.viewRoot = startingLayer;
            setFocusImmediately(startingLayer);
            viewState.syncToTargets();
            if (guiActionsRegistered) return;
            guiActionsRegistered = true;
            listenGuiAction(
                (IGuiAction.MouseScroll) (direction, amount) -> handleMouseWheel(
                    direction,
                    toLocalMouseX(getContext().getMouseX()),
                    toLocalMouseY(getContext().getMouseY())));
            listenGuiAction((IGuiAction.MousePressed) button -> {
                int localMouseX = toLocalMouseX(getContext().getMouseX());
                int localMouseY = toLocalMouseY(getContext().getMouseY());
                if (transferSimulatorState.isOpen()
                    && transferSimulatorWidget.isPointInPanel(localMouseX, localMouseY)) {
                    dragging = false;
                    dragEnabledForCurrentPress = false;
                    pressedBodyCandidate = null;
                    return false;
                }
                if (assetUiState.isAssetActionsOpen()) {
                    dragging = false;
                    dragEnabledForCurrentPress = false;
                    pressedBodyCandidate = null;
                    return button == 1;
                }
                if (button == 0 && contextMenuState.isOpen()) {
                    dragging = false;
                    dragEnabledForCurrentPress = false;
                    pressedBodyCandidate = null;
                    if (isPointInContextMenu(localMouseX, localMouseY)) return true;
                    closeContextMenu();
                    return true;
                }
                if (button != 0) return false;
                pressMouseX = localMouseX;
                pressMouseY = localMouseY;
                lastMouseX = pressMouseX;
                lastMouseY = pressMouseY;
                InterplanetaryTransferJob clickedTransfer = findTransferAtLocal(pressMouseX, pressMouseY);
                if (clickedTransfer != null) {
                    focusedTransfer = clickedTransfer;
                    focusedBody = null;
                    isFollowing = true;
                    dragging = false;
                    dragEnabledForCurrentPress = false;
                    pressedBodyCandidate = null;
                    closeContextMenu();
                    return true;
                }
                pressedBodyCandidate = findBodyAtLocal(pressMouseX, pressMouseY);
                dragEnabledForCurrentPress = pressedBodyCandidate == null && !transferSimulatorState.isWaitingForPick();
                dragging = false;
                return false;
            });
            listenGuiAction(
                (IGuiAction.MouseDrag) (mouseButton, time) -> handleMouseDragged(
                    toLocalMouseX(getContext().getMouseX()),
                    toLocalMouseY(getContext().getMouseY()),
                    mouseButton,
                    time));
            listenGuiAction((IGuiAction.MouseReleased) mouseButton -> {
                int localMouseX = toLocalMouseX(getContext().getMouseX());
                int localMouseY = toLocalMouseY(getContext().getMouseY());
                if (transferSimulatorState.isOpen()
                    && transferSimulatorWidget.isPointInPanel(localMouseX, localMouseY)) {
                    dragging = false;
                    dragEnabledForCurrentPress = false;
                    pressedBodyCandidate = null;
                    return false;
                }
                if (assetUiState.isAssetActionsOpen()) {
                    dragging = false;
                    dragEnabledForCurrentPress = false;
                    pressedBodyCandidate = null;
                    return mouseButton == 1;
                }
                if (contextMenuState.isOpen()) {
                    if (mouseButton == 0) {
                        if (isPointInContextMenu(localMouseX, localMouseY)) {
                            dragging = false;
                            dragEnabledForCurrentPress = false;
                            pressedBodyCandidate = null;
                            return true;
                        }
                        closeContextMenu();
                        dragging = false;
                        dragEnabledForCurrentPress = false;
                        pressedBodyCandidate = null;
                        return true;
                    } else if (mouseButton == 1 && isPointInContextMenu(localMouseX, localMouseY)) return true;
                }
                if (mouseButton == 1) {
                    CelestialObject clickedBody = findBodyAtLocal(localMouseX, localMouseY);
                    if (clickedBody != null) {
                        openContextMenu(clickedBody, localMouseX, localMouseY);
                        dragging = false;
                        dragEnabledForCurrentPress = false;
                        pressedBodyCandidate = null;
                        return true;
                    }
                    closeContextMenu();
                    return false;
                }
                if (mouseButton == 0 && !dragging) {
                    CelestialObject clickedBody = pressedBodyCandidate;
                    if (clickedBody == null) clickedBody = findBodyAtLocal(localMouseX, localMouseY);
                    if (handleTransferSimulatorPick(clickedBody)) {
                        dragging = false;
                        dragEnabledForCurrentPress = false;
                        pressedBodyCandidate = null;
                        return true;
                    }
                    if (clickedBody != null) handleBodyClick(clickedBody);
                }
                dragging = false;
                dragEnabledForCurrentPress = false;
                pressedBodyCandidate = null;
                return false;
            });
            listenGuiAction((IGuiAction.KeyPressed) this::handleKeyPressed);
        }

        private boolean handleKeyPressed(char ch, int keyCode) {
            if (assetUiState.pendingAssetRename != null) {
                if (keyCode == Keyboard.KEY_ESCAPE) {
                    assetActionController.closePendingAssetRename(assetUiState);
                    return true;
                }
                if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
                    assetActionController.confirmPendingAssetRename(assetUiState);
                    return true;
                }
                return false;
            }
            if (keyCode == 57) {
                clock.togglePaused(isInWorld(), getServerOrbitalTime());
                return true;
            }
            if (keyCode == Keyboard.KEY_B) {
                debugOverlayEnabled = !debugOverlayEnabled;
                return true;
            }
            return false;
        }

        private boolean handleMouseWheel(UpOrDown dir, int mx, int my) {
            int sign = dir.isUp() ? 1 : dir.isDown() ? -1 : 0;
            if (sign == 0) return false;
            if (signalsWidget.isPointInPanel(mx, my)) return false;
            if (assetUiState.isAssetActionsOpen()) {
                if (assetUiState.hasBlockingModal()) return true;
                return !assetActionsWidget.isPointInScrollViewport(mx, my);
            }
            double oldScale = getScale();
            viewState.zoomLevel = Math.max(-7000.0, Math.min(14000.0, viewState.zoomLevel + sign * 0.78));
            int lx = mx;
            int ly = my;
            double wmx = viewState.cameraX + (lx - getArea().width / 2.0) / oldScale;
            double wmy = viewState.cameraY + (ly - getArea().height / 2.0) / oldScale;
            double newScale = getScale();
            viewState.cameraX = wmx - (lx - getArea().width / 2.0) / newScale;
            viewState.cameraY = wmy - (ly - getArea().height / 2.0) / newScale;
            viewState.targetCameraX = viewState.cameraX;
            viewState.targetCameraY = viewState.cameraY;
            viewState.targetZoomLevel = viewState.zoomLevel;
            planetTrackingController.onScrolled();
            return true;
        }

        private boolean handleMouseDragged(int mx, int my, int button, long time) {
            if (button != 0) return false;
            if (assetUiState.isAssetActionsOpen()) return false;
            return true;
        }

        private void updateManualDragging() {
            if (assetUiState.isAssetActionsOpen() || transitionState.hasPending()
                || isLayerSwitchActive()
                || transferSimulatorState.isWaitingForPick()) return;
            if (!Mouse.isButtonDown(0)) return;
            if (!dragEnabledForCurrentPress) return;
            int mx = toLocalMouseX(getContext().getMouseX());
            int my = toLocalMouseY(getContext().getMouseY());
            int lx = mx;
            int ly = my;
            if (!dragging) {
                if (Math.abs(mx - pressMouseX) <= CLICK_DRAG_THRESHOLD
                    && Math.abs(my - pressMouseY) <= CLICK_DRAG_THRESHOLD) return;
                dragging = true;
                lastMouseX = lx;
                lastMouseY = ly;
                return;
            }
            double dx = lx - lastMouseX;
            double dy = ly - lastMouseY;
            if (dx == 0.0 && dy == 0.0) return;
            viewState.cameraX -= dx / getScale();
            viewState.cameraY -= dy / getScale();
            viewState.targetCameraX = viewState.cameraX;
            viewState.targetCameraY = viewState.cameraY;
            isFollowing = false;
            focusedTransfer = null;
            planetTrackingController.onManualCameraMoved();
            lastMouseX = lx;
            lastMouseY = ly;
        }

        private double getScale() {
            return OrbitalZoom.scaleForZoomLevel(viewState.zoomLevel);
        }

        private double getDisplayZoomMultiplier() {
            CelestialObject referenceBody = viewRoot != null ? viewRoot : root;
            if (referenceBody == null) return 1.0;
            double referenceScale = OrbitalZoom.scaleForZoomLevel(getOverviewZoomForBody(referenceBody));
            if (referenceScale <= 1e-9) return 1.0;
            return getScale() / referenceScale;
        }

        private float worldToScreenX(double wx) {
            return (float) ((wx - viewState.cameraX) * getScale() + getArea().width / 2.0);
        }

        private float worldToScreenY(double wy) {
            return (float) ((wy - viewState.cameraY) * getScale() + getArea().height / 2.0);
        }

        private int toLocalMouseX(int mouseX) {
            return mouseX - getArea().x;
        }

        private int toLocalMouseY(int mouseY) {
            return mouseY - getArea().y;
        }

        private float snapToPixel(float value) {
            return Math.round(value);
        }

        private static double lerp(double a, double b, double t) {
            return a + (b - a) * t;
        }

        private static float lerp(float a, float b, float t) {
            return a + (b - a) * t;
        }

        private float getCubeSizeForBody(CelestialObject body) {
            if (focusedBody == null) return body.spriteSize() <= 0.0001f ? ISO_BASE_CUBE_SIZE
                : (float) (ISO_BASE_CUBE_SIZE * Math.sqrt(body.spriteSize()));
            double focusSize = focusedBody.spriteSize();
            if (focusSize <= 0.0001) return body.spriteSize() <= 0.0001f ? ISO_BASE_CUBE_SIZE
                : (float) (ISO_BASE_CUBE_SIZE * Math.sqrt(body.spriteSize()));
            double scale = body.spriteSize() / focusSize;
            return (float) (ISO_BASE_CUBE_SIZE * scale);
        }

        private float getSpriteRadius(CelestialObject body) {
            if (body != null && body.isAsteroid()) {
                // Asteroids use relative zoom so their apparent size tracks the
                // current focused system instead of being clamped like planet icons.
                return mapAsteroidSpriteRadiusForRelativeZoom(
                    body,
                    getDisplaySpriteSize(body),
                    getDisplayZoomMultiplier());
            }
            return mapSpriteRadiusForScale(body, getDisplaySpriteSize(body), getScale());
        }

        static float mapAsteroidSpriteRadiusForRelativeZoom(CelestialObject body, double relativeZoom) {
            float spriteSize = body == null ? 0f : (float) body.spriteSize();
            return mapAsteroidSpriteRadiusForRelativeZoom(body, spriteSize, relativeZoom);
        }

        private static float mapAsteroidSpriteRadiusForRelativeZoom(CelestialObject body, float spriteSize,
            double relativeZoom) {
            if (body == null || !body.isAsteroid() || spriteSize <= 0.0001f) return 0f;
            return AsteroidStarmapProjection.spriteRadius(body, spriteSize, relativeZoom);
        }

        static float mapSpriteRadiusForScale(CelestialObject body, double scale) {
            float spriteSize = body == null ? 0f : (float) body.spriteSize();
            return mapSpriteRadiusForScale(body, spriteSize, scale);
        }

        private static float mapSpriteRadiusForScale(CelestialObject body, float spriteSize, double scale) {
            if (spriteSize <= 0.0001f) return 2f;
            float radius = spriteSize * (MAP_ICON_BASE_SCALE + (float) scale * MAP_ICON_ZOOM_SCALE);
            return Math.max(2.0f, radius);
        }

        private float getDisplaySpriteSize(CelestialObject body) {
            if (body == null) return 0f;
            float systemSize = (float) body.spriteSize();
            float galaxySize = GALAXY_MAP_STAR_SPRITE_SIZE;
            if (body == transitionState.pendingAnchor() && transitionState.hasPending()
                && body.objectClass() == CelestialObject.Class.STAR) {
                if (transitionState.pendingTarget() == root) {
                    float progress = getTransitionProgress(
                        viewState.zoomLevel,
                        transitionState.pendingStartZoom(),
                        transitionState.pendingTargetZoom());
                    return lerp(systemSize, galaxySize, progress);
                }
                return galaxySize;
            }
            if (body == transitionState.activeAnchor() && body.objectClass() == CelestialObject.Class.STAR
                && (transitionState.phase() == OrbitalLayerTransitionState.Phase.SYSTEM_PRE_CUT
                    || transitionState.phase() == OrbitalLayerTransitionState.Phase.GALAXY_POST_CUT)) {
                float progress = getTransitionProgress(
                    viewState.zoomLevel,
                    transitionState.activeStartZoom(),
                    transitionState.activeTargetZoom());
                return lerp(
                    transitionState.activeStartSpriteSize(),
                    transitionState.activeTargetSpriteSize(),
                    progress);
            }
            if (viewRoot == root && body.objectClass() == CelestialObject.Class.STAR) {
                CelestialObject parent = findParent(root, body);
                if (parent != null && parent.objectClass() == CelestialObject.Class.GALAXY) return galaxySize;
            }
            return systemSize;
        }

        private float getTransitionProgress(double current, double start, double end) {
            double delta = end - start;
            if (Math.abs(delta) < 1e-9) return 1.0f;
            return (float) Math.max(0.0, Math.min(1.0, (current - start) / delta));
        }

        private float getRenderedBodyRadius(CelestialObject body) {
            if (getRenderTexture(body) != null && getDisplaySpriteSize(body) > 0.0001f) {
                float spriteR = getSpriteRadius(body);
                float cubeR = getCubeSizeForBody(body) * 0.5f;
                return lerp(spriteR, cubeR, (float) viewState.isometricProgress);
            }
            return body == viewRoot ? 11f : 7f;
        }

        private ResourceLocation getRenderTexture(CelestialObject body) {
            if (body == null || body.objectClass() == CelestialObject.Class.GALAXY) return null;
            ResourceLocation texture = body.texture();
            if (isMapBodyIcon(texture)) return texture;
            return EnumTextures.ICON_EGORA.get();
        }

        private boolean isMapBodyIcon(ResourceLocation texture) {
            if (texture == null || texture.getResourcePath() == null) return false;
            String path = texture.getResourcePath();
            return path.contains("textures/gui/bodyicons/") || path.startsWith("textures/gui/icon_");
        }

        public void focusOn(CelestialObject body) {
            if (body == null) return;
            focusedTransfer = null;
            if (viewState.isometricProgress < 0.01) setFocusImmediately(body);
            else {
                pendingFocusBody = body;
                viewState.targetIsometricProgress = 0.0;
            }
        }

        private boolean handleBodyClick(CelestialObject clickedBody) {
            if (clickedBody == null) return false;
            boolean opensSystemFromGalaxy = viewRoot == root && clickedBody.objectClass() == CelestialObject.Class.STAR
                && bodySelectionListener != null;
            OrbitalPlanetTrackingController.ClickAction action = planetTrackingController
                .clickBody(clickedBody, opensSystemFromGalaxy);
            switch (action) {
                case TRACK_ONLY -> centerOnBody(clickedBody);
                case FOCUS_AND_SELECT -> {
                    focusOn(clickedBody);
                    if (bodySelectionListener != null) bodySelectionListener.onBodySelected(clickedBody);
                }
                case SELECT_ONLY -> {
                    if (bodySelectionListener != null) bodySelectionListener.onBodySelected(clickedBody);
                }
            }
            return true;
        }

        public OrbitalMapClickMode getClickMode() {
            return planetTrackingController.clickMode();
        }

        public void setClickMode(OrbitalMapClickMode clickMode) {
            planetTrackingController.setClickMode(clickMode);
        }

        private void centerOnBody(CelestialObject body) {
            if (body == null) return;
            focusedBody = body;
            focusedTransfer = null;
            isFollowing = true;
            double[] pos = getAbsoluteWorldPos(body);
            if (pos != null) {
                viewState.targetCameraX = pos[0];
                viewState.targetCameraY = pos[1];
            }
            viewState.targetIsometricProgress = 0.0;
        }

        private void applyLayerSwitch(CelestialObject targetLayer, CelestialObject focusBody) {
            this.viewRoot = targetLayer == null ? root : targetLayer;
            focusOn(focusBody == null ? this.viewRoot : focusBody);
        }

        private void setFocusImmediately(CelestialObject body) {
            focusedBody = body;
            focusedTransfer = null;
            isFollowing = true;
            double[] pos = getAbsoluteWorldPos(body);
            if (pos != null) {
                viewState.targetCameraX = pos[0];
                viewState.targetCameraY = pos[1];
            }
            boolean goIso = OrbitalZoom.useIsometricOverview(body);
            viewState.targetIsometricProgress = goIso ? 1.0 : 0.0;
            viewState.targetZoomLevel = getOverviewZoomForBody(body);
        }

        private void resetForLayer(CelestialObject layerRoot) {
            isFollowing = false;
            focusedBody = null;
            focusedTransfer = null;
            viewState.reset(layerRoot == root);
        }

        private boolean isReadyForPendingLayerSwitch() {
            return Math.abs(viewState.cameraX - viewState.targetCameraX) < PENDING_LAYER_SWITCH_CAMERA_THRESHOLD
                && Math.abs(viewState.cameraY - viewState.targetCameraY) < PENDING_LAYER_SWITCH_CAMERA_THRESHOLD;
        }

        private boolean isReadyForLayerSwitchPhase() {
            return isReadyForPendingLayerSwitch()
                && Math.abs(viewState.zoomLevel - viewState.targetZoomLevel) < LAYER_SWITCH_CONVERGE_THRESHOLD;
        }

        private double calculateOverviewExtent(CelestialObject body) {
            if (body.objectClass() == CelestialObject.Class.GALAXY) {
                double maxDistance = 0.0;
                for (CelestialObject child : CelestialClient.getChildren(body)) {
                    double[] pos = getAbsoluteWorldPos(child);
                    if (pos == null) continue;
                    maxDistance = Math.max(maxDistance, Math.hypot(pos[0], pos[1]));
                }
                return maxDistance;
            }
            double maxSize = 0.0;
            for (CelestialObject child : CelestialClient.getChildren(body)) maxSize = Math.max(
                maxSize,
                child.orbitalParams()
                    .apogee());
            return maxSize;
        }

        private double calculateFocusedOrbitExtent(CelestialObject body) {
            CelestialObject parent = findParent(root, body);
            if (parent == null) return 0.0;
            double maxApogee = 0.0;
            for (CelestialObject sibling : CelestialClient.getChildren(parent)) maxApogee = Math.max(
                maxApogee,
                sibling.orbitalParams()
                    .apogee());
            return maxApogee;
        }

        private double computeOverviewZoom(CelestialObject body, boolean goIso) {
            double extent = goIso ? calculateFocusedOrbitExtent(body) : calculateOverviewExtent(body);
            return OrbitalZoom.overviewZoomForExtent(extent, goIso);
        }

        private double getOverviewZoomForBody(CelestialObject body) {
            return computeOverviewZoom(body, OrbitalZoom.useIsometricOverview(body));
        }

        private double viewportHalfDiagonal() {
            return OrbitalZoom.viewportHalfDiagonal(getArea().width, getArea().height);
        }

        private double viewportMinDimension() {
            return OrbitalZoom.viewportMinDimension(getArea().width, getArea().height);
        }

        private double getSystemDepartureZoom(CelestialObject star) {
            double farthestOrbit = calculateOverviewExtent(star);
            return OrbitalZoom.zoomForWorldDistance(
                farthestOrbit * SYSTEM_DEPARTURE_EXTENT_MULTIPLIER,
                OrbitalZoom.OVERVIEW_SCREEN_RADIUS);
        }

        private double getNearestOtherStarDistance(CelestialObject anchorStar) {
            return OrbitalZoom
                .nearestOtherStarDistance(anchorStar, CelestialClient.getChildren(root), this::getAbsoluteWorldPos);
        }

        private double getGalaxyOverviewZoom(CelestialObject anchorStar) {
            double nearestDistance = getNearestOtherStarDistance(anchorStar);
            if (nearestDistance == Double.MAX_VALUE || nearestDistance <= 1e-9) return getOverviewZoomForBody(root);
            return OrbitalZoom.zoomForWorldDistance(nearestDistance, viewportMinDimension() * 0.2);
        }

        private double getGalaxyCutZoom(CelestialObject anchorStar) {
            double nearestDistance = getNearestOtherStarDistance(anchorStar);
            if (nearestDistance == Double.MAX_VALUE || nearestDistance <= 1e-9)
                return getGalaxyOverviewZoom(anchorStar);
            return OrbitalZoom.zoomForWorldDistance(nearestDistance, viewportHalfDiagonal() * 1.5);
        }

        private boolean isLayerSwitchActive() {
            return transitionState.isActive();
        }

        private void clearLayerSwitchState() {
            transitionState = transitionState.clear();
        }

        private void startLayerSwitchTransition(CelestialObject targetLayer, CelestialObject anchorBody,
            float currentAnchorSpriteSize) {
            if (targetLayer == root) {
                transitionState = transitionState.beginActive(
                    OrbitalLayerTransitionState.Phase.SYSTEM_PRE_CUT,
                    targetLayer,
                    anchorBody,
                    viewState.zoomLevel,
                    getSystemDepartureZoom(anchorBody),
                    currentAnchorSpriteSize,
                    GALAXY_MAP_STAR_SPRITE_SIZE);
            } else {
                transitionState = transitionState.beginActive(
                    OrbitalLayerTransitionState.Phase.GALAXY_PRE_CUT,
                    targetLayer,
                    anchorBody,
                    viewState.zoomLevel,
                    getGalaxyCutZoom(anchorBody),
                    currentAnchorSpriteSize,
                    (float) anchorBody.spriteSize());
            }
            viewState.targetZoomLevel = transitionState.activeTargetZoom();
            viewState.targetIsometricProgress = 0.0;
        }

        private void updateLayerSwitchTransition() {
            if (!transitionState.isActive() || transitionState.activeTarget() == null
                || transitionState.activeAnchor() == null) return;
            if (!isReadyForLayerSwitchPhase()) return;
            if (transitionState.phase() == OrbitalLayerTransitionState.Phase.SYSTEM_PRE_CUT) {
                double[] anchorPos = getAbsoluteWorldPos(transitionState.activeAnchor());
                this.viewRoot = root;
                focusedBody = transitionState.activeAnchor();
                focusedTransfer = null;
                isFollowing = true;
                if (anchorPos != null) viewState.setCamera(anchorPos[0], anchorPos[1]);
                viewState.zoomLevel = getGalaxyCutZoom(transitionState.activeAnchor());
                viewState.targetZoomLevel = getGalaxyOverviewZoom(transitionState.activeAnchor());
                viewState.isometricProgress = 0.0;
                viewState.targetIsometricProgress = 0.0;
                pendingFocusBody = null;
                transitionState = transitionState.beginActive(
                    OrbitalLayerTransitionState.Phase.SYSTEM_POST_CUT,
                    transitionState.activeTarget(),
                    transitionState.activeAnchor(),
                    transitionState.activeStartZoom(),
                    transitionState.activeTargetZoom(),
                    transitionState.activeStartSpriteSize(),
                    transitionState.activeTargetSpriteSize());
                return;
            }
            if (transitionState.phase() == OrbitalLayerTransitionState.Phase.GALAXY_PRE_CUT) {
                double[] anchorPos = getAbsoluteWorldPos(transitionState.activeAnchor());
                this.viewRoot = transitionState.activeTarget();
                focusedBody = transitionState.activeAnchor();
                focusedTransfer = null;
                isFollowing = true;
                if (anchorPos != null) viewState.setCamera(anchorPos[0], anchorPos[1]);
                viewState.zoomLevel = getSystemDepartureZoom(transitionState.activeAnchor());
                viewState.targetZoomLevel = getOverviewZoomForBody(transitionState.activeTarget());
                viewState.isometricProgress = 0.0;
                viewState.targetIsometricProgress = 0.0;
                pendingFocusBody = null;
                transitionState = transitionState.beginActive(
                    OrbitalLayerTransitionState.Phase.GALAXY_POST_CUT,
                    transitionState.activeTarget(),
                    transitionState.activeAnchor(),
                    viewState.zoomLevel,
                    viewState.targetZoomLevel,
                    GALAXY_MAP_STAR_SPRITE_SIZE,
                    (float) transitionState.activeAnchor()
                        .spriteSize());
                return;
            }
            transitionState = transitionState.clearActive();
        }

        private void ensureWorldStateCache() {
            worldStateCache.ensure(root, clock.time());
        }

        private double[] getAbsoluteWorldPos(CelestialObject target) {
            ensureWorldStateCache();
            return worldStateCache.getWorldPosition(target);
        }

        private CelestialObject findParent(CelestialObject cur, CelestialObject target) {
            if (cur != root) return null;
            ensureWorldStateCache();
            return worldStateCache.getParent(target);
        }

        private void fillIsometricScreenPos(CelestialObject body, float[] out) {
            float cx = getArea().width / 2f;
            float cy = getArea().height / 2f + ISO_Y_OFFSET;
            if (focusedBody == null || sameBody(focusedBody, root)) {
                out[0] = cx;
                out[1] = cy;
                return;
            }
            CelestialObject parent = findParent(root, focusedBody);
            if (parent == null) {
                out[0] = cx;
                out[1] = cy;
                return;
            }
            if (sameBody(body, parent)) {
                out[0] = cx - ISO_OFFSET;
                out[1] = cy;
                return;
            }
            if (sameBody(body, focusedBody)) {
                out[0] = cx;
                out[1] = cy;
                return;
            }
            List<CelestialObject> children = CelestialClient.getChildren(focusedBody);
            int index = indexOfBodyByKey(children, body);
            if (index >= 0) {
                out[0] = cx + ISO_OFFSET + index * ISO_SPACING;
                out[1] = cy;
                return;
            }
            out[0] = -1000f;
            out[1] = -1000f;
        }

        private boolean isImportantInIsoMode(CelestialObject body) {
            if (focusedBody == null || sameBody(focusedBody, root)) return true;
            CelestialObject parent = findParent(root, focusedBody);
            if (parent == null) return false;
            return sameBody(body, parent) || sameBody(body, focusedBody)
                || containsBodyByKey(CelestialClient.getChildren(focusedBody), body);
        }

        private boolean shouldTraverseChildren(CelestialObject body) {
            return shouldTraverseChildrenInLayer(root, viewRoot, body);
        }

        static boolean shouldTraverseChildrenInLayer(CelestialObject root, CelestialObject viewRoot,
            CelestialObject body) {
            if (!sameBody(viewRoot, root)) return true;
            // In the system overview we normally stop at first-level bodies, but
            // asteroid belts must expose their dynamic asteroid children directly.
            return sameBody(body, root) || body != null && body.isAsteroidBelt();
        }

        private boolean isVisibleInCurrentLayer(CelestialObject body) {
            return isDescendantOrSelf(viewRoot, body);
        }

        private boolean isTransferEndpointRendered(CelestialObject body) {
            return body != null && isVisibleInCurrentLayer(body) && shouldRenderBodyAtCurrentZoom(body);
        }

        private boolean isDescendantOrSelf(CelestialObject ancestor, CelestialObject target) {
            if (sameBody(ancestor, target)) return true;
            for (CelestialObject child : CelestialClient.getChildren(ancestor))
                if (isDescendantOrSelf(child, target)) return true;
            return false;
        }

        static boolean sameBody(CelestialObject left, CelestialObject right) {
            if (left == right) return true;
            if (left == null || right == null || left.key() == null || right.key() == null) return false;
            return left.key()
                .equals(right.key());
        }

        static boolean containsBodyByKey(List<CelestialObject> bodies, CelestialObject target) {
            return indexOfBodyByKey(bodies, target) >= 0;
        }

        private static int indexOfBodyByKey(List<CelestialObject> bodies, CelestialObject target) {
            if (bodies == null || target == null) return -1;
            for (int i = 0; i < bodies.size(); i++) if (sameBody(bodies.get(i), target)) return i;
            return -1;
        }

        @Override
        public void drawBackground(ModularGuiContext context, WidgetThemeEntry widgetTheme) {
            clock.advance(isInWorld(), getServerOrbitalTime());
            updateManualDragging();
            updateRenameFieldLayout();
            double activeLerpSpeed = transitionState.hasPending() ? PENDING_LAYER_CENTER_LERP_SPEED
                : isLayerSwitchActive() ? LAYER_SWITCH_LERP_SPEED : LERP_SPEED;
            viewState.step(activeLerpSpeed);
            viewState.snap(CONVERGE_THRESHOLD);
            if (pendingFocusBody != null && viewState.isometricProgress < 0.01) {
                setFocusImmediately(pendingFocusBody);
                pendingFocusBody = null;
            }
            if (transitionState.hasPending() && isReadyForPendingLayerSwitch()) {
                CelestialObject targetLayer = transitionState.pendingTarget();
                CelestialObject anchorBody = transitionState.pendingAnchor();
                float currentAnchorSpriteSize = getDisplaySpriteSize(anchorBody);
                transitionState = transitionState.clearPending();
                startLayerSwitchTransition(targetLayer, anchorBody, currentAnchorSpriteSize);
            }
            updateLayerSwitchTransition();
            ensureWorldStateCache();
            if (isFollowing && focusedTransfer != null) {
                if (InterplanetaryTransferSystem
                    .writeCurrentTransferPoint(focusedTransfer, clock.time(), focusedTransferPoint)
                    && focusedTransferPoint.valid()
                    && !focusedTransfer.isFinished(clock.time())) {
                    viewState.cameraX = focusedTransferPoint.worldX();
                    viewState.cameraY = focusedTransferPoint.worldY();
                    viewState.targetCameraX = focusedTransferPoint.worldX();
                    viewState.targetCameraY = focusedTransferPoint.worldY();
                } else {
                    focusedTransfer = null;
                    isFollowing = false;
                }
            } else if (isFollowing && focusedBody != null) {
                double[] pos = getAbsoluteWorldPos(focusedBody);
                if (pos != null) {
                    viewState.targetCameraX = pos[0];
                    viewState.targetCameraY = pos[1];
                    viewState.syncCameraToTarget();
                }
            }
            Gui.drawRect(0, 0, getArea().width, getArea().height, EnumColors.MapBackground.getColor());
            GlStateManager.pushMatrix();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GlStateManager.disableTexture2D();
            GL11.glEnable(GL11.GL_LINE_SMOOTH);
            float labelAlpha = (float) Math.max(0.0, 1.0 - viewState.isometricProgress * 2.5);
            sceneFrame = sceneFrameBuilder.buildInto(sceneFrame, viewRoot, clock.time(), labelAlpha);
            syncRenderedLogisticsTransfers();
            if (transferSimulatorState.isOpen() && !transferSimulatorState.isWaitingForPick()
                && viewRoot.objectClass() == CelestialObject.Class.STAR) {
                InterplanetaryTransferSystem.updatePreview(transferSimulatorState, root, clock.time());
            }
            if (!transfersHidden) {
                transferRenderer.drawTransferPaths(
                    transferState,
                    viewRoot,
                    clock.time(),
                    viewRoot.objectClass() == CelestialObject.Class.STAR
                        ? (float) Math.max(0.0, 1.0 - viewState.isometricProgress * 2.5)
                        : 0f);
                transferRenderer.drawTransferPaths(
                    clientSimulatedTransferState,
                    viewRoot,
                    clock.time(),
                    viewRoot.objectClass() == CelestialObject.Class.STAR
                        ? (float) Math.max(0.0, 1.0 - viewState.isometricProgress * 2.5)
                        : 0f);
                transferRenderer.drawPreviewTrajectory(
                    transferSimulatorState,
                    viewRoot.objectClass() == CelestialObject.Class.STAR
                        ? (float) Math.max(0.0, 1.0 - viewState.isometricProgress * 2.5)
                        : 0f);
            }
            sceneRenderer.drawOrbits(sceneFrame, (float) Math.max(0.0, 1.0 - viewState.isometricProgress * 2.5));
            GL11.glDisable(GL11.GL_LINE_SMOOTH);
            GL11.glLineWidth(1f);
            GlStateManager.enableTexture2D();
            sceneRenderer.drawSpheresOfInfluence(sceneFrame);
            sceneRenderer.drawBodies(sceneFrame, viewRoot);
            if (!satelliteNetworkHidden) {
                satelliteNetworkOverlay.draw(
                    sceneFrame,
                    viewRoot.objectClass() == CelestialObject.Class.STAR
                        ? (float) Math.max(0.0, 1.0 - viewState.isometricProgress * 2.5)
                        : 0f);
            }
            if (!transfersHidden) {
                transferRenderer.drawTransferDots(
                    transferState,
                    viewRoot,
                    clock.time(),
                    viewRoot.objectClass() == CelestialObject.Class.STAR
                        ? (float) Math.max(0.0, 1.0 - viewState.isometricProgress * 2.5)
                        : 0f);
                transferRenderer.drawTransferDots(
                    clientSimulatedTransferState,
                    viewRoot,
                    clock.time(),
                    viewRoot.objectClass() == CelestialObject.Class.STAR
                        ? (float) Math.max(0.0, 1.0 - viewState.isometricProgress * 2.5)
                        : 0f);
            }
            if (labelAlpha > 0.02f) GlStateManager.color(1f, 1f, 1f, 1f);
            GlStateManager.enableTexture2D();
            GlStateManager.color(1f, 1f, 1f, 1f);
            GlStateManager.popMatrix();
            sceneRenderer.drawCollectedLabels(sceneFrame);
            sceneRenderer.drawCollectedMarkers(sceneFrame);
            drawActionStatusMessage();
            sceneRenderer.drawViewTitleBanner(viewRoot, getArea().width);
            drawViewStatusLabel(viewRoot, getArea().width);
            drawHammerTrajectoryLoadDebug(getArea().width);
            int localMouseX = getContext().getMouseX();
            int localMouseY = getContext().getMouseY();
            if (transfersHidden || dragging
                || viewRoot.objectClass() != CelestialObject.Class.STAR
                || viewState.isometricProgress > 0.95
                || assetUiState.isAssetActionsOpen()
                || contextMenuState.isOpen()) {
                transferState.updateHoveredTransfer(null, localMouseX, localMouseY);
                clientSimulatedTransferState.updateHoveredTransfer(null, localMouseX, localMouseY);
            } else {
                InterplanetaryTransferJob hoveredSimulatedTransfer = transferRenderer.findHoveredTransfer(
                    clientSimulatedTransferState,
                    viewRoot,
                    clock.time(),
                    localMouseX,
                    localMouseY);
                clientSimulatedTransferState.updateHoveredTransfer(hoveredSimulatedTransfer, localMouseX, localMouseY);
                transferState.updateHoveredTransfer(
                    hoveredSimulatedTransfer == null
                        ? transferRenderer
                            .findHoveredTransfer(transferState, viewRoot, clock.time(), localMouseX, localMouseY)
                        : null,
                    localMouseX,
                    localMouseY);
            }
            hoveredBody = dragging ? null : findBodyAtLocal(localMouseX, localMouseY);
            if (hoveredBody != null && hoveredBody.objectClass() == CelestialObject.Class.GALAXY) hoveredBody = null;
            if (hoveredBody != null && isVisibleInCurrentLayer(hoveredBody)) {
                if (!sameBody(hoveredBody, focusedBody)) sceneRenderer.drawHoverHighlight(hoveredBody, sceneFrame);
            }
            if (focusedBody != null && focusedBody.objectClass() != CelestialObject.Class.GALAXY
                && isVisibleInCurrentLayer(focusedBody)) sceneRenderer.drawSelectionHighlight(focusedBody, sceneFrame);
            if (debugOverlayEnabled) sceneRenderer.drawDebugOverlay(sceneFrame, getArea().height);
            super.drawBackground(context, widgetTheme);
            if (!dragging && !contextMenuState.isOpen() && !assetUiState.isAssetActionsOpen()) {
                drawSatelliteMarkerTooltip(sceneFrame, localMouseX, localMouseY);
            }
        }

        private void fillResolvedBodyDrawState(OrbitalScene.ResolvedBodyDrawState out, CelestialObject body,
            CelestialObject parent, double worldX, double worldY, float labelAlpha) {
            fillIsometricScreenPos(body, isoScratchPos);
            float screenX = snapToPixel(
                (float) lerp(worldToScreenX(worldX), isoScratchPos[0], viewState.isometricProgress));
            float screenY = snapToPixel(
                (float) lerp(worldToScreenY(worldY), isoScratchPos[1], viewState.isometricProgress));
            float bodyAlpha = getBodyRenderAlpha(body);
            if (CelestialClient.isDebugHiddenAsteroid(body) || CelestialClient.isAsteroidScanInProgress(body)
                || CelestialClient.isSensorRevealedAsteroid(body)) bodyAlpha *= 0.35f;
            float renderedRadius = getRenderedBodyRadius(body);
            boolean renderBody = shouldRenderBodyAtCurrentZoom(body);
            boolean drawLabel = false;
            float labelY = 0f;
            int labelColor = 0;
            if (labelAlpha > 0.02f && !sameBody(body, root)
                && !sameBody(body, focusedBody)
                && renderBody
                && (AsteroidStarmapScenePresentation.drawsDefaultBodyLabel(body)
                    || CelestialClient.isAsteroidScanInProgress(body))) {
                float actualLabelAlpha = getLabelRenderAlpha(body, labelAlpha);
                if (actualLabelAlpha > 0.01f) {
                    drawLabel = true;
                    labelY = screenY + getLabelYOffset(renderedRadius);
                    labelColor = StarmapColor.withAlpha(EnumColors.MapCelestialLabelText.getColor(), actualLabelAlpha);
                }
            }
            out.set(
                body,
                parent,
                worldX,
                worldY,
                screenX,
                screenY,
                renderedRadius,
                bodyAlpha,
                renderBody,
                drawLabel,
                labelY,
                labelColor);
        }

        private float getBodyRenderAlpha(CelestialObject body) {
            if (viewState.isometricProgress < 0.01) return 1f;
            if (isImportantInIsoMode(body)) return 1f;
            return (float) Math.max(0.0, 1.0 - viewState.isometricProgress * 3.0);
        }

        private float getLabelRenderAlpha(CelestialObject body, float labelAlpha) {
            if (viewState.isometricProgress < 0.01 || isImportantInIsoMode(body)) return labelAlpha;
            return labelAlpha * (float) Math.max(0.0, 1.0 - viewState.isometricProgress * 3.0);
        }

        private InterplanetaryTransferJob findTransferAtLocal(int mouseX, int mouseY) {
            if (viewRoot.objectClass() != CelestialObject.Class.STAR || viewState.isometricProgress > 0.95
                || assetUiState.isAssetActionsOpen()
                || contextMenuState.isOpen()
                || transferSimulatorState.isWaitingForPick()) return null;
            InterplanetaryTransferJob simulatedTransfer = transferRenderer
                .findHoveredTransfer(clientSimulatedTransferState, viewRoot, clock.time(), mouseX, mouseY);
            return simulatedTransfer == null
                ? transferRenderer.findHoveredTransfer(transferState, viewRoot, clock.time(), mouseX, mouseY)
                : simulatedTransfer;
        }

        private CelestialObject findBodyAtLocal(float localX, float localY) {
            CelestialObject best = null;
            double bestScore = Double.MAX_VALUE;
            for (int i = sceneFrame.screenBodies.size() - 1; i >= 0; i--) {
                OrbitalScene.ScreenBodyBounds bounds = sceneFrame.screenBodies.get(i);
                double score = bounds.bodyScore(localX, localY);
                if (score < bestScore) {
                    best = bounds.body();
                    bestScore = score;
                }
            }
            return best;
        }

        private boolean handleTransferSimulatorPick(CelestialObject clickedBody) {
            if (!transferSimulatorState.isWaitingForPick()) return false;
            if (viewRoot.objectClass() != CelestialObject.Class.STAR) {
                transferSimulatorState.cancelPick();
                showActionStatus("Open a star system first");
                return true;
            }
            if (clickedBody == null || clickedBody == root
                || clickedBody.objectClass() == CelestialObject.Class.GALAXY
                || !isDescendantOrSelf(viewRoot, clickedBody)) {
                transferSimulatorState.cancelPick();
                showActionStatus("Pick a body from the current system");
                return true;
            }
            transferSimulatorState.applyPickedBody(clickedBody);
            showActionStatus("Selected " + clickedBody.displayName());
            return true;
        }

        private void createResourceTransfer(CelestialObject sourceBody, CelestialAsset sourceAsset,
            StationTransferTarget target) {
            if (sourceBody == null || sourceAsset == null || target == null || target.hostBody() == null) {
                showActionStatus("Transfer failed");
                return;
            }
            InterplanetaryTransferJob transfer = transferSupport.createTransferJob(
                root,
                sourceBody,
                target.hostBody(),
                sourceAsset.displayName() + " -> " + target.displayName(),
                assetSupport.buildConstructionInventorySummary(sourceAsset),
                clock.time());
            if (transfer == null) {
                showActionStatus("Transfer failed");
                return;
            }
            transferState.addTransfer(transfer);
            showActionStatus("Transfer dispatched");
        }

        private static boolean isInWorld() {
            return Minecraft.getMinecraft().theWorld != null;
        }

        private double getServerOrbitalTime() {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.theWorld == null) return clock.time();
            double partialTicks = RenderTickState.getLastPartialTicks();
            return (mc.theWorld.getTotalWorldTime() + partialTicks) * OrbitalTransferPlanner.OSU_PER_TICK;
        }

        private void syncRenderedLogisticsTransfers() {
            int revision = CelestialClient.clientDeliveryRevision();
            if (revision == lastRenderedLogisticsTaskRevision && clock.revision() == lastRenderedLogisticsClockRevision)
                return;

            List<InterplanetaryTransferJob> logisticsTransfers = new ArrayList<>();
            for (LogisticsDelivery delivery : CelestialClient.clientDeliveries()) {
                InterplanetaryTransferJob transfer = buildRenderedLogisticsTransfer(delivery);
                if (transfer != null) logisticsTransfers.add(transfer);
            }

            transferState.replaceTransfersMatching(
                transfer -> transfer.transferId() != null && transfer.transferId()
                    .startsWith("logistics:"),
                logisticsTransfers);
            lastRenderedLogisticsTaskRevision = revision;
            lastRenderedLogisticsClockRevision = clock.revision();
        }

        private InterplanetaryTransferJob buildRenderedLogisticsTransfer(LogisticsDelivery delivery) {
            if (delivery == null || delivery.data.resourceId() == null) return null;
            CelestialObject sourceBody = GalaxiaCelestialAPI.findBodyByKey(root, delivery.data.fromBodyKey());
            CelestialObject destinationBody = GalaxiaCelestialAPI.findBodyByKey(root, delivery.data.toBodyKey());
            if (sourceBody == null || destinationBody == null) return null;

            String itemName = delivery.data.resourceId()
                .toStack(1)
                .getDisplayName();
            String summary = delivery.data.amount() + " x " + itemName;
            double departureDisplayTime = clock.toDisplayTime(delivery.data.departureOrbitalTime());
            double arrivalDisplayTime = clock
                .toDisplayTime(delivery.data.departureOrbitalTime() + delivery.data.tofOrbitalSeconds());
            double displayedTof = Math.max(1e-6, arrivalDisplayTime - departureDisplayTime);
            OrbitalTransferPlanner.TransferRoute route = delivery.data.transferRoute();
            InterplanetaryTransferJob base = route != null && route.hasTrajectoryGeometry()
                ? transferSupport.createTransferJob(
                    root,
                    sourceBody,
                    destinationBody,
                    TransferPackageKind.HAMMER.displayName(),
                    summary,
                    departureDisplayTime,
                    displayedTof,
                    route)
                : transferSupport.createTransferJob(
                    root,
                    sourceBody,
                    destinationBody,
                    TransferPackageKind.HAMMER.displayName(),
                    summary,
                    departureDisplayTime,
                    displayedTof);
            if (base == null) return null;

            return new InterplanetaryTransferJob(
                "logistics:" + delivery.deliveryId,
                base.displayName(),
                base.inventorySummary(),
                base.rootBody(),
                base.sourceBody(),
                base.destinationBody(),
                base.orbitAnchorBody(),
                base.departureTime(),
                base.arrivalTime(),
                base.trajectoryXs(),
                base.trajectoryYs(),
                base.trajectoryPointCount(),
                base.packageKind());
        }

        private void dispatchSimulatedTransfer() {
            if (!transferSimulatorState.isOpen()) return;
            if (transferSimulatorState.originBody() == null || transferSimulatorState.destinationBody() == null) {
                /// TODO: LOCALLIZE
                showActionStatus("Select transfer origin and destination first");
                return;
            }
            if (!transferSimulatorState.hasPreview() || transferSimulatorState.previewTof() <= 0.0) {
                showActionStatus("No valid transfer trajectory");
                /// TODO: LOCALLIZE
                return;
            }
            InterplanetaryTransferJob transfer = transferSupport.createTransferJob(
                root,
                transferSimulatorState.originBody(),
                transferSimulatorState.destinationBody(),
                transferSimulatorState.originBody()
                    .displayName() + " -> "
                    + transferSimulatorState.destinationBody()
                        .displayName(),
                "Simulation",
                clock.time(),
                transferSimulatorState.previewTof());
            if (transfer == null) {
                showActionStatus("Transfer failed");
                return;
            }
            clientSimulatedTransferState.addTransfer(transfer);
            showActionStatus("Transfer dispatched");
        }

        private void runTransferPlannerStressTest() {
            if (!transferSimulatorState.isOpen()) return;
            if (viewRoot.objectClass() != CelestialObject.Class.STAR) {
                showActionStatus("Open a star system first");
                return;
            }

            long startNanos = System.nanoTime();
            InterplanetaryTransferSystem.LambertStressReport report = InterplanetaryTransferSystem
                .runLambertStress(root, viewRoot, clock.time(), 1000, 500.0);
            long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;

            if (!report.hasEnoughPlanets()) {
                showActionStatus("Stress: need at least 2 planets in this system (" + elapsedMs + " ms)");
                return;
            }

            if (!report.hasSuccesses()) {
                showActionStatus(
                    "Stress: " + report.executedSimulations()
                        + " runs, 0 solved within 500 dV in "
                        + elapsedMs
                        + " ms\n"
                        + formatStressBenchmarkBreakdown(report));
                return;
            }

            if (report.hasTrajectoryFailures()) {
                showActionStatus(
                    "Stress: " + report.trajectoryFailures()
                        + " trajectory failures / "
                        + report.successfulTransfers()
                        + " solved in "
                        + elapsedMs
                        + "ms avg="
                        + formatDecimal1(elapsedMs / (double) Math.max(1, report.executedSimulations()))
                        + "ms/run\n"
                        + formatStressBenchmarkBreakdown(report));
                return;
            }

            showActionStatus(
                "Stress: ok " + report.successfulTransfers()
                    + "/"
                    + report.executedSimulations()
                    + " avg dV="
                    + formatDecimal1(report.averageTotalDv())
                    + " best dV="
                    + formatDecimal1(report.bestTotalDv())
                    + " worst dV="
                    + formatDecimal1(report.worstTotalDv())
                    + " time="
                    + elapsedMs
                    + "ms avg="
                    + formatDecimal1(elapsedMs / (double) Math.max(1, report.executedSimulations()))
                    + "ms/run\n"
                    + formatStressBenchmarkBreakdown(report));
        }

        private void drawAssetIcon(CelestialAsset.Kind kind, int x, int y, int size, float alpha) {
            sceneRenderer.drawAssetIcon(kind, x, y, size, alpha);
        }

        private void drawActionStatusMessage() {
            if (actionStatusMessage == null || actionStatusMessage.isEmpty()) return;
            if (System.currentTimeMillis() > actionStatusExpiresAt) {
                actionStatusMessage = "";
                return;
            }
            // TODO: COLOR
            String[] lines = actionStatusMessage.split("\\R");
            for (int i = 0; i < lines.length; i++) {
                Minecraft.getMinecraft().fontRenderer.drawStringWithShadow(lines[i], 12, 36 + i * 11, 0xFFD9E0FF);
            }
        }

        private void drawViewStatusLabel(CelestialObject viewRoot, int widgetWidth) {
            if (viewRoot == null) return;
            String title = viewRoot.objectClass() == CelestialObject.Class.GALAXY ? viewRoot.displayName()
                : viewRoot.objectClass() == CelestialObject.Class.STAR ? viewRoot.displayName() + " System" : null;
            if (title == null) return;

            String statusText = "Zoom: x" + formatCompactDecimal(getDisplayZoomMultiplier(), 3);

            Minecraft mc = Minecraft.getMinecraft();
            int titleWidth = mc.fontRenderer.getStringWidth(title);
            int statusX = Math.round(widgetWidth / 2f + titleWidth / 2f + 68f);
            int statusY = 18;
            mc.fontRenderer.drawStringWithShadow(statusText, statusX, statusY, EnumColors.MapStatusText.getColor());
        }

        private void drawHammerTrajectoryLoadDebug(int widgetWidth) {
            if (!isCreativeModeAvailable()) return;
            HammerTrajectoryLoadSample sample = CelestialClient.hammerTrajectoryLoadSample();
            String text = "route ms/tick own=" + formatDebugMillis(sample.ownMsPerTick())
                + " all="
                + formatDebugMillis(sample.allMsPerTick());

            Minecraft mc = Minecraft.getMinecraft();
            int x = Math.max(12, widgetWidth - mc.fontRenderer.getStringWidth(text) - 12);
            mc.fontRenderer.drawStringWithShadow(text, x, 36, 0xFF9FD7FF);
        }

        private String formatDebugMillis(double value) {
            if (value < 1.0) return String.format(java.util.Locale.ROOT, "%.3f", value);
            if (value < 10.0) return String.format(java.util.Locale.ROOT, "%.2f", value);
            return String.format(java.util.Locale.ROOT, "%.1f", value);
        }

        private String formatCompactDecimal(double value, int maxDecimals) {
            String text = String.format(java.util.Locale.ROOT, "%." + maxDecimals + "f", value);
            int cut = text.length();
            while (cut > 0 && text.charAt(cut - 1) == '0') cut--;
            if (cut > 0 && text.charAt(cut - 1) == '.') cut--;
            return cut <= 0 ? "0" : text.substring(0, cut);
        }

        private void openContextMenu(CelestialObject body, int localMouseX, int localMouseY) {
            if (body == null || body.objectClass() == CelestialObject.Class.GALAXY) {
                closeContextMenu();
                return;
            }
            contextMenuState.open(body, localMouseX, localMouseY);
        }

        private boolean isPointInContextMenu(int localMouseX, int localMouseY) {
            return contextMenuWidget != null && contextMenuWidget.isPointInMenu(localMouseX, localMouseY);
        }

        private void closeContextMenu() {
            contextMenuState.close();
        }

        private boolean shouldRenderBodyAtCurrentZoom(CelestialObject body) {
            if (viewState.isometricProgress > 0.01 || sameBody(body, viewRoot) || sameBody(body, focusedBody))
                return true;
            if (shouldCullAsteroidAtCurrentZoom(body)) return false;
            if (!shouldUseOverlapDeclutter(body)) return true;
            CelestialObject parent = findParent(root, body);
            if (parent == null || parent.objectClass() == CelestialObject.Class.GALAXY) return true;
            // Absolute-position children, including generated asteroid markers,
            // have already been placed in world space and should not be culled by
            // parent orbit separation heuristics.
            if (OrbitalWorldStateCache.usesAbsolutePosition(parent, body)) return true;
            float separation = (float) (body.orbitalParams()
                .perigee() * getScale());
            float minimumSeparation = getRenderedBodyRadius(body) + getRenderedBodyRadius(parent) + 10f;
            return separation >= minimumSeparation;
        }

        private boolean shouldUseOverlapDeclutter(CelestialObject body) {
            return !sameBody(body, root);
        }

        private boolean shouldCullAsteroidAtCurrentZoom(CelestialObject body) {
            if (body == null || !body.isAsteroid()) return false;
            float naturalRadius = getNaturalSpriteRadius(body);
            return shouldCullAsteroidAtNaturalRadius(body, naturalRadius);
        }

        static boolean shouldCullAsteroidAtNaturalRadius(CelestialObject body, float naturalRadius) {
            if (body == null || !body.isAsteroid()) return false;
            return CelestialClient.asteroidProjection(body)
                .map(projection -> AsteroidStarmapProjection.shouldCull(body, projection, naturalRadius))
                .orElse(false);
        }

        private float getNaturalSpriteRadius(CelestialObject body) {
            return getSpriteRadius(body);
        }

        private void showActionStatus(String message) {
            actionStatusMessage = message;
            actionStatusExpiresAt = System.currentTimeMillis() + 2500L;
        }

        private String formatDecimal1(double value) {
            long scaled = Math.round(value * 10.0);
            return (scaled / 10L) + "." + Math.abs(scaled % 10L);
        }

        private String formatDecimal2(double value) {
            long scaled = Math.round(value * 100.0);
            return (scaled / 100L) + "." + Math.abs((scaled / 10L) % 10L) + Math.abs(scaled % 10L);
        }

        private String formatStressBenchmarkBreakdown(InterplanetaryTransferSystem.LambertStressReport report) {
            int runs = Math.max(1, report.executedSimulations());
            return "ms/action: scan=" + formatDecimal2(nanosToMillisPerRun(report.routeScanNanos(), runs))
                + " sample="
                + formatDecimal2(nanosToMillisPerRun(report.trajectorySampleNanos(), runs))
                + " other="
                + formatDecimal2(nanosToMillisPerRun(report.otherNanos(), runs))
                + "\nscan: hoh="
                + formatDecimal2(nanosToMillisPerRun(report.hohmannNanos(), runs))
                + " dep="
                + formatDecimal2(nanosToMillisPerRun(report.departureResolveNanos(), runs))
                + " arr="
                + formatDecimal2(nanosToMillisPerRun(report.arrivalResolveNanos(), runs))
                + " geom="
                + formatDecimal2(nanosToMillisPerRun(report.geometryNanos(), runs))
                + " lam="
                + formatDecimal2(nanosToMillisPerRun(report.lambertNanos(), runs))
                + " acc="
                + formatDecimal2(nanosToMillisPerRun(report.acceptNanos(), runs))
                + " overhead="
                + formatDecimal2(nanosToMillisPerRun(report.scanOverheadNanos(), runs))
                + " cand="
                + report.scanCandidateCount()
                + " lamCalls="
                + report.lambertPairCount() * 2;
        }

        private double nanosToMillisPerRun(long nanos, int runs) {
            return nanos / 1_000_000.0 / Math.max(1, runs);
        }

        private void drawSatelliteMarkerTooltip(OrbitalScene.OrbitalSceneFrame frame, int localMouseX,
            int localMouseY) {
            CelestialObject body = findSatelliteMarkerBodyAt(frame, localMouseX, localMouseY);
            if (body == null) return;
            List<String> lines = new ArrayList<>();
            lines.add(satelliteCountSummary(body, SatelliteKind.COMMUNICATION));
            lines.add(satelliteBandwidthSummary(body));
            lines.add(satelliteCountSummary(body, SatelliteKind.PROSPECTING));
            satelliteScanningSummary(body).ifPresent(lines::add);
            lines.addAll(satellitePendingDataSummaries(body));
            drawTooltip(lines, localMouseX + 10, localMouseY + 10);
        }

        private CelestialObject findSatelliteMarkerBodyAt(OrbitalScene.OrbitalSceneFrame frame, int localMouseX,
            int localMouseY) {
            for (OrbitalScene.SatelliteMarkerBounds bounds : frame.satelliteMarkerBounds) {
                if (bounds.contains(localMouseX, localMouseY)) return bounds.body();
            }
            return null;
        }

        private void drawTooltip(List<String> lines, int x, int y) {
            if (lines == null || lines.isEmpty()) return;
            Minecraft mc = Minecraft.getMinecraft();
            int width = 0;
            for (String line : lines) {
                width = Math.max(width, mc.fontRenderer.getStringWidth(line));
            }
            int padding = 6;
            int lineHeight = 10;
            int tooltipWidth = width + padding * 2;
            int tooltipHeight = padding * 2 + lines.size() * lineHeight;
            int left = Math.min(Math.max(6, x), getArea().width - tooltipWidth - 6);
            int top = Math.min(Math.max(6, y), getArea().height - tooltipHeight - 6);
            Gui.drawRect(left, top, left + tooltipWidth, top + tooltipHeight, EnumColors.MAP_COLOR_MODAL_BG.getColor());
            for (int i = 0; i < lines.size(); i++) {
                mc.fontRenderer.drawStringWithShadow(
                    lines.get(i),
                    left + padding,
                    top + padding + i * lineHeight,
                    EnumColors.MAP_COLOR_TEXT_BODY.getColor());
            }
        }

        private void updateRenameFieldLayout() {
            if (renameField == null) return;
            ButtonRect layout = assetActionsWidget.getRenameInputBounds();
            if (layout == null) {
                renameField.top(-1000);
                if (renameField.isEnabled()) renameField.setEnabled(false);
                return;
            }
            if (!renameField.isEnabled()) renameField.setEnabled(true);
            renameField.left(getArea().x + layout.left())
                .top(getArea().y + layout.top())
                .width(layout.right() - layout.left())
                .height(layout.bottom() - layout.top());
        }

        private ResourceLocation getAssetIconTexture(CelestialAsset.Kind kind) {
            return CelestialMarkerBase.CelestialAssetIcons.get(kind);
        }

        private float getSelectionBoxRadius(OrbitalScene.ScreenBodyBounds bounds) {
            return bounds.renderedRadius() + 4f;
        }

        private boolean isGT5AutomationAvailable() {
            return isGregTech5UnofficialNewHorizonsLoaded();
        }

        private boolean canCreateBaseStation(CelestialObject body) {
            return body != null && body.properties()
                .canCreateStation();
        }

        private boolean canCreateAutomatedStation(CelestialObject body) {
            return canCreateBaseStation(body) && isGT5AutomationAvailable();
        }

        private boolean canCreateAutomatedFacility(CelestialObject body) {
            return body != null && isGT5AutomationAvailable()
                && body.properties()
                    .canCreateOutpost();
        }

        private boolean canDebugSatellites(CelestialObject body) {
            return body != null && body.objectClass() != CelestialObject.Class.GALAXY
                && isCreativeBuildModeEnabled()
                && currentTeamId() != null;
        }

        private void addSatellite(CelestialObject body, SatelliteKind kind) {
            mutateSatellites(body, kind, SatelliteMutationOperation.ADD, 1);
        }

        private void deleteSatellites(CelestialObject body, SatelliteKind kind) {
            mutateSatellites(body, kind, SatelliteMutationOperation.DELETE_ALL, 0);
        }

        private void deleteSatelliteAmount(CelestialObject body, SatelliteKind kind, int amount) {
            if (amount <= 0) return;
            mutateSatellites(body, kind, SatelliteMutationOperation.DELETE_AMOUNT, amount);
        }

        private void mutateSatellites(CelestialObject body, SatelliteKind kind, SatelliteMutationOperation operation,
            int amount) {
            if (body == null || body.objectClass() == CelestialObject.Class.GALAXY || currentTeamId() == null) return;
            if ((operation == SatelliteMutationOperation.ADD || operation == SatelliteMutationOperation.SET)
                && !canDebugSatellites(body)) return;
            if (StarmapActionSyncHandler.sendSatelliteMutation(currentTeamId(), body.key(), kind, operation, amount)) {
                showActionStatus("Satellite request sent");
            }
        }

        private UUID currentTeamId() {
            return GTTeamsCompat.getTeam();
        }

        private int satelliteCount(CelestialObject body, SatelliteKind kind) {
            UUID teamId = currentTeamId();
            if (teamId == null || body == null || kind == null) return 0;
            return CelestialAssetStore.CLIENT.satelliteCount(teamId, body.key(), kind);
        }

        private String satelliteCountSummary(CelestialObject body, SatelliteKind kind) {
            return StatCollector.translateToLocalFormatted(
                "galaxia.satellite.tooltip.count",
                StatCollector.translateToLocal(satelliteKindKey(kind)),
                satelliteCount(body, kind));
        }

        private String satelliteKindKey(SatelliteKind kind) {
            return "galaxia.satellite.kind." + kind.name()
                .toLowerCase(Locale.ROOT);
        }

        private String satelliteBandwidthSummary(CelestialObject body) {
            UUID teamId = currentTeamId();
            SatelliteNetworkState networkState = SatelliteNetworkClientState.current();
            CelestialObjectKey satelliteNetworkBodyKey = body == null ? null : body.key();
            long usedKbps = 0L;
            long capacityKbps = 0L;
            if (teamId != null && satelliteNetworkBodyKey != null && teamId.equals(networkState.teamId())) {
                usedKbps = networkState.usedKbps(satelliteNetworkBodyKey);
                capacityKbps = networkState.capacityKbps(satelliteNetworkBodyKey);
            }
            return "Bandwidth: " + SatelliteBandwidthFormatter.formatKbps(usedKbps)
                + " / "
                + SatelliteBandwidthFormatter.formatKbps(capacityKbps);
        }

        private java.util.Optional<String> satelliteScanningSummary(CelestialObject body) {
            if (satelliteCount(body, SatelliteKind.PROSPECTING) <= 0) return java.util.Optional.empty();
            return CelestialDiscoveryClientState.scan(body.key(), CelestialDiscoveryCapability.PROSPECTING)
                .map(scan -> "Scanning: " + scanProgressPercent(scan) + "%");
        }

        private int scanProgressPercent(CelestialDiscoveryScanSnapshot scan) {
            int durationTicks = scan.step()
                .durationTicks();
            if (durationTicks <= 0) return 100;
            return Math.min(99, Math.max(0, Math.round(scan.elapsedTicks() * 100.0f / durationTicks)));
        }

        private List<String> satellitePendingDataSummaries(CelestialObject body) {
            UUID teamId = currentTeamId();
            SatelliteNetworkState networkState = SatelliteNetworkClientState.current();
            CelestialObjectKey satelliteNetworkBodyKey = body == null ? null : body.key();
            if (teamId == null || satelliteNetworkBodyKey == null || !teamId.equals(networkState.teamId()))
                return List.of();
            return networkState.pendingData(satelliteNetworkBodyKey)
                .stream()
                .map(
                    entry -> SatelliteBandwidthFormatter.formatDataDeciKb(entry.deciKb()) + " "
                        + pendingDataLabel(entry.key())
                        + pendingDataDestinationLabel(entry))
                .toList();
        }

        private String pendingDataDestinationLabel(SatelliteNetworkState.PendingData entry) {
            if (entry.destinationBodyKeys()
                .isEmpty()) return " waiting";
            return " to " + entry.destinationBodyKeys()
                .stream()
                .map(this::bodyDisplayName)
                .collect(Collectors.joining(", "));
        }

        private String bodyDisplayName(CelestialObjectKey bodyKey) {
            return GalaxiaCelestialAPI.findBodyByKey(bodyKey)
                .map(CelestialObject::displayName)
                .orElseGet(
                    () -> bodyKey.isRegistered() ? bodyKey.registeredBodyId()
                        .name()
                        .toLowerCase(Locale.ROOT)
                        .replace('_', ' ')
                        : bodyKey.minorBodyId()
                            .parentBodyId()
                            .name()
                            .toLowerCase(Locale.ROOT)
                            .replace('_', ' ') + " asteroid "
                            + bodyKey.minorBodyId()
                                .index());
        }

        private String bodyDisplayName(CelestialObjectId bodyId) {
            return bodyDisplayName(CelestialObjectKey.registered(bodyId));
        }

        private String pendingDataLabel(SatelliteDataKey key) {
            String type = key.type()
                .name()
                .toLowerCase(Locale.ROOT)
                .replace('_', ' ');
            if (!key.hasOrigin()) return type + " data";
            return bodyDisplayName(key.origin()) + " " + type + " data";
        }

        private float getInteractionRadius(float renderedRadius) {
            return Math.max(5f, renderedRadius);
        }

        private boolean isOnScreen(float sx, float sy, float radius) {
            return sx >= 0 && sy >= 0 && sx <= getArea().width && sy <= getArea().height;
        }

        private float getLabelYOffset(float renderedRadius) {
            return renderedRadius + 6f;
        }

        private CelestialObject getPinnedInfoBody() {
            if (hoveredBody != null && hoveredBody.objectClass() != CelestialObject.Class.GALAXY
                && isVisibleInCurrentLayer(hoveredBody)) return hoveredBody;
            if (focusedBody != null && focusedBody.objectClass() != CelestialObject.Class.GALAXY
                && isVisibleInCurrentLayer(focusedBody)) return focusedBody;
            return null;
        }
    }
}
