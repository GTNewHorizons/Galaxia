package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import static com.gtnewhorizons.galaxia.api.GalaxiaAPI.isGregTechLoaded;

import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.util.ResourceLocation;

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
import com.gtnewhorizons.galaxia.core.profiling.HammerTrajectoryLoadSample;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalMechanics;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalParams;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalTransferPlanner;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.LogisticsDelivery;

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

        private final Map<CelestialObject, BodyWorldState> states = new IdentityHashMap<>();
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
            BodyWorldState state = states.get(body);
            if (state == null) return null;
            return new double[] { state.worldX, state.worldY };
        }

        double[] getWorldVelocity(CelestialObject body) {
            BodyWorldState state = states.get(body);
            if (state == null) return null;
            return new double[] { state.worldVx, state.worldVy };
        }

        CelestialObject getParent(CelestialObject body) {
            BodyWorldState state = states.get(body);
            return state == null ? null : state.parent;
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
            BodyWorldState cachedState = states.get(body);
            if (cachedState == null) {
                cachedState = new BodyWorldState();
                states.put(body, cachedState);
            }
            cachedState.set(parent, worldState.x(), worldState.y(), worldState.vx(), worldState.vy(), rebuildVersion);
            for (CelestialObject child : GalaxiaCelestialAPI.getChildren(body)) {
                OrbitalMechanics.OrbitalState childWorldState = OrbitalMechanics
                    .resolveChildWorldState(body, child, worldState, globalTime);
                populate(child, body, childWorldState, globalTime);
            }
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
        private double globalTime = 0.0;
        private double timeScale = OrbitalTransferPlanner.OSU_PER_SECOND;
        private boolean paused = false;
        private long lastFrameTime = System.currentTimeMillis();
        private double displayOrbitalTimeAnchor = 0.0;
        private double serverOrbitalTimeAnchor = Double.NaN;
        private final InterplanetaryTransferSystem.MutableTransferPoint focusedTransferPoint = new InterplanetaryTransferSystem.MutableTransferPoint();
        private final float[] isoScratchPos = new float[2];
        private CelestialObject focusedBody = null;
        private CelestialObject hoveredBody = null;
        private InterplanetaryTransferJob focusedTransfer = null;
        private boolean isFollowing = false;
        private CelestialObject pendingFocusBody = null;
        private boolean clickCandidate = false;
        private boolean dragEnabledForCurrentPress = false;
        private CelestialObject pressedBodyCandidate = null;
        private boolean debugOverlayEnabled = true;
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
        private final OrbitalScene.OrbitalSceneFrameBuilder sceneFrameBuilder;
        private int lastRenderedLogisticsTaskRevision = Integer.MIN_VALUE;
        private int orbitalClockRevision = Integer.MIN_VALUE;
        private int lastRenderedLogisticsClockRevision = Integer.MIN_VALUE;
        private TextFieldWidget renameField = null;
        private boolean creativeBuildMode = creativeBuildModePersisted;
        private boolean guiActionsRegistered = false;
        private OrbitalLayerTransitionState transitionState = new OrbitalLayerTransitionState();
        private static final double ZOOM_BASE = 1.18;
        private static final double BASE_SCALE = 82.0;
        private static final double SERVER_OSU_PER_SECOND = OrbitalTransferPlanner.OSU_PER_SECOND;
        private static final double LERP_SPEED = 0.045;
        private static final double PENDING_LAYER_CENTER_LERP_SPEED = 0.08;
        private static final double LAYER_SWITCH_LERP_SPEED = 0.036;
        private static final double OVERVIEW_SCREEN_RADIUS = 420.0;
        private static final double ISO_OVERVIEW_SCREEN_RADIUS = 350.0;
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
                        return globalTime;
                    }

                    @Override
                    public double getTimeScale() {
                        return timeScale;
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
                        InterplanetaryTransferSystem.updatePreview(transferSimulatorState, root, globalTime);
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
                        return timeScale;
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
                    public void openAssetActions(CelestialObject body) {
                        assetActionController.openAssetActions(assetUiState, body);
                        assetActionsWidget.markStructureDirty();
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
            if (!creativeBuildMode) transferSimulatorState.close();
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
                    clickCandidate = false;
                    dragging = false;
                    dragEnabledForCurrentPress = false;
                    pressedBodyCandidate = null;
                    return false;
                }
                if (assetUiState.isAssetActionsOpen()) {
                    clickCandidate = false;
                    dragging = false;
                    dragEnabledForCurrentPress = false;
                    pressedBodyCandidate = null;
                    return button == 1;
                }
                if (button == 0 && contextMenuState.isOpen()) {
                    clickCandidate = false;
                    dragging = false;
                    dragEnabledForCurrentPress = false;
                    pressedBodyCandidate = null;
                    if (contextMenuWidget.isPointInMenu(localMouseX, localMouseY)) return false;
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
                    clickCandidate = false;
                    dragging = false;
                    dragEnabledForCurrentPress = false;
                    pressedBodyCandidate = null;
                    closeContextMenu();
                    return true;
                }
                pressedBodyCandidate = findBodyAtLocal(pressMouseX, pressMouseY);
                clickCandidate = pressedBodyCandidate != null;
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
                    clickCandidate = false;
                    dragging = false;
                    dragEnabledForCurrentPress = false;
                    pressedBodyCandidate = null;
                    return false;
                }
                if (assetUiState.isAssetActionsOpen()) {
                    clickCandidate = false;
                    dragging = false;
                    dragEnabledForCurrentPress = false;
                    pressedBodyCandidate = null;
                    return mouseButton == 1;
                }
                if (contextMenuState.isOpen()) {
                    if (mouseButton == 0) {
                        if (contextMenuWidget.isPointInMenu(localMouseX, localMouseY)) {
                            clickCandidate = false;
                            dragging = false;
                            dragEnabledForCurrentPress = false;
                            pressedBodyCandidate = null;
                            return true;
                        }
                        closeContextMenu();
                        clickCandidate = false;
                        dragging = false;
                        dragEnabledForCurrentPress = false;
                        pressedBodyCandidate = null;
                        return true;
                    } else
                        if (mouseButton == 1 && contextMenuWidget.isPointInMenu(localMouseX, localMouseY)) return true;
                }
                if (mouseButton == 1) {
                    CelestialObject clickedBody = findBodyAtLocal(localMouseX, localMouseY);
                    if (clickedBody != null) {
                        openContextMenu(clickedBody, localMouseX, localMouseY);
                        clickCandidate = false;
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
                        clickCandidate = false;
                        dragging = false;
                        dragEnabledForCurrentPress = false;
                        pressedBodyCandidate = null;
                        return true;
                    }
                    if (clickedBody != null) {
                        boolean opensSystemFromGalaxy = viewRoot == root
                            && clickedBody.objectClass() == CelestialObject.Class.STAR
                            && bodySelectionListener != null;
                        if (!opensSystemFromGalaxy) focusOn(clickedBody);
                        if (bodySelectionListener != null) bodySelectionListener.onBodySelected(clickedBody);
                    }
                }
                clickCandidate = false;
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
                double displayTime = captureCurrentDisplayOrbitalTime();
                paused = !paused;
                reanchorOrbitalClock(displayTime);
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
            if (focusedTransfer == null) isFollowing = false;
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
                clickCandidate = false;
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
            lastMouseX = lx;
            lastMouseY = ly;
        }

        private void updateSimulationTime() {
            long now = System.currentTimeMillis();
            double dt = (now - lastFrameTime) / 1000.0;
            lastFrameTime = now;
            if (Minecraft.getMinecraft().theWorld == null) {
                if (!paused) globalTime += dt * timeScale;
                return;
            }
            double serverNow = getServerOrbitalTime();
            if (Double.isNaN(serverOrbitalTimeAnchor)) {
                globalTime = serverNow;
                displayOrbitalTimeAnchor = serverNow;
                serverOrbitalTimeAnchor = serverNow;
                orbitalClockRevision++;
                return;
            }
            globalTime = paused ? displayOrbitalTimeAnchor : mapServerOrbitalTimeToDisplay(serverNow);
        }

        private double captureCurrentDisplayOrbitalTime() {
            if (Minecraft.getMinecraft().theWorld == null) return globalTime;
            double serverNow = getServerOrbitalTime();
            if (Double.isNaN(serverOrbitalTimeAnchor)) return serverNow;
            return paused ? displayOrbitalTimeAnchor : mapServerOrbitalTimeToDisplay(serverNow);
        }

        private double mapServerOrbitalTimeToDisplay(double serverOrbitalTime) {
            if (Double.isNaN(serverOrbitalTimeAnchor)) return serverOrbitalTime;
            return displayOrbitalTimeAnchor
                + (serverOrbitalTime - serverOrbitalTimeAnchor) * (timeScale / SERVER_OSU_PER_SECOND);
        }

        private void reanchorOrbitalClock(double displayTime) {
            globalTime = displayTime;
            if (Minecraft.getMinecraft().theWorld == null) return;
            double serverNow = getServerOrbitalTime();
            displayOrbitalTimeAnchor = displayTime;
            serverOrbitalTimeAnchor = serverNow;
            orbitalClockRevision++;
        }

        private double getScale() {
            return BASE_SCALE * Math.pow(ZOOM_BASE, viewState.zoomLevel);
        }

        private double getScaleForZoomLevel(double zoomLevel) {
            return BASE_SCALE * Math.pow(ZOOM_BASE, zoomLevel);
        }

        private double getDisplayZoomMultiplier() {
            CelestialObject referenceBody = viewRoot != null ? viewRoot : root;
            if (referenceBody == null) return 1.0;
            double referenceScale = getScaleForZoomLevel(getOverviewZoomForBody(referenceBody));
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
            float spriteSize = getDisplaySpriteSize(body);
            if (spriteSize > 0.0001f) {
                float radius = spriteSize * (MAP_ICON_BASE_SCALE + (float) getScale() * MAP_ICON_ZOOM_SCALE);
                return Math.max(2.0f, radius);
            }
            return 2f;
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
            boolean goIso = shouldUseIsometricOverview(body);
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
                for (CelestialObject child : GalaxiaCelestialAPI.getChildren(body)) {
                    double[] pos = getAbsoluteWorldPos(child);
                    if (pos == null) continue;
                    maxDistance = Math.max(maxDistance, Math.hypot(pos[0], pos[1]));
                }
                return maxDistance;
            }
            double maxSize = 0.0;
            for (CelestialObject child : GalaxiaCelestialAPI.getChildren(body)) maxSize = Math.max(
                maxSize,
                child.orbitalParams()
                    .apogee());
            return maxSize;
        }

        private boolean shouldUseIsometricOverview(CelestialObject body) {
            return body.objectClass() != CelestialObject.Class.GALAXY
                && body.objectClass() != CelestialObject.Class.STAR;
        }

        private double calculateFocusedOrbitExtent(CelestialObject body) {
            CelestialObject parent = findParent(root, body);
            if (parent == null) return 0.0;
            double maxApogee = 0.0;
            for (CelestialObject sibling : GalaxiaCelestialAPI.getChildren(parent)) maxApogee = Math.max(
                maxApogee,
                sibling.orbitalParams()
                    .apogee());
            return maxApogee;
        }

        private double computeOverviewZoom(CelestialObject body, boolean goIso) {
            double extent = goIso ? calculateFocusedOrbitExtent(body) : calculateOverviewExtent(body);
            double screenRadius = goIso ? ISO_OVERVIEW_SCREEN_RADIUS : OVERVIEW_SCREEN_RADIUS;
            return extent > 1e-9 ? zoomForWorldDistance(extent, screenRadius) : goIso ? 3.0 : -0.8;
        }

        private double clampZoom(double zoom) {
            return Math.max(-7000.0, Math.min(14000.0, zoom));
        }

        private double zoomForWorldDistance(double worldDistance, double screenDistance) {
            if (worldDistance <= 1e-9 || screenDistance <= 1e-9) return -0.8;
            return clampZoom(Math.log((screenDistance / worldDistance) / BASE_SCALE) / Math.log(ZOOM_BASE));
        }

        private double getViewportHalfDiagonal() {
            double width = getArea().width > 0 ? getArea().width : 960.0;
            double height = getArea().height > 0 ? getArea().height : 640.0;
            return Math.hypot(width * 0.5, height * 0.5);
        }

        private double getViewportMinDimension() {
            double width = getArea().width > 0 ? getArea().width : 960.0;
            double height = getArea().height > 0 ? getArea().height : 640.0;
            return Math.min(width, height);
        }

        private double getOverviewZoomForBody(CelestialObject body) {
            return computeOverviewZoom(body, shouldUseIsometricOverview(body));
        }

        private double getSystemDepartureZoom(CelestialObject star) {
            double farthestOrbit = calculateOverviewExtent(star);
            return zoomForWorldDistance(farthestOrbit * SYSTEM_DEPARTURE_EXTENT_MULTIPLIER, OVERVIEW_SCREEN_RADIUS);
        }

        private double getNearestOtherStarDistance(CelestialObject anchorStar) {
            return nearestOtherStarDistance(
                anchorStar,
                GalaxiaCelestialAPI.getChildren(root),
                this::getAbsoluteWorldPos);
        }

        static double nearestOtherStarDistance(CelestialObject anchorStar, Collection<CelestialObject> galaxyBodies,
            Function<CelestialObject, double[]> worldPositionProvider) {
            if (anchorStar == null || galaxyBodies == null || worldPositionProvider == null) return Double.MAX_VALUE;
            double[] anchorPos = worldPositionProvider.apply(anchorStar);
            if (anchorPos == null) return Double.MAX_VALUE;
            double nearestDistance = Double.MAX_VALUE;
            for (CelestialObject body : galaxyBodies) {
                if (body == anchorStar || body.objectClass() != CelestialObject.Class.STAR) continue;
                double[] bodyPos = worldPositionProvider.apply(body);
                if (bodyPos == null) continue;
                nearestDistance = Math
                    .min(nearestDistance, Math.hypot(bodyPos[0] - anchorPos[0], bodyPos[1] - anchorPos[1]));
            }
            return nearestDistance;
        }

        private double getGalaxyOverviewZoom(CelestialObject anchorStar) {
            double nearestDistance = getNearestOtherStarDistance(anchorStar);
            if (nearestDistance == Double.MAX_VALUE || nearestDistance <= 1e-9) return getOverviewZoomForBody(root);
            return zoomForWorldDistance(nearestDistance, getViewportMinDimension() * 0.2);
        }

        private double getGalaxyCutZoom(CelestialObject anchorStar) {
            double nearestDistance = getNearestOtherStarDistance(anchorStar);
            if (nearestDistance == Double.MAX_VALUE || nearestDistance <= 1e-9)
                return getGalaxyOverviewZoom(anchorStar);
            return zoomForWorldDistance(nearestDistance, getViewportHalfDiagonal() * 1.5);
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
            worldStateCache.ensure(root, globalTime);
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
            if (focusedBody == null || focusedBody == root) {
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
            if (body == parent) {
                out[0] = cx - ISO_OFFSET;
                out[1] = cy;
                return;
            }
            if (body == focusedBody) {
                out[0] = cx;
                out[1] = cy;
                return;
            }
            List<CelestialObject> children = GalaxiaCelestialAPI.getChildren(focusedBody);
            int index = children.indexOf(body);
            if (index >= 0) {
                out[0] = cx + ISO_OFFSET + index * ISO_SPACING;
                out[1] = cy;
                return;
            }
            out[0] = -1000f;
            out[1] = -1000f;
        }

        private boolean isImportantInIsoMode(CelestialObject body) {
            if (focusedBody == null || focusedBody == root) return true;
            CelestialObject parent = findParent(root, focusedBody);
            if (parent == null) return false;
            return body == parent || body == focusedBody
                || GalaxiaCelestialAPI.getChildren(focusedBody)
                    .contains(body);
        }

        private boolean shouldTraverseChildren(CelestialObject body) {
            return viewRoot != root || body == root;
        }

        private boolean isVisibleInCurrentLayer(CelestialObject body) {
            return isDescendantOrSelf(viewRoot, body);
        }

        private boolean isTransferEndpointRendered(CelestialObject body) {
            return body != null && isVisibleInCurrentLayer(body) && shouldRenderBodyAtCurrentZoom(body);
        }

        private boolean isDescendantOrSelf(CelestialObject ancestor, CelestialObject target) {
            if (ancestor == target) return true;
            for (CelestialObject child : GalaxiaCelestialAPI.getChildren(ancestor))
                if (isDescendantOrSelf(child, target)) return true;
            return false;
        }

        @Override
        public void drawBackground(ModularGuiContext context, WidgetThemeEntry widgetTheme) {
            updateSimulationTime();
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
                    .writeCurrentTransferPoint(focusedTransfer, globalTime, focusedTransferPoint)
                    && focusedTransferPoint.valid()
                    && !focusedTransfer.isFinished(globalTime)) {
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
                }
            }
            Gui.drawRect(0, 0, getArea().width, getArea().height, EnumColors.MapBackground.getColor());
            GlStateManager.pushMatrix();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GlStateManager.disableTexture2D();
            GL11.glEnable(GL11.GL_LINE_SMOOTH);
            float labelAlpha = (float) Math.max(0.0, 1.0 - viewState.isometricProgress * 2.5);
            sceneFrame = sceneFrameBuilder.buildInto(sceneFrame, viewRoot, globalTime, labelAlpha);
            syncRenderedLogisticsTransfers();
            if (transferSimulatorState.isOpen() && !transferSimulatorState.isWaitingForPick()
                && viewRoot.objectClass() == CelestialObject.Class.STAR) {
                InterplanetaryTransferSystem.updatePreview(transferSimulatorState, root, globalTime);
            }
            if (!transfersHidden) {
                transferRenderer.drawTransferPaths(
                    transferState,
                    viewRoot,
                    globalTime,
                    viewRoot.objectClass() == CelestialObject.Class.STAR
                        ? (float) Math.max(0.0, 1.0 - viewState.isometricProgress * 2.5)
                        : 0f);
                transferRenderer.drawTransferPaths(
                    clientSimulatedTransferState,
                    viewRoot,
                    globalTime,
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
            if (!transfersHidden) {
                transferRenderer.drawTransferDots(
                    transferState,
                    viewRoot,
                    globalTime,
                    viewRoot.objectClass() == CelestialObject.Class.STAR
                        ? (float) Math.max(0.0, 1.0 - viewState.isometricProgress * 2.5)
                        : 0f);
                transferRenderer.drawTransferDots(
                    clientSimulatedTransferState,
                    viewRoot,
                    globalTime,
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
                InterplanetaryTransferJob hoveredSimulatedTransfer = transferRenderer
                    .findHoveredTransfer(clientSimulatedTransferState, viewRoot, globalTime, localMouseX, localMouseY);
                clientSimulatedTransferState.updateHoveredTransfer(hoveredSimulatedTransfer, localMouseX, localMouseY);
                transferState.updateHoveredTransfer(
                    hoveredSimulatedTransfer == null
                        ? transferRenderer
                            .findHoveredTransfer(transferState, viewRoot, globalTime, localMouseX, localMouseY)
                        : null,
                    localMouseX,
                    localMouseY);
            }
            hoveredBody = dragging ? null : findBodyAtLocal(localMouseX, localMouseY);
            if (hoveredBody != null && hoveredBody.objectClass() == CelestialObject.Class.GALAXY) hoveredBody = null;
            if (hoveredBody != null && isVisibleInCurrentLayer(hoveredBody)) {
                if (hoveredBody != focusedBody) sceneRenderer.drawHoverHighlight(hoveredBody, sceneFrame);
            }
            if (focusedBody != null && focusedBody.objectClass() != CelestialObject.Class.GALAXY
                && isVisibleInCurrentLayer(focusedBody)) sceneRenderer.drawSelectionHighlight(focusedBody, sceneFrame);
            if (debugOverlayEnabled) sceneRenderer.drawDebugOverlay(sceneFrame, getArea().height);
            super.drawBackground(context, widgetTheme);
        }

        private void fillResolvedBodyDrawState(OrbitalScene.ResolvedBodyDrawState out, CelestialObject body,
            CelestialObject parent, double worldX, double worldY, float labelAlpha) {
            fillIsometricScreenPos(body, isoScratchPos);
            float screenX = snapToPixel(
                (float) lerp(worldToScreenX(worldX), isoScratchPos[0], viewState.isometricProgress));
            float screenY = snapToPixel(
                (float) lerp(worldToScreenY(worldY), isoScratchPos[1], viewState.isometricProgress));
            float bodyAlpha = getBodyRenderAlpha(body);
            float renderedRadius = getRenderedBodyRadius(body);
            boolean renderBody = shouldRenderBodyAtCurrentZoom(body);
            boolean drawLabel = false;
            float labelY = 0f;
            int labelColor = 0;
            if (labelAlpha > 0.02f && body != root && body != focusedBody && renderBody) {
                float actualLabelAlpha = getLabelRenderAlpha(body, labelAlpha);
                if (actualLabelAlpha > 0.01f) {
                    drawLabel = true;
                    labelY = screenY + getLabelYOffset(renderedRadius);
                    labelColor = withAlpha(EnumColors.MapCelestialLabelText.getColor(), actualLabelAlpha);
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
                .findHoveredTransfer(clientSimulatedTransferState, viewRoot, globalTime, mouseX, mouseY);
            return simulatedTransfer == null
                ? transferRenderer.findHoveredTransfer(transferState, viewRoot, globalTime, mouseX, mouseY)
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
                globalTime);
            if (transfer == null) {
                showActionStatus("Transfer failed");
                return;
            }
            transferState.addTransfer(transfer);
            showActionStatus("Transfer dispatched");
        }

        private double getServerOrbitalTime() {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.theWorld == null) return globalTime;
            double partialTicks = RenderTickState.getLastPartialTicks();
            return (mc.theWorld.getTotalWorldTime() + partialTicks) * OrbitalTransferPlanner.OSU_PER_TICK;
        }

        private void syncRenderedLogisticsTransfers() {
            int revision = CelestialClient.clientDeliveryRevision();
            if (revision == lastRenderedLogisticsTaskRevision
                && orbitalClockRevision == lastRenderedLogisticsClockRevision) return;

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
            lastRenderedLogisticsClockRevision = orbitalClockRevision;
        }

        private InterplanetaryTransferJob buildRenderedLogisticsTransfer(LogisticsDelivery delivery) {
            if (delivery == null || delivery.data.resourceId() == null) return null;
            CelestialObject sourceBody = GalaxiaCelestialAPI.findBodyById(root, delivery.data.fromBodyId());
            CelestialObject destinationBody = GalaxiaCelestialAPI.findBodyById(root, delivery.data.toBodyId());
            if (sourceBody == null || destinationBody == null) return null;

            String itemName = delivery.data.resourceId()
                .toStack(1)
                .getDisplayName();
            String summary = delivery.data.amount() + " x " + itemName;
            double departureDisplayTime = mapServerOrbitalTimeToDisplay(delivery.data.departureOrbitalTime());
            double arrivalDisplayTime = mapServerOrbitalTimeToDisplay(
                delivery.data.departureOrbitalTime() + delivery.data.tofOrbitalSeconds());
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
                globalTime,
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
                .runLambertStress(root, viewRoot, globalTime, 1000, 500.0);
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

        private void closeContextMenu() {
            contextMenuState.close();
        }

        private boolean shouldRenderBodyAtCurrentZoom(CelestialObject body) {
            if (viewState.isometricProgress > 0.01 || body == viewRoot || body == focusedBody) return true;
            if (!shouldUseOverlapDeclutter(body)) return true;
            CelestialObject parent = findParent(root, body);
            if (parent == null || parent.objectClass() == CelestialObject.Class.GALAXY) return true;
            if (OrbitalWorldStateCache.usesAbsolutePosition(parent, body)) return true;
            float separation = (float) (body.orbitalParams()
                .perigee() * getScale());
            float minimumSeparation = getRenderedBodyRadius(body) + getRenderedBodyRadius(parent) + 10f;
            return separation >= minimumSeparation;
        }

        private boolean shouldUseOverlapDeclutter(CelestialObject body) {
            return body != root;
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
            return isGregTechLoaded();
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

        private float getInteractionRadius(CelestialObject body) {
            return getInteractionRadius(getRenderedBodyRadius(body));
        }

        private float getInteractionRadius(float renderedRadius) {
            return Math.max(5f, renderedRadius);
        }

        private boolean isOnScreen(float sx, float sy, float radius) {
            return sx >= 0 && sy >= 0 && sx <= getArea().width && sy <= getArea().height;
        }

        private float getLabelYOffset(CelestialObject body) {
            return getLabelYOffset(getRenderedBodyRadius(body));
        }

        private float getLabelYOffset(float renderedRadius) {
            return renderedRadius + 6f;
        }

        private static int withAlpha(int color, float alpha) {
            int a = Math.max(0, Math.min(255, (int) (((color >> 24) & 0xFF) * alpha)));
            return (color & 0x00FFFFFF) | (a << 24);
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
