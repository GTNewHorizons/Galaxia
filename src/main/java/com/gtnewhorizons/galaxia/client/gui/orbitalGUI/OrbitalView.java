package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

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
import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.galaxia.client.EnumTextures;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.orbitalGUI.Hierarchy;
import com.gtnewhorizons.galaxia.orbitalGUI.Hierarchy.OrbitalCelestialBody;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetKind;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetLocation;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialManagedAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectClass;
import com.gtnewhorizons.galaxia.utility.EnumColors;

public class OrbitalView {

    @Desugar
    record OrbitalLayerTransitionState(OrbitalCelestialBody pendingTarget, OrbitalCelestialBody pendingAnchor,
        double pendingStartZoom, double pendingTargetZoom, Phase phase, OrbitalCelestialBody activeTarget,
        OrbitalCelestialBody activeAnchor, double activeStartZoom, double activeTargetZoom, float activeStartSpriteSize,
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

        OrbitalLayerTransitionState beginPending(OrbitalCelestialBody target, OrbitalCelestialBody anchor,
            double startZoom, double targetZoom) {
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

        OrbitalLayerTransitionState beginActive(Phase nextPhase, OrbitalCelestialBody target,
            OrbitalCelestialBody anchor, double startZoom, double targetZoom, float startSpriteSize,
            float targetSpriteSize) {
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

        private OrbitalCelestialBody body;
        private int x, y;

        boolean isOpen() {
            return body != null;
        }

        OrbitalCelestialBody body() {
            return body;
        }

        int x() {
            return x;
        }

        int y() {
            return y;
        }

        void open(OrbitalCelestialBody body, int x, int y) {
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

        private final Map<OrbitalCelestialBody, BodyWorldState> states = new IdentityHashMap<>();
        private double cachedTime = Double.NaN;

        void ensure(OrbitalCelestialBody root, double globalTime) {
            if (root == null) {
                states.clear();
                cachedTime = Double.NaN;
                return;
            }
            if (!states.isEmpty() && Double.compare(cachedTime, globalTime) == 0) return;
            rebuild(root, globalTime);
        }

        double[] getWorldPosition(OrbitalCelestialBody body) {
            BodyWorldState state = states.get(body);
            if (state == null) return null;
            return new double[] { state.worldX, state.worldY };
        }

        OrbitalCelestialBody getParent(OrbitalCelestialBody body) {
            BodyWorldState state = states.get(body);
            return state == null ? null : state.parent;
        }

        private void rebuild(OrbitalCelestialBody root, double globalTime) {
            states.clear();
            populate(root, null, 0.0, 0.0, globalTime);
            cachedTime = globalTime;
        }

        private void populate(OrbitalCelestialBody body, OrbitalCelestialBody parent, double worldX, double worldY,
            double globalTime) {
            states.put(body, new BodyWorldState(parent, worldX, worldY));
            for (OrbitalCelestialBody child : body.children()) {
                double[] childWorldPos = resolveChildWorldPos(body, child, worldX, worldY, globalTime);
                populate(child, body, childWorldPos[0], childWorldPos[1], globalTime);
            }
        }

        static boolean usesAbsolutePosition(OrbitalCelestialBody parent, OrbitalCelestialBody child) {
            return parent != null && parent.objectClass() == CelestialObjectClass.GALAXY
                && child.absolutePosition() != null;
        }

        static double[] resolveChildWorldPos(OrbitalCelestialBody parent, OrbitalCelestialBody child, double parentWX,
            double parentWY, double globalTime) {
            if (usesAbsolutePosition(parent, child)) {
                Hierarchy.AbsolutePosition absolute = child.absolutePosition();
                return new double[] { absolute.x(), absolute.y() };
            }
            double[] local = calculatePosition(child.orbitalParams(), globalTime);
            return new double[] { parentWX + local[0], parentWY + local[1] };
        }

        static double[] calculatePosition(Hierarchy.OrbitalParams p, double t) {
            double a = p.semiMajorAxis();
            if (a < 1e-8) return new double[] { 0.0, 0.0 };
            double n = p.orbitSpeed() > 0 ? p.orbitSpeed() : 0.42 * Math.pow(a, -1.5);
            double M = p.meanAnomalyAtEpoch() + n * t;
            double e = p.eccentricity();
            double E = M;
            for (int i = 0; i < 8; i++) E = M + e * Math.sin(E);
            double nu = 2.0
                * Math.atan2(Math.sqrt(1.0 + e) * Math.sin(E / 2.0), Math.sqrt(1.0 - e) * Math.cos(E / 2.0));
            double r = a * (1.0 - e * e) / (1.0 + e * Math.cos(nu));
            double ag = nu + p.argumentOfPeriapsis();
            return new double[] { r * Math.cos(ag), r * Math.sin(ag) };
        }

        @Desugar
        private record BodyWorldState(OrbitalCelestialBody parent, double worldX, double worldY) {}
    }

    public static class OrbitalMapWidget extends Widget<OrbitalMapWidget> {

        @FunctionalInterface
        public interface BodySelectionListener {

            void onBodySelected(OrbitalCelestialBody body);
        }

        private final OrbitalCelestialBody root;
        private OrbitalCelestialBody viewRoot;
        private OrbitalCelestialBody initialLayer;
        private BodySelectionListener bodySelectionListener;
        private OrbitalScene.OrbitalSceneFrame sceneFrame = new OrbitalScene.OrbitalSceneFrame();
        private final OrbitalViewState viewState = new OrbitalViewState(-0.8);
        private final OrbitalWorldStateCache worldStateCache = new OrbitalWorldStateCache();
        private boolean dragging = false;
        private double lastMouseX, lastMouseY;
        private double globalTime = 0.0;
        private double timeScale = 42.0;
        private boolean paused = false;
        private long lastFrameTime = System.currentTimeMillis();
        private OrbitalCelestialBody focusedBody = null;
        private OrbitalCelestialBody hoveredBody = null;
        private boolean isFollowing = false;
        private OrbitalCelestialBody pendingFocusBody = null;
        private boolean clickCandidate = false;
        private boolean dragEnabledForCurrentPress = false;
        private OrbitalCelestialBody pressedBodyCandidate = null;
        private boolean debugOverlayEnabled = true;
        private int pressMouseX, pressMouseY;
        private final OrbitalContextMenuState contextMenuState = new OrbitalContextMenuState();
        private String actionStatusMessage = "";
        private long actionStatusExpiresAt = 0L;
        private final AssetManagementSystem.OrbitalAssetSupport assetSupport = new AssetManagementSystem.OrbitalAssetSupport();
        private final AssetManagementSystem.OrbitalAssetActionController assetActionController;
        private final AssetManagementSystem.OrbitalAssetUiState assetUiState = new AssetManagementSystem.OrbitalAssetUiState();
        private final AssetManagementSystem.OrbitalAssetManagementWidget assetManagementWidget;
        private final OrbitalScene.OrbitalSceneRenderer sceneRenderer;
        private final OrbitalPinnedInfoContentBuilder pinnedInfoContentBuilder = new OrbitalPinnedInfoContentBuilder();
        private final OrbitalPinnedInfoContentBuilder.OrbitalPinnedInfoWidget pinnedInfoWidget;
        private final OrbitalContextMenuWidget contextMenuWidget;
        private final OrbitalScene.OrbitalSceneFrameBuilder sceneFrameBuilder;
        private TextFieldWidget renameField = null;
        private boolean creativeBuildMode = false;
        private boolean guiActionsRegistered = false;
        private OrbitalLayerTransitionState transitionState = new OrbitalLayerTransitionState();
        private static final double ZOOM_BASE = 1.18;
        private static final double BASE_SCALE = 82.0;
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

        public OrbitalMapWidget(OrbitalCelestialBody root) {
            this.root = root;
            this.viewRoot = root;
            this.initialLayer = root;
            this.assetActionController = new AssetManagementSystem.OrbitalAssetActionController(
                assetSupport,
                new AssetManagementSystem.OrbitalAssetActionController.Callbacks() {

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
                });
            this.assetManagementWidget = new AssetManagementSystem.OrbitalAssetManagementWidget(
                assetUiState,
                new AssetManagementSystem.OrbitalAssetManagementWidget.Callbacks() {

                    @Override
                    public void closeAssetManagement() {
                        assetActionController.closeAssetManagement(assetUiState);
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
                    public boolean canCreateBaseStation(OrbitalCelestialBody body) {
                        return OrbitalMapWidget.this.canCreateBaseStation(body);
                    }

                    @Override
                    public boolean canCreateAutomatedStation(OrbitalCelestialBody body) {
                        return OrbitalMapWidget.this.canCreateAutomatedStation(body);
                    }

                    @Override
                    public boolean canCreateAutomatedOutpost(OrbitalCelestialBody body) {
                        return OrbitalMapWidget.this.canCreateAutomatedOutpost(body);
                    }

                    @Override
                    public boolean hasStoredConstructionResources(CelestialManagedAsset asset) {
                        return assetSupport.hasStoredConstructionResources(asset);
                    }

                    @Override
                    public boolean isManageableStationAsset(CelestialManagedAsset asset) {
                        return assetSupport.isManageableStationAsset(asset);
                    }

                    @Override
                    public String formatAssetDisplayName(CelestialManagedAsset asset) {
                        return assetSupport.formatAssetDisplayName(asset);
                    }

                    @Override
                    public String buildConstructionInventorySummary(CelestialManagedAsset asset) {
                        return assetSupport.buildConstructionInventorySummary(asset);
                    }

                    @Override
                    public String formatAssetKind(CelestialAssetKind kind) {
                        return assetSupport.formatAssetKind(kind);
                    }

                    @Override
                    public String formatAssetLocation(CelestialAssetLocation location) {
                        return assetSupport.formatAssetLocation(location);
                    }

                    @Override
                    public void drawAssetIcon(CelestialAssetKind kind, int x, int y, int size, float alpha) {
                        OrbitalMapWidget.this.drawAssetIcon(kind, x, y, size, alpha);
                    }

                    @Override
                    public void createBaseStation(OrbitalCelestialBody body) {
                        assetActionController.createBaseStation(body);
                    }

                    @Override
                    public void triggerAssetCreation(OrbitalCelestialBody body, CelestialAssetKind kind,
                        boolean openManagementFirst) {
                        assetActionController.triggerAssetCreation(assetUiState, body, kind, openManagementFirst);
                    }

                    @Override
                    public void openPendingAssetRename(CelestialManagedAsset asset) {
                        assetActionController.openPendingAssetRename(assetUiState, asset);
                    }

                    @Override
                    public void openPendingConstructionCancellation(CelestialManagedAsset asset) {
                        assetActionController.openPendingConstructionCancellation(assetUiState, asset);
                    }

                    @Override
                    public void openPendingResourceTransfer(CelestialManagedAsset asset) {
                        assetActionController.openPendingResourceTransfer(assetUiState, root, asset);
                    }

                    @Override
                    public void openPendingAssetManagement(CelestialManagedAsset asset) {
                        assetActionController.openPendingAssetManagement(assetUiState, asset);
                    }

                    @Override
                    public void openPendingAssetDestruction(CelestialManagedAsset asset) {
                        assetActionController.openPendingAssetDestruction(assetUiState, asset);
                    }

                    @Override
                    public void confirmPendingAssetCreation() {
                        assetActionController.confirmPendingAssetCreation(assetUiState);
                    }

                    @Override
                    public void dismissPendingAssetCreation() {
                        assetActionController.dismissPendingAssetCreation(assetUiState);
                    }

                    @Override
                    public void closePendingAssetRename() {
                        assetActionController.closePendingAssetRename(assetUiState);
                    }

                    @Override
                    public void confirmPendingAssetRename() {
                        assetActionController.confirmPendingAssetRename(assetUiState);
                    }

                    @Override
                    public void dismissPendingAssetDestruction() {
                        assetActionController.dismissPendingAssetDestruction(assetUiState);
                    }

                    @Override
                    public void advancePendingAssetDestruction() {
                        assetActionController.advancePendingAssetDestruction(assetUiState);
                    }

                    @Override
                    public void dismissPendingConstructionCancellation() {
                        assetActionController.dismissPendingConstructionCancellation(assetUiState);
                    }

                    @Override
                    public void confirmPendingConstructionCancellation() {
                        assetActionController.confirmPendingConstructionCancellation(assetUiState);
                    }

                    @Override
                    public void dismissPendingResourceTransfer() {
                        assetActionController.dismissPendingResourceTransfer(assetUiState);
                    }

                    @Override
                    public void sendPendingResourceTransfer(StationTransferTarget target) {
                        assetActionController.sendPendingResourceTransfer(assetUiState, target);
                    }

                    @Override
                    public void closePendingAssetManagement() {
                        assetActionController.closePendingAssetManagement(assetUiState);
                    }

                    @Override
                    public void dismissPendingModalByOutsideClick() {
                        assetActionController.dismissPendingModalByOutsideClick(assetUiState);
                    }

                    @Override
                    public void showActionStatus(String message) {
                        OrbitalMapWidget.this.showActionStatus(message);
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
                    public ResourceLocation getRenderTexture(OrbitalCelestialBody body) {
                        return OrbitalMapWidget.this.getRenderTexture(body);
                    }

                    @Override
                    public float getDisplaySpriteSize(OrbitalCelestialBody body) {
                        return OrbitalMapWidget.this.getDisplaySpriteSize(body);
                    }

                    @Override
                    public float getSelectionBoxRadius(OrbitalScene.ScreenBodyBounds bounds) {
                        return OrbitalMapWidget.this.getSelectionBoxRadius(bounds);
                    }

                    @Override
                    public ResourceLocation getAssetIconTexture(CelestialAssetKind kind) {
                        return OrbitalMapWidget.this.getAssetIconTexture(kind);
                    }
                });
            this.pinnedInfoWidget = new OrbitalPinnedInfoContentBuilder.OrbitalPinnedInfoWidget(
                new OrbitalPinnedInfoContentBuilder.OrbitalPinnedInfoWidget.Callbacks() {

                    @Override
                    public OrbitalCelestialBody getPinnedInfoBody() {
                        return OrbitalMapWidget.this.getPinnedInfoBody();
                    }

                    @Override
                    public List<PinnedInfoRow> buildRows(OrbitalCelestialBody body) {
                        return pinnedInfoContentBuilder.buildRows(body);
                    }
                });
            this.contextMenuWidget = new OrbitalContextMenuWidget(
                contextMenuState,
                new OrbitalContextMenuWidget.Callbacks() {

                    @Override
                    public boolean canCreateBaseStation(OrbitalCelestialBody body) {
                        return OrbitalMapWidget.this.canCreateBaseStation(body);
                    }

                    @Override
                    public boolean canCreateAutomatedStation(OrbitalCelestialBody body) {
                        return OrbitalMapWidget.this.canCreateAutomatedStation(body);
                    }

                    @Override
                    public boolean canCreateAutomatedOutpost(OrbitalCelestialBody body) {
                        return OrbitalMapWidget.this.canCreateAutomatedOutpost(body);
                    }

                    @Override
                    public void openAssetManagement(OrbitalCelestialBody body) {
                        assetActionController.openAssetManagement(assetUiState, body);
                    }

                    @Override
                    public void createBaseStation(OrbitalCelestialBody body) {
                        assetActionController.createBaseStation(body);
                    }

                    @Override
                    public void triggerAssetCreation(OrbitalCelestialBody body, CelestialAssetKind kind,
                        boolean openManagementFirst) {
                        assetActionController.triggerAssetCreation(assetUiState, body, kind, openManagementFirst);
                    }

                    @Override
                    public void closeContextMenu() {
                        OrbitalMapWidget.this.closeContextMenu();
                    }
                });
            this.sceneFrameBuilder = new OrbitalScene.OrbitalSceneFrameBuilder(
                new OrbitalScene.OrbitalSceneFrameBuilder.Callbacks() {

                    @Override
                    public double[] getViewOrigin(OrbitalCelestialBody viewRoot) {
                        return OrbitalMapWidget.this.getAbsoluteWorldPos(viewRoot);
                    }

                    @Override
                    public OrbitalScene.ResolvedBodyDrawState resolveBodyDrawState(OrbitalCelestialBody body,
                        OrbitalCelestialBody parent, double worldX, double worldY, float labelAlpha) {
                        return OrbitalMapWidget.this.resolveBodyDrawState(body, parent, worldX, worldY, labelAlpha);
                    }

                    @Override
                    public boolean shouldTraverseChildren(OrbitalCelestialBody body) {
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
        }

        public OrbitalMapWidget withInitialLayer(OrbitalCelestialBody layerRoot) {
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

        public AssetManagementSystem.OrbitalAssetManagementWidget createAssetManagementWidget() {
            return assetManagementWidget;
        }

        public OrbitalPinnedInfoContentBuilder.OrbitalPinnedInfoWidget createPinnedInfoWidget() {
            return pinnedInfoWidget;
        }

        public OrbitalContextMenuWidget createContextMenuWidget() {
            return contextMenuWidget;
        }

        public void showLayer(OrbitalCelestialBody layerRoot) {
            OrbitalCelestialBody targetLayer = layerRoot == null ? root : layerRoot;
            if (this.viewRoot == targetLayer) return;
            clearLayerSwitchState();
            closeContextMenu();
            assetActionController.closeAssetManagement(assetUiState);
            OrbitalCelestialBody anchorBody = null;
            if (this.viewRoot == root && targetLayer.objectClass() == CelestialObjectClass.STAR)
                anchorBody = targetLayer;
            else if (this.viewRoot.objectClass() == CelestialObjectClass.STAR && targetLayer == root)
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

        public OrbitalCelestialBody getViewRoot() {
            return viewRoot;
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
                return;
            }
            creativeBuildMode = !creativeBuildMode;
            showActionStatus("Creative build mode " + (creativeBuildMode ? "enabled" : "disabled"));
        }

        @Override
        public void onInit() {
            super.onInit();
            OrbitalCelestialBody startingLayer = initialLayer == null ? root : initialLayer;
            resetForLayer(startingLayer);
            this.viewRoot = startingLayer;
            setFocusImmediately(startingLayer);
            viewState.syncToTargets();
            if (guiActionsRegistered) return;
            guiActionsRegistered = true;
            listenGuiAction(
                (IGuiAction.MouseScroll) (direction,
                    amount) -> handleMouseWheel(direction, getContext().getMouseX(), getContext().getMouseY()));
            listenGuiAction((IGuiAction.MousePressed) button -> {
                if (assetUiState.isAssetManagementOpen()) {
                    clickCandidate = false;
                    dragging = false;
                    dragEnabledForCurrentPress = false;
                    pressedBodyCandidate = null;
                    return button == 1;
                }
                if (button == 0 && contextMenuState.isOpen()) {
                    int localMouseX = toLocalMouseX(getContext().getMouseX());
                    int localMouseY = toLocalMouseY(getContext().getMouseY());
                    clickCandidate = false;
                    dragging = false;
                    dragEnabledForCurrentPress = false;
                    pressedBodyCandidate = null;
                    if (contextMenuWidget.isPointInMenu(localMouseX, localMouseY)) return false;
                    closeContextMenu();
                    return true;
                }
                if (button != 0) return false;
                pressMouseX = getContext().getMouseX();
                pressMouseY = getContext().getMouseY();
                lastMouseX = toLocalMouseX(pressMouseX);
                lastMouseY = toLocalMouseY(pressMouseY);
                pressedBodyCandidate = findBodyAtScreen(pressMouseX, pressMouseY);
                clickCandidate = pressedBodyCandidate != null;
                dragEnabledForCurrentPress = pressedBodyCandidate == null;
                dragging = false;
                return false;
            });
            listenGuiAction(
                (IGuiAction.MouseDrag) (mouseButton,
                    time) -> handleMouseDragged(getContext().getMouseX(), getContext().getMouseY(), mouseButton, time));
            listenGuiAction((IGuiAction.MouseReleased) mouseButton -> {
                int localMouseX = toLocalMouseX(getContext().getMouseX());
                int localMouseY = toLocalMouseY(getContext().getMouseY());
                if (assetUiState.isAssetManagementOpen()) {
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
                    OrbitalCelestialBody clickedBody = findBodyAtScreen(
                        getContext().getMouseX(),
                        getContext().getMouseY());
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
                    OrbitalCelestialBody clickedBody = pressedBodyCandidate;
                    if (clickedBody == null)
                        clickedBody = findBodyAtScreen(getContext().getMouseX(), getContext().getMouseY());
                    if (clickedBody != null) {
                        boolean opensSystemFromGalaxy = viewRoot == root
                            && clickedBody.objectClass() == CelestialObjectClass.STAR
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
                paused = !paused;
                return true;
            }
            if (ch == '+' || ch == '=') {
                timeScale = Math.min(timeScale * 1.35, 800_000.0);
                return true;
            }
            if (ch == '-') {
                timeScale = Math.max(timeScale / 1.35, 0.01);
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
            if (assetUiState.isAssetManagementOpen()) {
                if (assetUiState.hasBlockingModal()) return true;
                return !assetManagementWidget.isPointInScrollViewport(toLocalMouseX(mx), toLocalMouseY(my));
            }
            double oldScale = getScale();
            viewState.zoomLevel = Math.max(-7000.0, Math.min(14000.0, viewState.zoomLevel + sign * 0.78));
            int lx = toLocalMouseX(mx);
            int ly = toLocalMouseY(my);
            double wmx = viewState.cameraX + (lx - getArea().width / 2.0) / oldScale;
            double wmy = viewState.cameraY + (ly - getArea().height / 2.0) / oldScale;
            double newScale = getScale();
            viewState.cameraX = wmx - (lx - getArea().width / 2.0) / newScale;
            viewState.cameraY = wmy - (ly - getArea().height / 2.0) / newScale;
            viewState.targetCameraX = viewState.cameraX;
            viewState.targetCameraY = viewState.cameraY;
            viewState.targetZoomLevel = viewState.zoomLevel;
            isFollowing = false;
            return true;
        }

        private boolean handleMouseDragged(int mx, int my, int button, long time) {
            if (button != 0) return false;
            if (assetUiState.isAssetManagementOpen()) return false;
            return true;
        }

        private void updateManualDragging() {
            if (assetUiState.isAssetManagementOpen() || transitionState.hasPending() || isLayerSwitchActive()) return;
            if (!Mouse.isButtonDown(0)) return;
            if (!dragEnabledForCurrentPress) return;
            int mx = getContext().getMouseX();
            int my = getContext().getMouseY();
            int lx = toLocalMouseX(mx);
            int ly = toLocalMouseY(my);
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
            lastMouseX = lx;
            lastMouseY = ly;
        }

        private void updateSimulationTime() {
            long now = System.currentTimeMillis();
            double dt = (now - lastFrameTime) / 1000.0;
            lastFrameTime = now;
            if (!paused && !(focusedBody != null && viewState.targetIsometricProgress > 0.5))
                globalTime += dt * timeScale;
        }

        private double getScale() {
            return BASE_SCALE * Math.pow(ZOOM_BASE, viewState.zoomLevel);
        }

        private float worldToScreenX(double wx) {
            return (float) ((wx - viewState.cameraX) * getScale() + getArea().width / 2.0);
        }

        private float worldToScreenY(double wy) {
            return (float) ((wy - viewState.cameraY) * getScale() + getArea().height / 2.0);
        }

        private int toLocalMouseX(int mouseX) {
            return mouseX - getArea().rx;
        }

        private int toLocalMouseY(int mouseY) {
            return mouseY - getArea().ry;
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

        private float getCubeSizeForBody(OrbitalCelestialBody body) {
            if (focusedBody == null) return body.spriteSize() <= 0.0001f ? ISO_BASE_CUBE_SIZE
                : (float) (ISO_BASE_CUBE_SIZE * Math.sqrt(body.spriteSize()));
            double focusSize = focusedBody.spriteSize();
            if (focusSize <= 0.0001) return body.spriteSize() <= 0.0001f ? ISO_BASE_CUBE_SIZE
                : (float) (ISO_BASE_CUBE_SIZE * Math.sqrt(body.spriteSize()));
            double scale = body.spriteSize() / focusSize;
            return (float) (ISO_BASE_CUBE_SIZE * scale);
        }

        private float getSpriteRadius(OrbitalCelestialBody body) {
            float spriteSize = getDisplaySpriteSize(body);
            if (spriteSize > 0.0001f) {
                float radius = spriteSize * (MAP_ICON_BASE_SCALE + (float) getScale() * MAP_ICON_ZOOM_SCALE);
                return Math.max(2.0f, radius);
            }
            return 2f;
        }

        private float getDisplaySpriteSize(OrbitalCelestialBody body) {
            if (body == null) return 0f;
            float systemSize = (float) body.spriteSize();
            float galaxySize = GALAXY_MAP_STAR_SPRITE_SIZE;
            if (body == transitionState.pendingAnchor() && transitionState.hasPending()
                && body.objectClass() == CelestialObjectClass.STAR) {
                if (transitionState.pendingTarget() == root) {
                    float progress = getTransitionProgress(
                        viewState.zoomLevel,
                        transitionState.pendingStartZoom(),
                        transitionState.pendingTargetZoom());
                    return lerp(systemSize, galaxySize, progress);
                }
                return galaxySize;
            }
            if (body == transitionState.activeAnchor() && body.objectClass() == CelestialObjectClass.STAR
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
            if (viewRoot == root && body.objectClass() == CelestialObjectClass.STAR) {
                OrbitalCelestialBody parent = findParent(root, body);
                if (parent != null && parent.objectClass() == CelestialObjectClass.GALAXY) return galaxySize;
            }
            return systemSize;
        }

        private float getTransitionProgress(double current, double start, double end) {
            double delta = end - start;
            if (Math.abs(delta) < 1e-9) return 1.0f;
            return (float) Math.max(0.0, Math.min(1.0, (current - start) / delta));
        }

        private float getRenderedBodyRadius(OrbitalCelestialBody body) {
            if (getRenderTexture(body) != null && getDisplaySpriteSize(body) > 0.0001f) {
                float spriteR = getSpriteRadius(body);
                float cubeR = getCubeSizeForBody(body) * 0.5f;
                return lerp(spriteR, cubeR, (float) viewState.isometricProgress);
            }
            return body == viewRoot ? 11f : 7f;
        }

        private ResourceLocation getRenderTexture(OrbitalCelestialBody body) {
            if (body == null || body.objectClass() == CelestialObjectClass.GALAXY) return null;
            ResourceLocation texture = body.texture();
            if (isMapBodyIcon(texture)) return texture;
            return EnumTextures.ICON_EGORA.get();
        }

        private boolean isMapBodyIcon(ResourceLocation texture) {
            return texture != null && texture.getResourcePath() != null
                && texture.getResourcePath()
                    .contains("textures/gui/bodyicons/");
        }

        public void focusOn(OrbitalCelestialBody body) {
            if (body == null) return;
            if (viewState.isometricProgress < 0.01) setFocusImmediately(body);
            else {
                pendingFocusBody = body;
                viewState.targetIsometricProgress = 0.0;
            }
        }

        private void centerOnBody(OrbitalCelestialBody body) {
            if (body == null) return;
            focusedBody = body;
            isFollowing = true;
            double[] pos = getAbsoluteWorldPos(body);
            if (pos != null) {
                viewState.targetCameraX = pos[0];
                viewState.targetCameraY = pos[1];
            }
            viewState.targetIsometricProgress = 0.0;
        }

        private void applyLayerSwitch(OrbitalCelestialBody targetLayer, OrbitalCelestialBody focusBody) {
            this.viewRoot = targetLayer == null ? root : targetLayer;
            focusOn(focusBody == null ? this.viewRoot : focusBody);
        }

        private void setFocusImmediately(OrbitalCelestialBody body) {
            focusedBody = body;
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

        private void resetForLayer(OrbitalCelestialBody layerRoot) {
            isFollowing = false;
            focusedBody = null;
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

        private double calculateOverviewExtent(OrbitalCelestialBody body) {
            if (body.objectClass() == CelestialObjectClass.GALAXY) {
                double maxDistance = 0.0;
                for (OrbitalCelestialBody child : body.children()) {
                    double[] pos = getAbsoluteWorldPos(child);
                    if (pos == null) continue;
                    maxDistance = Math.max(maxDistance, Math.hypot(pos[0], pos[1]));
                }
                return maxDistance;
            }
            double maxSize = 0.0;
            for (OrbitalCelestialBody child : body.children()) maxSize = Math.max(
                maxSize,
                child.orbitalParams()
                    .apogee());
            return maxSize;
        }

        private boolean shouldUseIsometricOverview(OrbitalCelestialBody body) {
            return body.objectClass() != CelestialObjectClass.GALAXY && body.objectClass() != CelestialObjectClass.STAR;
        }

        private double calculateFocusedOrbitExtent(OrbitalCelestialBody body) {
            OrbitalCelestialBody parent = findParent(root, body);
            if (parent == null) return 0.0;
            double maxApogee = 0.0;
            for (OrbitalCelestialBody sibling : parent.children()) maxApogee = Math.max(
                maxApogee,
                sibling.orbitalParams()
                    .apogee());
            return maxApogee;
        }

        private double computeOverviewZoom(OrbitalCelestialBody body, boolean goIso) {
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

        private double getOverviewZoomForBody(OrbitalCelestialBody body) {
            return computeOverviewZoom(body, shouldUseIsometricOverview(body));
        }

        private double getSystemDepartureZoom(OrbitalCelestialBody star) {
            double farthestOrbit = calculateOverviewExtent(star);
            return zoomForWorldDistance(farthestOrbit * SYSTEM_DEPARTURE_EXTENT_MULTIPLIER, OVERVIEW_SCREEN_RADIUS);
        }

        private double getNearestOtherStarDistance(OrbitalCelestialBody anchorStar) {
            double[] anchorPos = getAbsoluteWorldPos(anchorStar);
            if (anchorPos == null) return Double.MAX_VALUE;
            double nearestDistance = Double.MAX_VALUE;
            for (OrbitalCelestialBody child : root.children()) {
                if (child == anchorStar || child.objectClass() != CelestialObjectClass.STAR) continue;
                double[] childPos = getAbsoluteWorldPos(child);
                if (childPos == null) continue;
                nearestDistance = Math
                    .min(nearestDistance, Math.hypot(childPos[0] - anchorPos[0], childPos[1] - anchorPos[1]));
            }
            return nearestDistance;
        }

        private double getGalaxyOverviewZoom(OrbitalCelestialBody anchorStar) {
            double nearestDistance = getNearestOtherStarDistance(anchorStar);
            if (nearestDistance == Double.MAX_VALUE || nearestDistance <= 1e-9) return getOverviewZoomForBody(root);
            return zoomForWorldDistance(nearestDistance, getViewportMinDimension() * 0.2);
        }

        private double getGalaxyCutZoom(OrbitalCelestialBody anchorStar) {
            double nearestDistance = getNearestOtherStarDistance(anchorStar);
            if (nearestDistance == Double.MAX_VALUE || nearestDistance <= 1e-9)
                return getGalaxyOverviewZoom(anchorStar);
            return zoomForWorldDistance(nearestDistance, getViewportHalfDiagonal() * 1.2);
        }

        private boolean isLayerSwitchActive() {
            return transitionState.isActive();
        }

        private void clearLayerSwitchState() {
            transitionState = transitionState.clear();
        }

        private void startLayerSwitchTransition(OrbitalCelestialBody targetLayer, OrbitalCelestialBody anchorBody,
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

        private double[] getAbsoluteWorldPos(OrbitalCelestialBody target) {
            ensureWorldStateCache();
            return worldStateCache.getWorldPosition(target);
        }

        private OrbitalCelestialBody findParent(OrbitalCelestialBody cur, OrbitalCelestialBody target) {
            if (cur != root) return null;
            ensureWorldStateCache();
            return worldStateCache.getParent(target);
        }

        private float[] getIsometricScreenPos(OrbitalCelestialBody body) {
            float cx = getArea().width / 2f;
            float cy = getArea().height / 2f + ISO_Y_OFFSET;
            if (focusedBody == null || focusedBody == root) return new float[] { cx, cy };
            OrbitalCelestialBody parent = findParent(root, focusedBody);
            if (parent == null) return new float[] { cx, cy };
            if (body == parent) return new float[] { cx - ISO_OFFSET, cy };
            if (body == focusedBody) return new float[] { cx, cy };
            List<OrbitalCelestialBody> children = focusedBody.children();
            int index = children.indexOf(body);
            if (index >= 0) return new float[] { cx + ISO_OFFSET + index * ISO_SPACING, cy };
            return new float[] { -1000f, -1000f };
        }

        private boolean isImportantInIsoMode(OrbitalCelestialBody body) {
            if (focusedBody == null || focusedBody == root) return true;
            OrbitalCelestialBody parent = findParent(root, focusedBody);
            if (parent == null) return false;
            return body == parent || body == focusedBody
                || focusedBody.children()
                    .contains(body);
        }

        private boolean shouldTraverseChildren(OrbitalCelestialBody body) {
            return viewRoot != root || body == root;
        }

        private boolean isVisibleInCurrentLayer(OrbitalCelestialBody body) {
            return isDescendantOrSelf(viewRoot, body);
        }

        private boolean isDescendantOrSelf(OrbitalCelestialBody ancestor, OrbitalCelestialBody target) {
            if (ancestor == target) return true;
            for (OrbitalCelestialBody child : ancestor.children()) if (isDescendantOrSelf(child, target)) return true;
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
                OrbitalCelestialBody targetLayer = transitionState.pendingTarget();
                OrbitalCelestialBody anchorBody = transitionState.pendingAnchor();
                float currentAnchorSpriteSize = getDisplaySpriteSize(anchorBody);
                transitionState = transitionState.clearPending();
                startLayerSwitchTransition(targetLayer, anchorBody, currentAnchorSpriteSize);
            }
            updateLayerSwitchTransition();
            ensureWorldStateCache();
            if (isFollowing && focusedBody != null) {
                double[] pos = getAbsoluteWorldPos(focusedBody);
                if (pos != null) {
                    viewState.targetCameraX = pos[0];
                    viewState.targetCameraY = pos[1];
                }
            }
            super.drawBackground(context, widgetTheme);
            Gui.drawRect(0, 0, getArea().width, getArea().height, EnumColors.MapBackground.getColor());
            GlStateManager.pushMatrix();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GlStateManager.disableTexture2D();
            GL11.glEnable(GL11.GL_LINE_SMOOTH);
            float labelAlpha = (float) Math.max(0.0, 1.0 - viewState.isometricProgress * 2.5);
            sceneFrame = sceneFrameBuilder.build(viewRoot, globalTime, labelAlpha);
            sceneRenderer.drawOrbits(sceneFrame, (float) Math.max(0.0, 1.0 - viewState.isometricProgress * 2.5));
            GL11.glDisable(GL11.GL_LINE_SMOOTH);
            GL11.glLineWidth(1f);
            GlStateManager.enableTexture2D();
            sceneRenderer.drawBodies(sceneFrame, viewRoot);
            if (labelAlpha > 0.02f) GlStateManager.color(1f, 1f, 1f, 1f);
            GlStateManager.enableTexture2D();
            GlStateManager.color(1f, 1f, 1f, 1f);
            GlStateManager.popMatrix();
            sceneRenderer.drawCollectedLabels(sceneFrame);
            sceneRenderer.drawCollectedMarkers(sceneFrame);
            String speedText = paused ? StatCollector.translateToLocal("galaxia.gui.orbital.paused")
                : StatCollector.translateToLocalFormatted("galaxia.gui.orbital.speed_multiplier", timeScale);
            Minecraft.getMinecraft().fontRenderer.drawStringWithShadow(
                StatCollector.translateToLocalFormatted("galaxia.gui.orbital.status", getScale(), speedText),
                12,
                12,
                EnumColors.MapStatusText.getColor());
            drawActionStatusMessage();
            sceneRenderer.drawViewTitleBanner(viewRoot, getArea().width);
            hoveredBody = dragging ? null : findBodyAtLocal(getContext().getMouseX(), getContext().getMouseY());
            if (hoveredBody != null && hoveredBody.objectClass() == CelestialObjectClass.GALAXY) hoveredBody = null;
            if (hoveredBody != null && isVisibleInCurrentLayer(hoveredBody)) {
                if (hoveredBody != focusedBody) sceneRenderer.drawHoverHighlight(hoveredBody, sceneFrame);
            }
            if (focusedBody != null && focusedBody.objectClass() != CelestialObjectClass.GALAXY
                && isVisibleInCurrentLayer(focusedBody)) sceneRenderer.drawSelectionHighlight(focusedBody, sceneFrame);
            if (debugOverlayEnabled) sceneRenderer.drawDebugOverlay(sceneFrame, getArea().height);
        }

        private OrbitalScene.ResolvedBodyDrawState resolveBodyDrawState(OrbitalCelestialBody body,
            OrbitalCelestialBody parent, double worldX, double worldY, float labelAlpha) {
            float[] isoPos = getIsometricScreenPos(body);
            float screenX = snapToPixel((float) lerp(worldToScreenX(worldX), isoPos[0], viewState.isometricProgress));
            float screenY = snapToPixel((float) lerp(worldToScreenY(worldY), isoPos[1], viewState.isometricProgress));
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
            return new OrbitalScene.ResolvedBodyDrawState(
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

        private float getBodyRenderAlpha(OrbitalCelestialBody body) {
            if (viewState.isometricProgress < 0.01) return 1f;
            if (isImportantInIsoMode(body)) return 1f;
            return (float) Math.max(0.0, 1.0 - viewState.isometricProgress * 3.0);
        }

        private float getLabelRenderAlpha(OrbitalCelestialBody body, float labelAlpha) {
            if (viewState.isometricProgress < 0.01 || isImportantInIsoMode(body)) return labelAlpha;
            return labelAlpha * (float) Math.max(0.0, 1.0 - viewState.isometricProgress * 3.0);
        }

        private OrbitalCelestialBody findBodyAtScreen(int mouseX, int mouseY) {
            return findBodyAtLocal(toLocalMouseX(mouseX), toLocalMouseY(mouseY));
        }

        private OrbitalCelestialBody findBodyAtLocal(float localX, float localY) {
            OrbitalCelestialBody best = null;
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

        private void drawAssetIcon(CelestialAssetKind kind, int x, int y, int size, float alpha) {
            sceneRenderer.drawAssetIcon(kind, x, y, size, alpha);
        }

        private void drawActionStatusMessage() {
            if (actionStatusMessage == null || actionStatusMessage.isEmpty()) return;
            if (System.currentTimeMillis() > actionStatusExpiresAt) {
                actionStatusMessage = "";
                return;
            }
            Minecraft.getMinecraft().fontRenderer.drawStringWithShadow(actionStatusMessage, 12, 24, 0xFFD9E0FF);
        }

        private void openContextMenu(OrbitalCelestialBody body, int localMouseX, int localMouseY) {
            if (body == null || body.objectClass() == CelestialObjectClass.GALAXY) {
                closeContextMenu();
                return;
            }
            contextMenuState.open(body, localMouseX, localMouseY);
        }

        private void closeContextMenu() {
            contextMenuState.close();
        }

        private boolean shouldRenderBodyAtCurrentZoom(OrbitalCelestialBody body) {
            if (viewState.isometricProgress > 0.01 || body == viewRoot || body == focusedBody) return true;
            if (!shouldUseOverlapDeclutter(body)) return true;
            OrbitalCelestialBody parent = findParent(root, body);
            if (parent == null || parent.objectClass() == CelestialObjectClass.GALAXY) return true;
            if (OrbitalWorldStateCache.usesAbsolutePosition(parent, body)) return true;
            float separation = (float) (body.orbitalParams()
                .perigee() * getScale());
            float minimumSeparation = getRenderedBodyRadius(body) + getRenderedBodyRadius(parent) + 10f;
            return separation >= minimumSeparation;
        }

        private boolean shouldUseOverlapDeclutter(OrbitalCelestialBody body) {
            return body != root;
        }

        private void showActionStatus(String message) {
            actionStatusMessage = message;
            actionStatusExpiresAt = System.currentTimeMillis() + 2500L;
        }

        private void updateRenameFieldLayout() {
            if (renameField == null) return;
            ButtonRect layout = assetManagementWidget.getRenameInputBounds();
            if (layout == null) {
                renameField.top(-1000);
                return;
            }
            renameField.left(getArea().rx + layout.left())
                .top(getArea().ry + layout.top())
                .width(layout.right() - layout.left())
                .height(layout.bottom() - layout.top());
        }

        private ResourceLocation getAssetIconTexture(CelestialAssetKind kind) {
            return CelestialMarkerBase.CelestialAssetIcons.get(kind);
        }

        private float getSelectionBoxRadius(OrbitalScene.ScreenBodyBounds bounds) {
            return bounds.renderedRadius() + 4f;
        }

        private boolean isGT5AutomationAvailable() {
            return Galaxia.hasGT5U();
        }

        private boolean canCreateBaseStation(OrbitalCelestialBody body) {
            return body != null && body.properties()
                .canCreateStation();
        }

        private boolean canCreateAutomatedStation(OrbitalCelestialBody body) {
            return canCreateBaseStation(body) && isGT5AutomationAvailable();
        }

        private boolean canCreateAutomatedOutpost(OrbitalCelestialBody body) {
            return body != null && isGT5AutomationAvailable()
                && body.properties()
                    .canCreateOutpost();
        }

        private float getInteractionRadius(OrbitalCelestialBody body) {
            return getInteractionRadius(getRenderedBodyRadius(body));
        }

        private float getInteractionRadius(float renderedRadius) {
            return Math.max(5f, renderedRadius);
        }

        private boolean isOnScreen(float sx, float sy, float radius) {
            return sx >= 0 && sy >= 0 && sx <= getArea().width && sy <= getArea().height;
        }

        private float getLabelYOffset(OrbitalCelestialBody body) {
            return getLabelYOffset(getRenderedBodyRadius(body));
        }

        private float getLabelYOffset(float renderedRadius) {
            return renderedRadius + 6f;
        }

        private static int withAlpha(int colour, float alpha) {
            int a = Math.max(0, Math.min(255, (int) (((colour >> 24) & 0xFF) * alpha)));
            return (colour & 0x00FFFFFF) | (a << 24);
        }

        private OrbitalCelestialBody getPinnedInfoBody() {
            if (hoveredBody != null && hoveredBody.objectClass() != CelestialObjectClass.GALAXY
                && isVisibleInCurrentLayer(hoveredBody)) return hoveredBody;
            if (focusedBody != null && focusedBody.objectClass() != CelestialObjectClass.GALAXY
                && isVisibleInCurrentLayer(focusedBody)) return focusedBody;
            return null;
        }
    }
}
