package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import java.util.ArrayList;
import java.util.List;

import com.gtnewhorizons.galaxia.client.EnumTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import com.cleanroommc.modularui.api.UpOrDown;
import com.cleanroommc.modularui.api.widget.IGuiAction;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.utils.GlStateManager;
import com.cleanroommc.modularui.widget.Widget;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.orbitalGUI.Hierarchy.AbsolutePosition;
import com.gtnewhorizons.galaxia.orbitalGUI.Hierarchy.OrbitalCelestialBody;
import com.gtnewhorizons.galaxia.orbitalGUI.Hierarchy.OrbitalParams;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetKind;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetLocation;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetRequirement;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStatus;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialBodyAssetState;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialManagedAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectClass;
import com.gtnewhorizons.galaxia.utility.EnumColors;

public class OrbitalMapWidget extends Widget<OrbitalMapWidget> {

    @FunctionalInterface
    public interface BodySelectionListener {

        void onBodySelected(OrbitalCelestialBody body);
    }

    private final OrbitalCelestialBody root;
    private OrbitalCelestialBody viewRoot;
    private OrbitalCelestialBody initialLayer;
    private BodySelectionListener bodySelectionListener;
    private final List<ScreenBodyBounds> screenBodies = new ArrayList<>();
    private final List<LabelDrawCall> labelDrawCalls = new ArrayList<>();

    private double cameraX = 0, cameraY = 0;
    private double zoomLevel = -0.8;
    private double targetCameraX = 0, targetCameraY = 0;
    private double targetZoomLevel = -0.8;

    private boolean dragging = false;
    private double lastMouseX, lastMouseY;

    private double globalTime = 0.0;
    private double timeScale = 42.0;
    private boolean paused = false;
    private long lastFrameTime = System.currentTimeMillis();

    private OrbitalCelestialBody focusedBody = null;
    private OrbitalCelestialBody hoveredBody = null;
    private boolean isFollowing = false;

    private double isometricProgress = 0.0;
    private double targetIsometricProgress = 0.0;

    private OrbitalCelestialBody pendingFocusBody = null;
    private boolean clickCandidate = false;
    private boolean debugOverlayEnabled = true;
    private int pressMouseX;
    private int pressMouseY;
    private OrbitalCelestialBody contextMenuBody = null;
    private int contextMenuX;
    private int contextMenuY;
    private String actionStatusMessage = "";
    private long actionStatusExpiresAt = 0L;
    private OrbitalCelestialBody assetManagementBody = null;
    private PendingAssetCreation pendingAssetCreation = null;
    private PendingAssetDestruction pendingAssetDestruction = null;
    private PendingConstructionCancellation pendingConstructionCancellation = null;
    private PendingResourceTransfer pendingResourceTransfer = null;

    private static final double ZOOM_BASE = 1.18;
    private static final double BASE_SCALE = 82.0;
    private static final double LERP_SPEED = 0.06;
    private static final double KEPLER_BASE = 0.42;

    private static final float ISO_BASE_CUBE_SIZE = 42f;
    private static final float ISO_SPACING = 90f;
    private static final float ISO_OFFSET = 110f;
    private static final float ISO_Y_OFFSET = 20f;

    private static final double CONVERGE_THRESHOLD = 0.001;
    private static final int CLICK_DRAG_THRESHOLD = 6;
    private static final float MAP_ICON_BASE_SCALE = 18f;
    private static final float MAP_ICON_ZOOM_SCALE = 0.8f;
    private static final float MAP_LABEL_SCALE = 0.82f;
    private static final int GALAXY_TITLE_TOP = 10;
    private static final int GALAXY_TITLE_HEIGHT = 21;

    public OrbitalMapWidget(OrbitalCelestialBody root) {
        this.root = root;
        this.viewRoot = root;
        this.initialLayer = root;
    }

    public OrbitalMapWidget withInitialLayer(OrbitalCelestialBody layerRoot) {
        this.initialLayer = layerRoot == null ? root : layerRoot;
        this.viewRoot = this.initialLayer;
        return this;
    }

    public OrbitalMapWidget setBodySelectionListener(BodySelectionListener listener) {
        this.bodySelectionListener = listener;
        return this;
    }

    public void showLayer(OrbitalCelestialBody layerRoot) {
        OrbitalCelestialBody targetLayer = layerRoot == null ? root : layerRoot;
        this.viewRoot = targetLayer;
        closeContextMenu();
        closeAssetManagement();
        focusOn(targetLayer);
    }

    public OrbitalCelestialBody getViewRoot() {
        return viewRoot;
    }

    @Override
    public void onInit() {
        super.onInit();

        resetForLayer(initialLayer);
        showLayer(initialLayer);

        listenGuiAction(
            (IGuiAction.MouseScroll) (direction, amount) ->
                handleMouseWheel(direction, getContext().getMouseX(), getContext().getMouseY()));

        listenGuiAction((IGuiAction.MousePressed) button -> {
            if (assetManagementBody != null) {
                return true;
            }
            if (button == 0 && contextMenuBody != null) {
                clickCandidate = false;
                dragging = false;
                return true;
            }
            if (button != 0) return false;
            pressMouseX = getContext().getMouseX();
            pressMouseY = getContext().getMouseY();
            clickCandidate = true;
            dragging = false;
            return false;
        });

        listenGuiAction(
            (IGuiAction.MouseDrag) (mouseButton, time) ->
                handleMouseDragged(getContext().getMouseX(), getContext().getMouseY(), mouseButton, time));

        listenGuiAction((IGuiAction.MouseReleased) mouseButton -> {
            int localMouseX = toLocalMouseX(getContext().getMouseX());
            int localMouseY = toLocalMouseY(getContext().getMouseY());

            if (assetManagementBody != null) {
                if (mouseButton == 0 && handleAssetManagementClick(localMouseX, localMouseY)) {
                    clickCandidate = false;
                    dragging = false;
                    return true;
                }
                if (mouseButton == 1) {
                    return true;
                }
            }

            if (contextMenuBody != null) {
                if (mouseButton == 0) {
                    if (handleContextMenuClick(localMouseX, localMouseY)) {
                        clickCandidate = false;
                        dragging = false;
                        return true;
                    }
                    closeContextMenu();
                } else if (mouseButton == 1 && isWithinContextMenu(localMouseX, localMouseY)) {
                    return true;
                }
            }

            if (mouseButton == 1) {
                OrbitalCelestialBody clickedBody = findBodyAtScreen(getContext().getMouseX(), getContext().getMouseY());
                if (clickedBody != null) {
                    openContextMenu(clickedBody, localMouseX, localMouseY);
                    clickCandidate = false;
                    dragging = false;
                    return true;
                }
                closeContextMenu();
                return false;
            }

            if (mouseButton == 0 && clickCandidate) {
                OrbitalCelestialBody clickedBody = findBodyAtScreen(getContext().getMouseX(), getContext().getMouseY());
                if (clickedBody != null) {
                    focusOn(clickedBody);
                    if (bodySelectionListener != null) {
                        bodySelectionListener.onBodySelected(clickedBody);
                    }
                }
            }
            clickCandidate = false;
            dragging = false;
            return false;
        });

        listenGuiAction((IGuiAction.KeyPressed) this::handleKeyPressed);
    }

    private boolean handleKeyPressed(char ch, int keyCode) {
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
        if (assetManagementBody != null) {
            return true;
        }
        int sign = dir.isUp() ? 1 : dir.isDown() ? -1 : 0;
        if (sign == 0) return false;

        double oldScale = getScale();
        zoomLevel = Math.max(-7000.0, Math.min(14000.0, zoomLevel + sign * 0.78));

        int lx = toLocalMouseX(mx);
        int ly = toLocalMouseY(my);
        double wmx = cameraX + (lx - getArea().width / 2.0) / oldScale;
        double wmy = cameraY + (ly - getArea().height / 2.0) / oldScale;

        double newScale = getScale();
        cameraX = wmx - (lx - getArea().width / 2.0) / newScale;
        cameraY = wmy - (ly - getArea().height / 2.0) / newScale;

        targetCameraX = cameraX;
        targetCameraY = cameraY;
        targetZoomLevel = zoomLevel;
        isFollowing = false;
        return true;
    }

    private boolean handleMouseDragged(int mx, int my, int button, long time) {
        if (button != 0) return false;
        if (assetManagementBody != null) return true;
        int lx = toLocalMouseX(mx);
        int ly = toLocalMouseY(my);
        if (Math.abs(mx - pressMouseX) > CLICK_DRAG_THRESHOLD || Math.abs(my - pressMouseY) > CLICK_DRAG_THRESHOLD) {
            clickCandidate = false;
        }
        if (!dragging) {
            dragging = true;
            lastMouseX = lx;
            lastMouseY = ly;
        }
        cameraX -= (lx - lastMouseX) / getScale();
        cameraY -= (ly - lastMouseY) / getScale();
        lastMouseX = lx;
        lastMouseY = ly;
        targetCameraX = cameraX;
        targetCameraY = cameraY;
        isFollowing = false;
        return true;
    }

    private void updateSimulationTime() {
        long now = System.currentTimeMillis();
        double dt = (now - lastFrameTime) / 1000.0;
        lastFrameTime = now;
        if (!paused && !(focusedBody != null && targetIsometricProgress > 0.5)) {
            globalTime += dt * timeScale;
        }
    }

    private double[] calculatePosition(OrbitalParams p, double t) {
        return calculatePositionStatic(p, t);
    }

    private double getScale() {
        return BASE_SCALE * Math.pow(ZOOM_BASE, zoomLevel);
    }

    private static boolean usesAbsolutePosition(OrbitalCelestialBody parent, OrbitalCelestialBody child) {
        return parent != null && parent.objectClass() == CelestialObjectClass.GALAXY && child.absolutePosition() != null;
    }

    private static double[] resolveChildWorldPos(OrbitalCelestialBody parent, OrbitalCelestialBody child, double parentWX,
        double parentWY, double globalTime) {
        if (usesAbsolutePosition(parent, child)) {
            AbsolutePosition absolute = child.absolutePosition();
            return new double[] { absolute.x(), absolute.y() };
        }
        double[] local = calculatePositionStatic(child.orbitalParams(), globalTime);
        return new double[] { parentWX + local[0], parentWY + local[1] };
    }

    private static double[] calculatePositionStatic(OrbitalParams p, double t) {
        double a = p.semiMajorAxis();
        if (a < 1e-8) return new double[] { 0.0, 0.0 };

        double n = p.orbitSpeed() > 0 ? p.orbitSpeed() : KEPLER_BASE * Math.pow(a, -1.5);
        double M = p.meanAnomalyAtEpoch() + n * t;
        double e = p.eccentricity();

        double E = M;
        for (int i = 0; i < 8; i++) E = M + e * Math.sin(E);

        double nu = 2.0 * Math.atan2(Math.sqrt(1.0 + e) * Math.sin(E / 2.0), Math.sqrt(1.0 - e) * Math.cos(E / 2.0));
        double r = a * (1.0 - e * e) / (1.0 + e * Math.cos(nu));
        double ag = nu + p.argumentOfPeriapsis();
        return new double[] { r * Math.cos(ag), r * Math.sin(ag) };
    }

    private float worldToScreenX(double wx) {
        return (float) ((wx - cameraX) * getScale() + getArea().width / 2.0);
    }

    private float worldToScreenY(double wy) {
        return (float) ((wy - cameraY) * getScale() + getArea().height / 2.0);
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
        if (focusedBody == null) {
            if (body.spriteSize() <= 0.0001) {
                return ISO_BASE_CUBE_SIZE;
            }
            return (float) (ISO_BASE_CUBE_SIZE * Math.sqrt(body.spriteSize()));
        }
        double focusSize = focusedBody.spriteSize();
        if (focusSize <= 0.0001) {
            if (body.spriteSize() <= 0.0001) {
                return ISO_BASE_CUBE_SIZE;
            }
            return (float) (ISO_BASE_CUBE_SIZE * Math.sqrt(body.spriteSize()));
        }
        double scale = body.spriteSize() / focusSize;
        return (float) (ISO_BASE_CUBE_SIZE * scale);
    }

    private float getSpriteRadius(OrbitalCelestialBody body) {
        if (body.spriteSize() > 0.0001) {
            float spriteSize = (float) body.spriteSize();
            float radius = spriteSize * (MAP_ICON_BASE_SCALE + (float) getScale() * MAP_ICON_ZOOM_SCALE);
            return Math.max(2.0f, radius);
        }
        return 2f;
    }

    private float getRenderedBodyRadius(OrbitalCelestialBody body) {
        if (getRenderTexture(body) != null && body.spriteSize() > 0.0001) {
            float spriteR = getSpriteRadius(body);
            float cubeR = getCubeSizeForBody(body) * 0.5f;
            return lerp(spriteR, cubeR, (float) isometricProgress);
        }
        return body == viewRoot ? 11f : 7f;
    }

    private ResourceLocation getRenderTexture(OrbitalCelestialBody body) {
        return body.objectClass() == CelestialObjectClass.GALAXY ? null : EnumTextures.ICON_EGORA.get();
    }

    public void focusOn(OrbitalCelestialBody body) {
        if (body == null) return;
        if (isometricProgress < 0.01) {
            setFocusImmediately(body);
        } else {
            pendingFocusBody = body;
            targetIsometricProgress = 0.0;
        }
    }

    private void setFocusImmediately(OrbitalCelestialBody body) {
        focusedBody = body;
        isFollowing = true;

        double[] pos = getAbsoluteWorldPos(body);
        if (pos != null) {
            targetCameraX = pos[0];
            targetCameraY = pos[1];
        }

        boolean goIso = body.objectClass() != CelestialObjectClass.GALAXY && body.objectClass() != CelestialObjectClass.STAR;
        targetIsometricProgress = goIso ? 1.0 : 0.0;

        if (!goIso) {
            double maxSize = calculateOverviewExtent(body);
            targetZoomLevel = maxSize > 1e-9 ? Math.log((420.0 / maxSize) / BASE_SCALE) / Math.log(ZOOM_BASE) : -0.8;
        } else {
            OrbitalCelestialBody parent = findParent(root, body);
            double maxApogee = 0;
            if (parent != null) {
                for (OrbitalCelestialBody c : parent.children()) {
                    maxApogee = Math.max(maxApogee, c.orbitalParams().apogee());
                }
            }
            targetZoomLevel = maxApogee > 1e-9 ? Math.log((350.0 / maxApogee) / BASE_SCALE) / Math.log(ZOOM_BASE) : 3.0;
        }
        targetZoomLevel = Math.max(-7000.0, Math.min(14000.0, targetZoomLevel));
    }

    private void resetForLayer(OrbitalCelestialBody layerRoot) {
        if (layerRoot != root) {
            return;
        }
        cameraX = 0.0;
        cameraY = 0.0;
        targetCameraX = 0.0;
        targetCameraY = 0.0;
        isFollowing = false;
        focusedBody = null;
        isometricProgress = 0.0;
        targetIsometricProgress = 0.0;
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
        for (OrbitalCelestialBody child : body.children()) {
            maxSize = Math.max(maxSize, child.orbitalParams().apogee());
        }
        return maxSize;
    }

    private double[] getAbsoluteWorldPos(OrbitalCelestialBody target) {
        return getAbsoluteWorldPos(root, target, 0.0, 0.0);
    }

    private double[] getAbsoluteWorldPos(OrbitalCelestialBody cur, OrbitalCelestialBody target, double wx, double wy) {
        if (cur == target) return new double[] { wx, wy };
        for (OrbitalCelestialBody child : cur.children()) {
            double[] childWorldPos = resolveChildWorldPos(cur, child, wx, wy, globalTime);
            double[] res = getAbsoluteWorldPos(child, target, childWorldPos[0], childWorldPos[1]);
            if (res != null) return res;
        }
        return null;
    }

    private OrbitalCelestialBody findParent(OrbitalCelestialBody cur, OrbitalCelestialBody target) {
        for (OrbitalCelestialBody child : cur.children()) {
            if (child == target) return cur;
            OrbitalCelestialBody found = findParent(child, target);
            if (found != null) return found;
        }
        return null;
    }

    private float[] getIsometricScreenPos(OrbitalCelestialBody body) {
        float cx = getArea().width / 2f;
        float cy = getArea().height / 2f + ISO_Y_OFFSET;

        if (focusedBody == null || focusedBody == root) {
            return new float[] { cx, cy };
        }

        OrbitalCelestialBody parent = findParent(root, focusedBody);
        if (parent == null) {
            return new float[] { cx, cy };
        }

        if (body == parent) {
            return new float[] { cx - ISO_OFFSET, cy };
        }
        if (body == focusedBody) {
            return new float[] { cx, cy };
        }
        List<OrbitalCelestialBody> children = focusedBody.children();
        int index = children.indexOf(body);
        if (index >= 0) {
            return new float[] { cx + ISO_OFFSET + index * ISO_SPACING, cy };
        }

        return new float[] { -1000f, -1000f };
    }

    private boolean isImportantInIsoMode(OrbitalCelestialBody body) {
        if (focusedBody == null || focusedBody == root) return true;
        OrbitalCelestialBody parent = findParent(root, focusedBody);
        if (parent == null) return false;
        return body == parent || body == focusedBody || focusedBody.children().contains(body);
    }

    private boolean shouldTraverseChildren(OrbitalCelestialBody body) {
        return viewRoot != root || body == root;
    }

    private boolean isVisibleInCurrentLayer(OrbitalCelestialBody body) {
        return isDescendantOrSelf(viewRoot, body);
    }

    private boolean isDescendantOrSelf(OrbitalCelestialBody ancestor, OrbitalCelestialBody target) {
        if (ancestor == target) {
            return true;
        }
        for (OrbitalCelestialBody child : ancestor.children()) {
            if (isDescendantOrSelf(child, target)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void drawBackground(ModularGuiContext context, WidgetThemeEntry widgetTheme) {
        updateSimulationTime();

        cameraX = lerp(cameraX, targetCameraX, LERP_SPEED);
        cameraY = lerp(cameraY, targetCameraY, LERP_SPEED);
        zoomLevel = lerp(zoomLevel, targetZoomLevel, LERP_SPEED);
        isometricProgress = lerp(isometricProgress, targetIsometricProgress, LERP_SPEED);

        if (Math.abs(cameraX - targetCameraX) < CONVERGE_THRESHOLD) cameraX = targetCameraX;
        if (Math.abs(cameraY - targetCameraY) < CONVERGE_THRESHOLD) cameraY = targetCameraY;
        if (Math.abs(zoomLevel - targetZoomLevel) < CONVERGE_THRESHOLD) zoomLevel = targetZoomLevel;
        if (Math.abs(isometricProgress - targetIsometricProgress) < CONVERGE_THRESHOLD)
            isometricProgress = targetIsometricProgress;

        if (pendingFocusBody != null && isometricProgress < 0.01) {
            setFocusImmediately(pendingFocusBody);
            pendingFocusBody = null;
        }

        if (isFollowing && focusedBody != null) {
            double[] pos = getAbsoluteWorldPos(focusedBody);
            if (pos != null) {
                targetCameraX = pos[0];
                targetCameraY = pos[1];
            }
        }

        super.drawBackground(context, widgetTheme);

        Gui.drawRect(0, 0, getArea().width, getArea().height, EnumColors.MapBackground.getColor());

        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        GlStateManager.disableTexture2D();
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        double[] viewOrigin = getAbsoluteWorldPos(viewRoot);
        if (viewOrigin == null) {
            viewOrigin = new double[] { 0.0, 0.0 };
        }
        screenBodies.clear();
        labelDrawCalls.clear();
        drawOrbitsRecursive(viewRoot, viewOrigin[0], viewOrigin[1]);
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(1f);

        GlStateManager.enableTexture2D();
        drawBodiesRecursive(viewRoot, viewOrigin[0], viewOrigin[1]);

        float labelAlpha = (float) Math.max(0.0, 1.0 - isometricProgress * 2.5);
        if (labelAlpha > 0.02f) {
            GlStateManager.color(1f, 1f, 1f, 1f);
            drawLabelsRecursive(viewRoot, viewOrigin[0], viewOrigin[1], labelAlpha);
        }

        GlStateManager.enableTexture2D();
        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.popMatrix();

        drawCollectedLabels();

        String speedText = paused ? StatCollector.translateToLocal("galaxia.gui.orbital.paused")
            : StatCollector.translateToLocalFormatted("galaxia.gui.orbital.speed_multiplier", timeScale);
        Minecraft.getMinecraft().fontRenderer.drawStringWithShadow(
            StatCollector.translateToLocalFormatted("galaxia.gui.orbital.status", getScale(), speedText),
            12,
            12,
            EnumColors.MapStatusText.getColor());
        drawActionStatusMessage();
        drawViewTitleBanner();

        hoveredBody = dragging ? null : findBodyAtLocal(getContext().getMouseX(), getContext().getMouseY());
        if (hoveredBody != null && hoveredBody.objectClass() == CelestialObjectClass.GALAXY) {
            hoveredBody = null;
        }

        if (hoveredBody != null && isVisibleInCurrentLayer(hoveredBody)) {
            if (hoveredBody != focusedBody) {
                drawHoverHighlight(hoveredBody);
            }
        }

        if (focusedBody != null && focusedBody.objectClass() != CelestialObjectClass.GALAXY
            && isVisibleInCurrentLayer(focusedBody)) drawSelectionHighlight(focusedBody);

        if (debugOverlayEnabled) {
            drawDebugOverlay();
        }

        OrbitalCelestialBody infoBody = getPinnedInfoBody();
        if (infoBody != null) {
            drawPinnedInfoPanel(infoBody);
        }

        drawAssetManagementModal();
        drawContextMenu();
    }

    private void drawOrbitsRecursive(OrbitalCelestialBody body, double parentWX, double parentWY) {
        float ellipseAlpha = (float) Math.max(0.0, 1.0 - isometricProgress * 2.5);

        if (!shouldTraverseChildren(body)) {
            return;
        }

        for (OrbitalCelestialBody child : body.children()) {
            if (ellipseAlpha > 0.01f && !usesAbsolutePosition(body, child) && shouldRenderBodyAtCurrentZoom(child)) {
                drawEllipse(child.orbitalParams(), parentWX, parentWY, ellipseAlpha);
            }
            double[] childWorldPos = resolveChildWorldPos(body, child, parentWX, parentWY, globalTime);
            drawOrbitsRecursive(child, childWorldPos[0], childWorldPos[1]);
        }
    }

    private void drawBodiesRecursive(OrbitalCelestialBody body, double parentWX, double parentWY) {
        double wx = parentWX;
        double wy = parentWY;

        float[] isoPos = getIsometricScreenPos(body);
        float sx = snapToPixel((float) lerp(worldToScreenX(wx), isoPos[0], isometricProgress));
        float sy = snapToPixel((float) lerp(worldToScreenY(wy), isoPos[1], isometricProgress));

        float bodyAlpha;
        if (isometricProgress < 0.01) {
            bodyAlpha = 1f;
        } else if (isImportantInIsoMode(body)) {
            bodyAlpha = 1f;
        } else {
            bodyAlpha = (float) Math.max(0.0, 1.0 - isometricProgress * 3.0);
        }

        boolean renderBody = shouldRenderBodyAtCurrentZoom(body);
        if (body.objectClass() != CelestialObjectClass.GALAXY && bodyAlpha > 0.01f && renderBody) {
            ResourceLocation texture = getRenderTexture(body);
            if (texture != null && body.spriteSize() > 0.0001) {
                float bodyRadius = getRenderedBodyRadius(body);
                drawSprite(texture, sx, sy, bodyRadius, bodyAlpha);
            } else {
                int color = getFallbackBodyColor(body.objectClass());
                float radius = body == root ? 11f : 7f;
                drawFilledCircle(sx, sy, radius, color, bodyAlpha);
            }
            registerHitboxes(body, sx, sy);
        }

        if (!shouldTraverseChildren(body)) {
            return;
        }

        for (OrbitalCelestialBody child : body.children()) {
            double[] childWorldPos = resolveChildWorldPos(body, child, wx, wy, globalTime);
            drawBodiesRecursive(child, childWorldPos[0], childWorldPos[1]);
        }
    }

    private void drawLabelsRecursive(OrbitalCelestialBody body, double parentWX, double parentWY, float labelAlpha) {
        double wx = parentWX;
        double wy = parentWY;

        boolean drawLabel = body != root && body != focusedBody;
        if (drawLabel) {
            float[] isoPos = getIsometricScreenPos(body);
            float sx = snapToPixel((float) lerp(worldToScreenX(wx), isoPos[0], isometricProgress));
            float sy = snapToPixel((float) lerp(worldToScreenY(wy), isoPos[1], isometricProgress));

            float actualLabelAlpha = labelAlpha;
            if (isometricProgress >= 0.01 && !isImportantInIsoMode(body)) {
                actualLabelAlpha *= (float) Math.max(0.0, 1.0 - isometricProgress * 3.0);
            }

            if (shouldRenderBodyAtCurrentZoom(body)) {
                float yOffset = getLabelYOffset(body);
                int col = withAlpha(EnumColors.MapCelestialLabelText.getColor(), actualLabelAlpha);
                labelDrawCalls.add(new LabelDrawCall(body.displayName(), sx, sy + yOffset, col));
            }
        }

        if (!shouldTraverseChildren(body)) {
            return;
        }

        for (OrbitalCelestialBody child : body.children()) {
            double[] childWorldPos = resolveChildWorldPos(body, child, wx, wy, globalTime);
            drawLabelsRecursive(child, childWorldPos[0], childWorldPos[1], labelAlpha);
        }
    }

    private OrbitalCelestialBody findBodyAtScreen(int mouseX, int mouseY) {
        return findBodyAtLocal(toLocalMouseX(mouseX), toLocalMouseY(mouseY));
    }

    private OrbitalCelestialBody findBodyAtLocal(float localX, float localY) {
        OrbitalCelestialBody best = null;
        double bestScore = Double.MAX_VALUE;
        for (int i = screenBodies.size() - 1; i >= 0; i--) {
            ScreenBodyBounds bounds = screenBodies.get(i);
            double score = bounds.bodyScore(localX, localY);
            if (score < bestScore) {
                best = bounds.body;
                bestScore = score;
            }
        }
        return best;
    }

    private void registerHitboxes(OrbitalCelestialBody body, float sx, float sy) {
        float renderedRadius = getRenderedBodyRadius(body);
        float interactionRadius = getInteractionRadius(body);
        float maxRadius = Math.max(renderedRadius, interactionRadius);

        if (!isOnScreen(sx, sy, maxRadius)) {
            return;
        }

        screenBodies.add(new ScreenBodyBounds(body, sx, sy, renderedRadius, interactionRadius));
    }

    private ScreenBodyBounds findScreenBodyBounds(OrbitalCelestialBody body) {
        for (int i = screenBodies.size() - 1; i >= 0; i--) {
            ScreenBodyBounds bounds = screenBodies.get(i);
            if (bounds.body == body) {
                return bounds;
            }
        }
        return null;
    }

    private void drawSprite(ResourceLocation tex, float x, float y, float radius, float alpha) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(tex);
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(1f, 1f, 1f, alpha);

        Tessellator tess = Tessellator.instance;
        tess.startDrawingQuads();
        tess.addVertexWithUV(x - radius, y + radius, 0, 0, 1);
        tess.addVertexWithUV(x + radius, y + radius, 0, 1, 1);
        tess.addVertexWithUV(x + radius, y - radius, 0, 1, 0);
        tess.addVertexWithUV(x - radius, y - radius, 0, 0, 0);
        tess.draw();

        GL11.glColor4f(1f, 1f, 1f, 1f);
    }

    private void drawFilledCircle(float x, float y, float r, int colour, float alpha) {
        GlStateManager.disableTexture2D();
        float red = ((colour >> 16) & 0xFF) / 255f;
        float green = ((colour >> 8) & 0xFF) / 255f;
        float blue = (colour & 0xFF) / 255f;
        GlStateManager.color(red, green, blue, alpha);
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glVertex2f(x, y);
        for (int i = 0; i <= 32; i++) {
            double a = i * Math.PI * 2.0 / 32.0;
            GL11.glVertex2f(x + (float) Math.cos(a) * r, y + (float) Math.sin(a) * r);
        }
        GL11.glEnd();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    private void drawCircleOutline(float x, float y, float r, int colour, float alpha, float lineWidth) {
        GlStateManager.disableTexture2D();
        float red = ((colour >> 16) & 0xFF) / 255f;
        float green = ((colour >> 8) & 0xFF) / 255f;
        float blue = (colour & 0xFF) / 255f;
        GlStateManager.color(red, green, blue, alpha);
        GL11.glLineWidth(lineWidth);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        for (int i = 0; i < 48; i++) {
            double a = i * Math.PI * 2.0 / 48.0;
            GL11.glVertex2f(x + (float) Math.cos(a) * r, y + (float) Math.sin(a) * r);
        }
        GL11.glEnd();
        GL11.glLineWidth(1f);
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    private void drawSquareOutline(float x, float y, float halfSize, int colour, float alpha, float lineWidth) {
        GlStateManager.disableTexture2D();
        float red = ((colour >> 16) & 0xFF) / 255f;
        float green = ((colour >> 8) & 0xFF) / 255f;
        float blue = (colour & 0xFF) / 255f;
        GlStateManager.color(red, green, blue, alpha);
        GL11.glLineWidth(lineWidth);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        GL11.glVertex2f(x - halfSize, y - halfSize);
        GL11.glVertex2f(x + halfSize, y - halfSize);
        GL11.glVertex2f(x + halfSize, y + halfSize);
        GL11.glVertex2f(x - halfSize, y + halfSize);
        GL11.glEnd();
        GL11.glLineWidth(1f);
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    private void drawCenteredString(String text, float x, float y, int colour) {
        Minecraft mc = Minecraft.getMinecraft();
        int w = mc.fontRenderer.getStringWidth(text);
        GlStateManager.pushMatrix();
        GlStateManager.scale(MAP_LABEL_SCALE, MAP_LABEL_SCALE, 1f);
        mc.fontRenderer.drawStringWithShadow(
            text,
            Math.round((x / MAP_LABEL_SCALE) - (w / 2f)),
            Math.round(y / MAP_LABEL_SCALE),
            colour);
        GlStateManager.popMatrix();
    }

    private void drawViewTitleBanner() {
        if (viewRoot == null) {
            return;
        }

        String title;
        if (viewRoot.objectClass() == CelestialObjectClass.GALAXY) {
            title = viewRoot.displayName();
        } else if (viewRoot.objectClass() == CelestialObjectClass.STAR) {
            title = viewRoot.displayName() + " System";
        } else {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        int textWidth = mc.fontRenderer.getStringWidth(title);
        float centerX = getArea().width / 2f;
        int top = GALAXY_TITLE_TOP;
        int bottom = top + GALAXY_TITLE_HEIGHT;
        float bottomHalfWidth = Math.max(74f, textWidth / 2f + 28f);
        float topHalfWidth = bottomHalfWidth + 8f;

        drawFilledTrapezoid(centerX, top, bottom, topHalfWidth, bottomHalfWidth, 0xEE162133);
        drawTrapezoidOutline(centerX, top, bottom, topHalfWidth, bottomHalfWidth, 0xFF7FB6FF, 1.4f);
        drawCenteredBannerString(title, centerX, top + 7, 0xFFFFFFFF);
    }

    private void prepareFilledShapeDraw(int colour) {
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_CULL_FACE);
        float red = ((colour >> 16) & 0xFF) / 255f;
        float green = ((colour >> 8) & 0xFF) / 255f;
        float blue = (colour & 0xFF) / 255f;
        float alpha = ((colour >> 24) & 0xFF) / 255f;
        GlStateManager.color(red, green, blue, alpha);
    }

    private void finishFilledShapeDraw() {
        GlStateManager.color(1f, 1f, 1f, 1f);
        GL11.glEnable(GL11.GL_CULL_FACE);
        GlStateManager.enableTexture2D();
    }

    private void drawCenteredBannerString(String text, float x, float y, int colour) {
        Minecraft mc = Minecraft.getMinecraft();
        int w = mc.fontRenderer.getStringWidth(text);
        mc.fontRenderer.drawStringWithShadow(text, Math.round(x - w / 2f), Math.round(y), colour);
    }

    private void drawFilledTrapezoid(float centerX, int top, int bottom, float topHalfWidth, float bottomHalfWidth,
        int colour) {
        prepareFilledShapeDraw(colour);
        for (int y = top; y < bottom; y++) {
            float t = (y - top) / (float) Math.max(1, bottom - top);
            float halfWidth = topHalfWidth + (bottomHalfWidth - topHalfWidth) * t;
            int left = Math.round(centerX - halfWidth);
            int right = Math.round(centerX + halfWidth);
            Gui.drawRect(left, y, right, y + 1, colour);
        }
        finishFilledShapeDraw();
    }

    private void drawTrapezoidOutline(float centerX, int top, int bottom, float topHalfWidth, float bottomHalfWidth,
        int colour, float lineWidth) {
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        float red = ((colour >> 16) & 0xFF) / 255f;
        float green = ((colour >> 8) & 0xFF) / 255f;
        float blue = (colour & 0xFF) / 255f;
        float alpha = ((colour >> 24) & 0xFF) / 255f;
        GlStateManager.color(red, green, blue, alpha);
        GL11.glLineWidth(lineWidth);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        GL11.glVertex2f(centerX - topHalfWidth, top);
        GL11.glVertex2f(centerX + topHalfWidth, top);
        GL11.glVertex2f(centerX + bottomHalfWidth, bottom);
        GL11.glVertex2f(centerX - bottomHalfWidth, bottom);
        GL11.glEnd();
        GL11.glLineWidth(1f);
        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.enableTexture2D();
    }

    private void drawDebugOverlay() {
        Minecraft mc = Minecraft.getMinecraft();
        Gui.drawRect(8, getArea().height - 36, 182, getArea().height - 8, 0x990B111C);
        mc.fontRenderer.drawStringWithShadow("Debug: body hitzones", 14, getArea().height - 30, 0xFF7FFFD4);
        mc.fontRenderer.drawStringWithShadow("Toggle: B", 14, getArea().height - 18, 0xFFB8C7D9);

        for (ScreenBodyBounds bounds : screenBodies) {
            drawSquareOutline(bounds.centerX, bounds.centerY, bounds.interactionRadius, 0xFF00E5FF, 0.95f, 1.5f);
            Gui.drawRect(
                Math.round(bounds.centerX) - 1,
                Math.round(bounds.centerY) - 1,
                Math.round(bounds.centerX) + 1,
                Math.round(bounds.centerY) + 1,
                0xFF9BFF7A);
        }
    }

    private void drawCollectedLabels() {
        for (LabelDrawCall label : labelDrawCalls) {
            drawCenteredString(label.text, label.x, label.y, label.color);
        }
    }

    private void drawEllipse(OrbitalParams p, double parentX, double parentY, float alpha) {
        double a = p.semiMajorAxis();
        double e = p.eccentricity();
        double b = a * Math.sqrt(Math.max(0.0, 1.0 - e * e));
        double rot = p.argumentOfPeriapsis();

        GlStateManager.disableTexture2D();
        GlStateManager.color(1f, 1f, 1f, alpha * 0.92f);
        GL11.glLineWidth((float) Math.max(1.4, getScale() * 0.035));

        GL11.glBegin(GL11.GL_LINE_LOOP);
        for (int i = 0; i <= 360; i++) {
            double E = i * Math.PI * 2.0 / 360.0;
            double ex = a * (Math.cos(E) - e);
            double ey = b * Math.sin(E);
            double rx = ex * Math.cos(rot) - ey * Math.sin(rot);
            double ry = ex * Math.sin(rot) + ey * Math.cos(rot);
            GL11.glVertex2d(worldToScreenX(parentX + rx), worldToScreenY(parentY + ry));
        }
        GL11.glEnd();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    private void drawSelectionHighlight(OrbitalCelestialBody body) {
        ScreenBodyBounds bounds = findScreenBodyBounds(body);
        if (bounds == null) return;

        float sx = bounds.centerX;
        float sy = bounds.centerY;
        float box = getSelectionBoxRadius(bounds);

        Minecraft mc = Minecraft.getMinecraft();
        String name = body.displayName();
        int labelY = (int)(sy - box - 22);

        drawSelectionOverlay(sx, sy, box, 1.0f);
        drawCenteredString(name, sx, labelY, 0xFFFFFFFF);
    }

    private void drawHoverHighlight(OrbitalCelestialBody body) {
        ScreenBodyBounds bounds = findScreenBodyBounds(body);
        if (bounds == null) return;

        float sx = bounds.centerX;
        float sy = bounds.centerY;
        float box = getSelectionBoxRadius(bounds);

        drawSelectionOverlay(sx, sy, box, 0.45f);
    }

    private OrbitalCelestialBody getPinnedInfoBody() {
        if (hoveredBody != null && hoveredBody.objectClass() != CelestialObjectClass.GALAXY
            && isVisibleInCurrentLayer(hoveredBody)) {
            return hoveredBody;
        }
        return null;
    }

    private void drawPinnedInfoPanel(OrbitalCelestialBody body) {
        Minecraft mc = Minecraft.getMinecraft();
        List<InfoRow> rows = buildPinnedInfoRows(body);
        int widest = 0;
        for (InfoRow row : rows) {
            widest = Math.max(widest, mc.fontRenderer.getStringWidth(row.label));
            widest = Math.max(widest, mc.fontRenderer.getStringWidth(row.value));
        }

        int lineHeight = 18;
        int boxWidth = Math.max(150, widest + 18);
        int boxHeight = 10 + rows.size() * lineHeight;
        int x = getArea().width - boxWidth - 18;
        int y = Math.max(24, (getArea().height - boxHeight) / 2);

        Gui.drawRect(x, y, x + boxWidth, y + boxHeight, 0xFF162133);
        Gui.drawRect(x, y, x + boxWidth, y + 2, 0xFF7FB6FF);
        Gui.drawRect(x, y + boxHeight - 2, x + boxWidth, y + boxHeight, 0xFF7FB6FF);
        Gui.drawRect(x, y, x + 2, y + boxHeight, 0xFF7FB6FF);
        Gui.drawRect(x + boxWidth - 2, y, x + boxWidth, y + boxHeight, 0xFF7FB6FF);

        int textY = y + 8;
        for (InfoRow row : rows) {
            mc.fontRenderer.drawStringWithShadow(row.label, x + 12, textY, 0xFF5A63FF);
            if (!row.value.isEmpty()) {
                mc.fontRenderer.drawStringWithShadow(row.value, x + 12, textY + 9, 0xFFD9E0FF);
            }
            textY += lineHeight;
        }
    }

    private List<InfoRow> buildPinnedInfoRows(OrbitalCelestialBody body) {
        List<InfoRow> rows = new ArrayList<>();
        rows.add(new InfoRow("Name", body.displayName()));
        rows.add(new InfoRow("Type", formatObjectClass(body.objectClass())));
        rows.add(new InfoRow("Landable", isLandable(body) ? "Yes" : "No"));
        rows.add(new InfoRow("Dangers", buildDangerSummary(body)));
        rows.add(new InfoRow("Surface", formatSurfaceType(body)));
        rows.add(new InfoRow("Ores", body.properties().oreProfile().isEmpty() ? "Later" : formatSurfaceType(body)));
        return rows;
    }

    private String buildDangerSummary(OrbitalCelestialBody body) {
        List<String> dangers = new ArrayList<>();
        if (body.properties().radiation() >= 0.25) {
            dangers.add("Radiation");
        }
        if (body.properties().temperature() > 360) {
            dangers.add("Heat");
        }
        if (body.properties().temperature() > 0 && body.properties().temperature() < 120) {
            dangers.add("Cold");
        }
        if (!body.properties().visitable() && body.properties().canCreateOutpost()) {
            dangers.add("Remote");
        }
        return dangers.isEmpty() ? "None" : String.join(", ", dangers);
    }

    private void drawContextMenu() {
        ContextMenuLayout layout = getContextMenuLayout();
        if (layout == null) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        int mouseX = toLocalMouseX(getContext().getMouseX());
        int mouseY = toLocalMouseY(getContext().getMouseY());

        Gui.drawRect(layout.left, layout.top, layout.right, layout.bottom, 0xFF111925);
        Gui.drawRect(layout.left, layout.top, layout.right, layout.top + layout.headerHeight, 0xFF23324B);
        Gui.drawRect(layout.left, layout.top, layout.right, layout.top + 2, 0xFF59BFD9);
        Gui.drawRect(layout.left, layout.bottom - 2, layout.right, layout.bottom, 0xFF59BFD9);
        Gui.drawRect(layout.left, layout.top, layout.left + 2, layout.bottom, 0xFF59BFD9);
        Gui.drawRect(layout.right - 2, layout.top, layout.right, layout.bottom, 0xFF59BFD9);

        mc.fontRenderer.drawStringWithShadow(contextMenuBody.displayName(), layout.left + 10, layout.top + 7, 0xFFFFFFFF);

        for (int i = 0; i < layout.actions.size(); i++) {
            ContextMenuAction action = layout.actions.get(i);
            int rowTop = layout.top + layout.headerHeight + i * layout.rowHeight;
            int rowBottom = rowTop + layout.rowHeight;
            boolean hovered = mouseX >= layout.left && mouseX <= layout.right && mouseY >= rowTop && mouseY < rowBottom;

            if (hovered && action.enabled) {
                Gui.drawRect(layout.left + 4, rowTop, layout.right - 4, rowBottom - 1, 0xFF375575);
            }

            int color = action.enabled ? 0xFFD9E0FF : 0xFF6F7A89;
            mc.fontRenderer.drawStringWithShadow(action.label, layout.left + 10, rowTop + 5, color);
        }
    }

    private void drawAssetManagementModal() {
        AssetManagementLayout layout = getAssetManagementLayout();
        if (layout == null) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        CelestialBodyAssetState state = CelestialAssetStore.getState(assetManagementBody.id());

        Gui.drawRect(0, 0, getArea().width, getArea().height, 0xAA09111B);
        Gui.drawRect(layout.left, layout.top, layout.right, layout.bottom, 0xFF121B28);
        Gui.drawRect(layout.left, layout.top, layout.right, layout.top + 3, 0xFF59BFD9);
        Gui.drawRect(layout.left, layout.bottom - 3, layout.right, layout.bottom, 0xFF59BFD9);
        Gui.drawRect(layout.left, layout.top, layout.left + 3, layout.bottom, 0xFF59BFD9);
        Gui.drawRect(layout.right - 3, layout.top, layout.right, layout.bottom, 0xFF59BFD9);
        Gui.drawRect(layout.left, layout.top, layout.right, layout.top + 28, 0xFF22324A);

        mc.fontRenderer.drawStringWithShadow("Manage Assets", layout.left + 12, layout.top + 10, 0xFFFFFFFF);
        mc.fontRenderer.drawStringWithShadow(assetManagementBody.displayName(), layout.left + 120, layout.top + 10, 0xFFD9E0FF);

        drawModalButton(layout.closeButton, "Close", true, false);
        drawModalButton(layout.createStationButton, "Create Station", canCreateBaseStation(assetManagementBody), false);
        if (isGT5AutomationAvailable()) {
            drawModalButton(
                layout.createAutomatedStationButton,
                "Create Automated Station",
                canCreateAutomatedStation(assetManagementBody),
                false);
            drawModalButton(
                layout.createOutpostButton,
                "Create Automated Outpost",
                canCreateAutomatedOutpost(assetManagementBody),
                false);
        } else {
            mc.fontRenderer.drawStringWithShadow(
                "GT5U required for automated assets",
                layout.left + 160,
                layout.top + 36,
                0xFF9AA7B8);
        }

        mc.fontRenderer.drawStringWithShadow("Construction", layout.left + 14, layout.top + 54, 0xFF5A63FF);
        int siteY = layout.top + 70;
        if (layout.siteRows.isEmpty()) {
            mc.fontRenderer.drawStringWithShadow("No construction sites", layout.left + 18, siteY, 0xFF9AA7B8);
        } else {
            for (ConstructionSiteRow row : layout.siteRows) {
                CelestialManagedAsset site = row.asset;
                Gui.drawRect(row.left, row.top, row.right, row.bottom, 0x55213144);
                mc.fontRenderer.drawStringWithShadow(formatAssetDisplayName(site), row.left + 8, row.top + 6, 0xFFFFFFFF);
                mc.fontRenderer.drawStringWithShadow(
                    (site.status() == CelestialAssetStatus.DECONSTRUCTION ? "Stored: " : "Inventory: ")
                        + buildConstructionInventorySummary(site),
                    row.left + 8,
                    row.top + 18,
                    0xFFD9E0FF);
                drawModalButton(row.actionButton, row.actionType.buttonLabel, true, false);
            }
        }

        int assetsHeaderY = layout.assetsSectionTop - 16;
        mc.fontRenderer.drawStringWithShadow("Assets", layout.left + 14, assetsHeaderY, 0xFF5A63FF);
        if (layout.assetRows.isEmpty()) {
            mc.fontRenderer.drawStringWithShadow("No deployed assets", layout.left + 18, layout.assetsSectionTop, 0xFF9AA7B8);
        } else {
            for (AssetRow row : layout.assetRows) {
                CelestialManagedAsset asset = row.asset;
                Gui.drawRect(row.left, row.top, row.right, row.bottom, 0x55213144);
                mc.fontRenderer.drawStringWithShadow(formatAssetDisplayName(asset), row.left + 8, row.top + 6, 0xFFFFFFFF);
                mc.fontRenderer.drawStringWithShadow(
                    formatAssetKind(asset.kind()) + " | " + formatAssetLocation(asset.location()),
                    row.left + 8,
                    row.top + 16,
                    0xFFD9E0FF);
                drawModalButton(row.destroyButton, "Destroy", true, false);
            }
        }

        drawPendingAssetCreationModal();
        drawPendingAssetDestructionModal();
        drawPendingConstructionCancellationModal();
        drawPendingResourceTransferModal();
    }

    private void drawModalButton(ButtonRect rect, String label, boolean enabled, boolean hovered) {
        int bg = !enabled ? 0xFF243041 : hovered ? 0xFF3A5678 : 0xFF2D435D;
        int border = enabled ? 0xFF7FB6FF : 0xFF556577;
        Gui.drawRect(rect.left, rect.top, rect.right, rect.bottom, bg);
        Gui.drawRect(rect.left, rect.top, rect.right, rect.top + 1, border);
        Gui.drawRect(rect.left, rect.bottom - 1, rect.right, rect.bottom, border);
        Gui.drawRect(rect.left, rect.top, rect.left + 1, rect.bottom, border);
        Gui.drawRect(rect.right - 1, rect.top, rect.right, rect.bottom, border);
        Minecraft.getMinecraft().fontRenderer.drawStringWithShadow(label, rect.left + 8, rect.top + 5, enabled ? 0xFFFFFFFF : 0xFF94A0AF);
    }

    private void drawDangerButton(ButtonRect rect, String label) {
        Gui.drawRect(rect.left, rect.top, rect.right, rect.bottom, 0xFF5A1E24);
        Gui.drawRect(rect.left, rect.top, rect.right, rect.top + 1, 0xFFFF5A5A);
        Gui.drawRect(rect.left, rect.bottom - 1, rect.right, rect.bottom, 0xFFFF5A5A);
        Gui.drawRect(rect.left, rect.top, rect.left + 1, rect.bottom, 0xFFFF5A5A);
        Gui.drawRect(rect.right - 1, rect.top, rect.right, rect.bottom, 0xFFFF5A5A);
        Minecraft.getMinecraft().fontRenderer.drawStringWithShadow(label, rect.left + 8, rect.top + 5, 0xFFFFFFFF);
    }

    private void drawCenteredLargeString(String text, float x, float y, float scale, int colour) {
        Minecraft mc = Minecraft.getMinecraft();
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0);
        GlStateManager.scale(scale, scale, 1f);
        float w = mc.fontRenderer.getStringWidth(text);
        mc.fontRenderer.drawStringWithShadow(text, Math.round(-w / 2f), 0, colour);
        GlStateManager.popMatrix();
    }

    private void drawActionStatusMessage() {
        if (actionStatusMessage == null || actionStatusMessage.isEmpty()) {
            return;
        }
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
        contextMenuBody = body;
        contextMenuX = localMouseX;
        contextMenuY = localMouseY;
    }

    private void closeContextMenu() {
        contextMenuBody = null;
    }

    private boolean handleContextMenuClick(int localMouseX, int localMouseY) {
        ContextMenuLayout layout = getContextMenuLayout();
        if (layout == null) {
            return false;
        }
        if (localMouseX < layout.left || localMouseX > layout.right || localMouseY < layout.top
            || localMouseY > layout.bottom) {
            return false;
        }
        if (localMouseY < layout.top + layout.headerHeight) {
            return true;
        }

        int rowIndex = (localMouseY - layout.top - layout.headerHeight) / layout.rowHeight;
        if (rowIndex < 0 || rowIndex >= layout.actions.size()) {
            return true;
        }

        ContextMenuAction action = layout.actions.get(rowIndex);
        if (!action.enabled) {
            return true;
        }

        if (action.actionType == ContextMenuActionType.MANAGE_ASSETS) {
            openAssetManagement(contextMenuBody);
        } else if (action.actionType == ContextMenuActionType.CREATE_STATION) {
            CelestialAssetStore.createOperationalAsset(
                contextMenuBody.id(),
                contextMenuBody.displayName() + " Station",
                CelestialAssetKind.STATION,
                CelestialAssetLocation.ORBIT);
            showActionStatus("Station created");
        } else if (action.actionType == ContextMenuActionType.OPEN_AUTOMATED_STATION_CONFIRM) {
            openAssetManagement(contextMenuBody);
            openPendingAssetCreation(
                contextMenuBody,
                contextMenuBody.displayName() + " Automated Station",
                CelestialAssetKind.AUTOMATED_STATION,
                CelestialAssetLocation.ORBIT);
        } else if (action.actionType == ContextMenuActionType.OPEN_AUTOMATED_OUTPOST_CONFIRM) {
            openAssetManagement(contextMenuBody);
            openPendingAssetCreation(
                contextMenuBody,
                contextMenuBody.displayName() + " Automated Outpost",
                CelestialAssetKind.AUTOMATED_OUTPOST,
                CelestialAssetLocation.SURFACE);
        } else {
            actionStatusMessage = action.feedbackMessage;
            actionStatusExpiresAt = System.currentTimeMillis() + 2500L;
        }
        closeContextMenu();
        return true;
    }

    private boolean isWithinContextMenu(int localMouseX, int localMouseY) {
        ContextMenuLayout layout = getContextMenuLayout();
        return layout != null && localMouseX >= layout.left && localMouseX <= layout.right && localMouseY >= layout.top
            && localMouseY <= layout.bottom;
    }

    private ContextMenuLayout getContextMenuLayout() {
        if (contextMenuBody == null) {
            return null;
        }

        List<ContextMenuAction> actions = buildContextMenuActions(contextMenuBody);
        Minecraft mc = Minecraft.getMinecraft();
        int maxTextWidth = mc.fontRenderer.getStringWidth(contextMenuBody.displayName());
        for (ContextMenuAction action : actions) {
            maxTextWidth = Math.max(maxTextWidth, mc.fontRenderer.getStringWidth(action.label));
        }

        int width = Math.max(150, maxTextWidth + 20);
        int headerHeight = 22;
        int rowHeight = 18;
        int height = headerHeight + actions.size() * rowHeight;
        int left = clamp(contextMenuX, 8, Math.max(8, getArea().width - width - 8));
        int top = clamp(contextMenuY, 8, Math.max(8, getArea().height - height - 8));
        return new ContextMenuLayout(left, top, left + width, top + height, headerHeight, rowHeight, actions);
    }

    private List<ContextMenuAction> buildContextMenuActions(OrbitalCelestialBody body) {
        List<ContextMenuAction> actions = new ArrayList<>();
        actions.add(new ContextMenuAction("Manage Assets", true, "", ContextMenuActionType.MANAGE_ASSETS));
        if (canCreateBaseStation(body)) {
            actions.add(
                new ContextMenuAction("Create Station", true, "", ContextMenuActionType.CREATE_STATION));
        }
        if (canCreateAutomatedStation(body)) {
            actions.add(
                new ContextMenuAction("Create Automated Station", true, "",
                    ContextMenuActionType.OPEN_AUTOMATED_STATION_CONFIRM));
        }
        if (canCreateAutomatedOutpost(body)) {
            actions.add(
                new ContextMenuAction("Create Automated Outpost", true, "",
                    ContextMenuActionType.OPEN_AUTOMATED_OUTPOST_CONFIRM));
        }
        if (actions.size() == 1) {
            actions.add(new ContextMenuAction("No actions available", false, "", ContextMenuActionType.MESSAGE));
        }
        return actions;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private boolean shouldRenderBodyAtCurrentZoom(OrbitalCelestialBody body) {
        if (isometricProgress > 0.01 || body == viewRoot || body == focusedBody) {
            return true;
        }
        if (body.objectClass() != CelestialObjectClass.STATION && body.objectClass() != CelestialObjectClass.ASTEROID
            && !(body.objectClass() == CelestialObjectClass.MOON && body.spriteSize() < 0.12)) {
            return true;
        }

        OrbitalCelestialBody parent = findParent(root, body);
        if (parent == null || parent.objectClass() == CelestialObjectClass.GALAXY) {
            return true;
        }
        if (usesAbsolutePosition(parent, body)) {
            return true;
        }
        float separation = (float) (body.orbitalParams().perigee() * getScale());
        float minimumSeparation = getRenderedBodyRadius(body) + getRenderedBodyRadius(parent) + 10f;
        return separation >= minimumSeparation;
    }

    private void openAssetManagement(OrbitalCelestialBody body) {
        if (body == null || body.objectClass() == CelestialObjectClass.GALAXY) {
            return;
        }
        assetManagementBody = body;
        pendingAssetCreation = null;
        pendingAssetDestruction = null;
        pendingConstructionCancellation = null;
        pendingResourceTransfer = null;
    }

    private void closeAssetManagement() {
        assetManagementBody = null;
        pendingAssetCreation = null;
        pendingAssetDestruction = null;
        pendingConstructionCancellation = null;
        pendingResourceTransfer = null;
    }

    private boolean handleAssetManagementClick(int localMouseX, int localMouseY) {
        AssetManagementLayout layout = getAssetManagementLayout();
        if (layout == null) {
            return false;
        }
        if (pendingResourceTransfer != null) {
            return handlePendingResourceTransferClick(localMouseX, localMouseY);
        }
        if (pendingConstructionCancellation != null) {
            return handlePendingConstructionCancellationClick(localMouseX, localMouseY);
        }
        if (pendingAssetDestruction != null) {
            return handlePendingAssetDestructionClick(localMouseX, localMouseY);
        }
        if (pendingAssetCreation != null) {
            return handlePendingAssetCreationClick(localMouseX, localMouseY);
        }
        if (localMouseX < layout.left || localMouseX > layout.right || localMouseY < layout.top
            || localMouseY > layout.bottom) {
            closeAssetManagement();
            return true;
        }

        if (layout.closeButton.contains(localMouseX, localMouseY)) {
            closeAssetManagement();
            return true;
        }
        if (layout.createStationButton.contains(localMouseX, localMouseY) && canCreateBaseStation(assetManagementBody)) {
            CelestialAssetStore.createOperationalAsset(
                assetManagementBody.id(),
                assetManagementBody.displayName() + " Station",
                CelestialAssetKind.STATION,
                CelestialAssetLocation.ORBIT);
            showActionStatus("Station created");
            return true;
        }
        if (layout.createAutomatedStationButton.contains(localMouseX, localMouseY)
            && canCreateAutomatedStation(assetManagementBody)) {
            openPendingAssetCreation(
                assetManagementBody,
                assetManagementBody.displayName() + " Automated Station",
                CelestialAssetKind.AUTOMATED_STATION,
                CelestialAssetLocation.ORBIT);
            return true;
        }
        if (layout.createOutpostButton.contains(localMouseX, localMouseY) && canCreateAutomatedOutpost(assetManagementBody)) {
            openPendingAssetCreation(
                assetManagementBody,
                assetManagementBody.displayName() + " Automated Outpost",
                CelestialAssetKind.AUTOMATED_OUTPOST,
                CelestialAssetLocation.SURFACE);
            return true;
        }

        for (ConstructionSiteRow row : layout.siteRows) {
            if (row.actionButton.contains(localMouseX, localMouseY)) {
                if (row.actionType == ConstructionRowActionType.CANCEL_BUILD) {
                    if (hasStoredConstructionResources(row.asset)) {
                        openPendingConstructionCancellation(row.asset);
                    } else {
                        CelestialAssetStore.cancelConstruction(row.asset.assetId());
                        showActionStatus("Construction canceled");
                    }
                } else if (row.actionType == ConstructionRowActionType.SEND_RESOURCES) {
                    openPendingResourceTransfer(row.asset);
                }
                return true;
            }
        }

        for (AssetRow row : layout.assetRows) {
            if (row.destroyButton.contains(localMouseX, localMouseY)) {
                openPendingAssetDestruction(row.asset);
                return true;
            }
        }

        return true;
    }

    private AssetManagementLayout getAssetManagementLayout() {
        if (assetManagementBody == null) {
            return null;
        }

        CelestialBodyAssetState state = CelestialAssetStore.getState(assetManagementBody.id());
        int width = Math.min(520, getArea().width - 80);
        int height = Math.min(420, getArea().height - 60);
        int left = (getArea().width - width) / 2;
        int top = (getArea().height - height) / 2;
        int right = left + width;
        int bottom = top + height;

        ButtonRect closeButton = new ButtonRect(right - 88, top + 6, right - 12, top + 24);
        ButtonRect createStationButton = new ButtonRect(left + 14, top + 30, left + 134, top + 48);
        ButtonRect createAutomatedStationButton = new ButtonRect(left + 144, top + 30, left + 304, top + 48);
        ButtonRect createOutpostButton = new ButtonRect(left + 314, top + 30, left + 474, top + 48);

        List<CelestialManagedAsset> constructionAssets = new ArrayList<>();
        constructionAssets.addAll(getAssetsWithStatus(state.assets(), CelestialAssetStatus.CONSTRUCTION_SITE));
        constructionAssets.addAll(getAssetsWithStatus(state.assets(), CelestialAssetStatus.DECONSTRUCTION));
        List<CelestialManagedAsset> deployedAssets = getAssetsWithStatus(state.assets(), CelestialAssetStatus.OPERATIONAL);

        List<ConstructionSiteRow> siteRows = new ArrayList<>();
        int siteTop = top + 70;
        int siteRowHeight = 42;
        for (int i = 0; i < constructionAssets.size(); i++) {
            CelestialManagedAsset site = constructionAssets.get(i);
            int rowTop = siteTop + i * (siteRowHeight + 6);
            int rowBottom = rowTop + siteRowHeight;
            int rowRight = right - 14;
            siteRows.add(
                new ConstructionSiteRow(
                    site,
                    left + 14,
                    rowTop,
                    rowRight,
                    rowBottom,
                    new ButtonRect(rowRight - 124, rowTop + 12, rowRight - 8, rowTop + 30),
                    site.status() == CelestialAssetStatus.DECONSTRUCTION
                        ? ConstructionRowActionType.SEND_RESOURCES
                        : ConstructionRowActionType.CANCEL_BUILD));
        }

        int assetsSectionTop = Math.max(top + 220, siteTop + Math.max(1, constructionAssets.size()) * (siteRowHeight + 6));
        List<AssetRow> assetRows = new ArrayList<>();
        int assetTop = assetsSectionTop;
        for (int i = 0; i < deployedAssets.size(); i++) {
            CelestialManagedAsset asset = deployedAssets.get(i);
            int rowTop = assetTop + i * (siteRowHeight + 6);
            int rowBottom = rowTop + siteRowHeight;
            int rowRight = right - 14;
            assetRows.add(
                new AssetRow(
                    asset,
                    left + 14,
                    rowTop,
                    rowRight,
                    rowBottom,
                    new ButtonRect(rowRight - 82, rowTop + 8, rowRight - 8, rowTop + 26)));
        }

        return new AssetManagementLayout(
            left,
            top,
            right,
            bottom,
            assetsSectionTop,
            closeButton,
            createStationButton,
            createAutomatedStationButton,
            createOutpostButton,
            siteRows,
            assetRows);
    }

    private void showActionStatus(String message) {
        actionStatusMessage = message;
        actionStatusExpiresAt = System.currentTimeMillis() + 2500L;
    }

    private void openPendingAssetCreation(OrbitalCelestialBody body, String displayName, CelestialAssetKind kind,
        CelestialAssetLocation location) {
        if (body == null) {
            return;
        }
        pendingAssetCreation = new PendingAssetCreation(
            body.id(),
            displayName,
            kind,
            location,
            CelestialAssetStore.previewRequirements(kind));
    }

    private void openPendingAssetDestruction(CelestialManagedAsset asset) {
        if (asset == null) {
            return;
        }
        pendingAssetDestruction = new PendingAssetDestruction(asset, false);
    }

    private boolean hasStoredConstructionResources(CelestialManagedAsset asset) {
        if (asset == null) {
            return false;
        }
        for (CelestialAssetRequirement entry : asset.constructionInventory()) {
            if (entry.amount() > 0) {
                return true;
            }
        }
        return false;
    }

    private void openPendingConstructionCancellation(CelestialManagedAsset asset) {
        if (asset == null) {
            return;
        }
        pendingConstructionCancellation = new PendingConstructionCancellation(asset);
    }

    private void openPendingResourceTransfer(CelestialManagedAsset asset) {
        if (asset == null) {
            return;
        }
        pendingResourceTransfer = new PendingResourceTransfer(asset, getTransferTargetsInSystem(assetManagementBody));
    }

    private boolean handlePendingAssetCreationClick(int localMouseX, int localMouseY) {
        PendingAssetCreationLayout layout = getPendingAssetCreationLayout();
        if (layout == null) {
            return true;
        }
        if (localMouseX < layout.left || localMouseX > layout.right || localMouseY < layout.top
            || localMouseY > layout.bottom) {
            pendingAssetCreation = null;
            return true;
        }
        if (layout.cancelButton.contains(localMouseX, localMouseY)) {
            pendingAssetCreation = null;
            return true;
        }
        if (layout.confirmButton.contains(localMouseX, localMouseY)) {
            CelestialAssetStore.createAssetInConstruction(
                pendingAssetCreation.celestialObjectId,
                pendingAssetCreation.displayName,
                pendingAssetCreation.kind,
                pendingAssetCreation.location);
            showActionStatus(formatAssetKind(pendingAssetCreation.kind) + " construction planned");
            pendingAssetCreation = null;
            return true;
        }
        return true;
    }

    private boolean handlePendingAssetDestructionClick(int localMouseX, int localMouseY) {
        PendingAssetDestructionLayout layout = getPendingAssetDestructionLayout();
        if (layout == null) {
            return true;
        }
        if (localMouseX < layout.left || localMouseX > layout.right || localMouseY < layout.top
            || localMouseY > layout.bottom) {
            pendingAssetDestruction = null;
            return true;
        }
        if (layout.cancelButton.contains(localMouseX, localMouseY)) {
            pendingAssetDestruction = null;
            return true;
        }
        if (layout.destroyButton.contains(localMouseX, localMouseY)) {
            if (!pendingAssetDestruction.armed) {
                pendingAssetDestruction = new PendingAssetDestruction(pendingAssetDestruction.asset, true);
            } else {
                CelestialAssetStore.destroyAsset(pendingAssetDestruction.asset.assetId());
                showActionStatus("Asset destroyed");
                pendingAssetDestruction = null;
            }
            return true;
        }
        return true;
    }

    private boolean handlePendingConstructionCancellationClick(int localMouseX, int localMouseY) {
        PendingConstructionCancellationLayout layout = getPendingConstructionCancellationLayout();
        if (layout == null) {
            return true;
        }
        if (localMouseX < layout.left || localMouseX > layout.right || localMouseY < layout.top
            || localMouseY > layout.bottom) {
            pendingConstructionCancellation = null;
            return true;
        }
        if (layout.cancelButton.contains(localMouseX, localMouseY)) {
            pendingConstructionCancellation = null;
            return true;
        }
        if (layout.confirmButton.contains(localMouseX, localMouseY)) {
            CelestialAssetStore.startDeconstruction(pendingConstructionCancellation.asset.assetId());
            showActionStatus("Construction site converted to deconstruction");
            pendingConstructionCancellation = null;
            return true;
        }
        return true;
    }

    private boolean handlePendingResourceTransferClick(int localMouseX, int localMouseY) {
        PendingResourceTransferLayout layout = getPendingResourceTransferLayout();
        if (layout == null) {
            return true;
        }
        if (localMouseX < layout.left || localMouseX > layout.right || localMouseY < layout.top
            || localMouseY > layout.bottom) {
            pendingResourceTransfer = null;
            return true;
        }
        if (layout.closeButton.contains(localMouseX, localMouseY)) {
            pendingResourceTransfer = null;
            return true;
        }
        for (TransferTargetRow row : layout.rows) {
            if (row.sendButton.contains(localMouseX, localMouseY)) {
                // TODO: validate an orbital rocket with enough free capacity before allowing resource recovery transfer.
                // TODO: consume the construction inventory and create an actual logistics job toward the selected station.
                showActionStatus("Resource transfer planning is not implemented yet");
                pendingResourceTransfer = null;
                return true;
            }
        }
        return true;
    }

    private void drawPendingAssetCreationModal() {
        PendingAssetCreationLayout layout = getPendingAssetCreationLayout();
        if (layout == null) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        Gui.drawRect(0, 0, getArea().width, getArea().height, 0x88000000);
        Gui.drawRect(layout.left, layout.top, layout.right, layout.bottom, 0xFF121B28);
        Gui.drawRect(layout.left, layout.top, layout.right, layout.top + 3, 0xFF59BFD9);
        Gui.drawRect(layout.left, layout.bottom - 3, layout.right, layout.bottom, 0xFF59BFD9);
        Gui.drawRect(layout.left, layout.top, layout.left + 3, layout.bottom, 0xFF59BFD9);
        Gui.drawRect(layout.right - 3, layout.top, layout.right, layout.bottom, 0xFF59BFD9);

        mc.fontRenderer.drawStringWithShadow(
            "Confirm " + formatAssetKind(pendingAssetCreation.kind),
            layout.left + 12,
            layout.top + 10,
            0xFFFFFFFF);
        mc.fontRenderer.drawStringWithShadow(
            pendingAssetCreation.displayName,
            layout.left + 12,
            layout.top + 28,
            0xFFD9E0FF);
        mc.fontRenderer.drawStringWithShadow("Required resources", layout.left + 12, layout.top + 52, 0xFF5A63FF);

        int resourceY = layout.top + 68;
        for (CelestialAssetRequirement requirement : pendingAssetCreation.requiredResources) {
            mc.fontRenderer.drawStringWithShadow(
                "- " + requirement.amount() + " " + requirement.displayName(),
                layout.left + 16,
                resourceY,
                0xFFD9E0FF);
            resourceY += 12;
        }

        drawModalButton(layout.cancelButton, "Cancel", true, false);
        drawModalButton(layout.confirmButton, "Confirm", true, false);
    }

    private void drawPendingAssetDestructionModal() {
        PendingAssetDestructionLayout layout = getPendingAssetDestructionLayout();
        if (layout == null) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        Gui.drawRect(0, 0, getArea().width, getArea().height, 0xAA000000);
        Gui.drawRect(layout.left, layout.top, layout.right, layout.bottom, 0xFF1A1012);
        Gui.drawRect(layout.left, layout.top, layout.right, layout.top + 3, 0xFFD14A4A);
        Gui.drawRect(layout.left, layout.bottom - 3, layout.right, layout.bottom, 0xFFD14A4A);
        Gui.drawRect(layout.left, layout.top, layout.left + 3, layout.bottom, 0xFFD14A4A);
        Gui.drawRect(layout.right - 3, layout.top, layout.right, layout.bottom, 0xFFD14A4A);

        drawCenteredLargeString("THIS IS IRREVERSIBLE", (layout.left + layout.right) / 2f, layout.top + 16, 1.45f, 0xFFFF5A5A);
        mc.fontRenderer.drawStringWithShadow(
            "You are about to destroy:",
            layout.left + 18,
            layout.top + 52,
            0xFFD9E0FF);
        mc.fontRenderer.drawStringWithShadow(
            formatAssetDisplayName(pendingAssetDestruction.asset),
            layout.left + 18,
            layout.top + 68,
            0xFFFFFFFF);
        mc.fontRenderer.drawStringWithShadow(
            pendingAssetDestruction.armed ? "Click Destroy again to confirm." : "Press Destroy to arm confirmation.",
            layout.left + 18,
            layout.top + 92,
            0xFFFFB3B3);

        drawModalButton(layout.cancelButton, "Cancel", true, false);
        drawDangerButton(layout.destroyButton, "Destroy");
    }

    private void drawPendingConstructionCancellationModal() {
        PendingConstructionCancellationLayout layout = getPendingConstructionCancellationLayout();
        if (layout == null) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        Gui.drawRect(0, 0, getArea().width, getArea().height, 0x88000000);
        Gui.drawRect(layout.left, layout.top, layout.right, layout.bottom, 0xFF121B28);
        Gui.drawRect(layout.left, layout.top, layout.right, layout.top + 3, 0xFFE6B35A);
        Gui.drawRect(layout.left, layout.bottom - 3, layout.right, layout.bottom, 0xFFE6B35A);
        Gui.drawRect(layout.left, layout.top, layout.left + 3, layout.bottom, 0xFFE6B35A);
        Gui.drawRect(layout.right - 3, layout.top, layout.right, layout.bottom, 0xFFE6B35A);

        mc.fontRenderer.drawStringWithShadow("Cancel Construction?", layout.left + 12, layout.top + 10, 0xFFFFFFFF);
        mc.fontRenderer.drawStringWithShadow(
            formatAssetDisplayName(pendingConstructionCancellation.asset),
            layout.left + 12,
            layout.top + 28,
            0xFFD9E0FF);
        mc.fontRenderer.drawStringWithShadow(
            "Stored resources will be moved into deconstruction recovery.",
            layout.left + 12,
            layout.top + 54,
            0xFFFFD59A);

        drawModalButton(layout.cancelButton, "Cancel", true, false);
        drawModalButton(layout.confirmButton, "Confirm", true, false);
    }

    private void drawPendingResourceTransferModal() {
        PendingResourceTransferLayout layout = getPendingResourceTransferLayout();
        if (layout == null) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        Gui.drawRect(0, 0, getArea().width, getArea().height, 0x88000000);
        Gui.drawRect(layout.left, layout.top, layout.right, layout.bottom, 0xFF121B28);
        Gui.drawRect(layout.left, layout.top, layout.right, layout.top + 3, 0xFF59BFD9);
        Gui.drawRect(layout.left, layout.bottom - 3, layout.right, layout.bottom, 0xFF59BFD9);
        Gui.drawRect(layout.left, layout.top, layout.left + 3, layout.bottom, 0xFF59BFD9);
        Gui.drawRect(layout.right - 3, layout.top, layout.right, layout.bottom, 0xFF59BFD9);

        mc.fontRenderer.drawStringWithShadow("Send Resources To", layout.left + 12, layout.top + 10, 0xFFFFFFFF);
        mc.fontRenderer.drawStringWithShadow(
            formatAssetDisplayName(pendingResourceTransfer.asset),
            layout.left + 12,
            layout.top + 28,
            0xFFD9E0FF);
        mc.fontRenderer.drawStringWithShadow(
            "Requires an orbital rocket with enough capacity.",
            layout.left + 12,
            layout.top + 46,
            0xFF9AA7B8);

        if (layout.rows.isEmpty()) {
            mc.fontRenderer.drawStringWithShadow("No stations available in this system", layout.left + 16, layout.top + 74, 0xFF9AA7B8);
        } else {
            for (TransferTargetRow row : layout.rows) {
                Gui.drawRect(row.left, row.top, row.right, row.bottom, 0x55213144);
                mc.fontRenderer.drawStringWithShadow(row.target.displayName, row.left + 8, row.top + 6, 0xFFFFFFFF);
                mc.fontRenderer.drawStringWithShadow(row.target.hostBodyName, row.left + 8, row.top + 18, 0xFFD9E0FF);
                drawModalButton(row.sendButton, "Send", true, false);
            }
        }

        drawModalButton(layout.closeButton, "Close", true, false);
    }

    private PendingAssetCreationLayout getPendingAssetCreationLayout() {
        if (pendingAssetCreation == null) {
            return null;
        }

        int width = 320;
        int height = 150 + Math.max(0, pendingAssetCreation.requiredResources.size() - 2) * 12;
        int left = (getArea().width - width) / 2;
        int top = (getArea().height - height) / 2;
        int right = left + width;
        int bottom = top + height;

        return new PendingAssetCreationLayout(
            left,
            top,
            right,
            bottom,
            new ButtonRect(left + 16, bottom - 34, left + 126, bottom - 14),
            new ButtonRect(right - 126, bottom - 34, right - 16, bottom - 14));
    }

    private PendingAssetDestructionLayout getPendingAssetDestructionLayout() {
        if (pendingAssetDestruction == null) {
            return null;
        }

        int width = 360;
        int height = 150;
        int left = (getArea().width - width) / 2;
        int top = (getArea().height - height) / 2;
        int right = left + width;
        int bottom = top + height;

        ButtonRect leftButton = new ButtonRect(left + 18, bottom - 34, left + 148, bottom - 14);
        ButtonRect rightButton = new ButtonRect(right - 148, bottom - 34, right - 18, bottom - 14);

        return pendingAssetDestruction.armed
            ? new PendingAssetDestructionLayout(left, top, right, bottom, rightButton, leftButton)
            : new PendingAssetDestructionLayout(left, top, right, bottom, leftButton, rightButton);
    }

    private PendingConstructionCancellationLayout getPendingConstructionCancellationLayout() {
        if (pendingConstructionCancellation == null) {
            return null;
        }

        int width = 360;
        int height = 124;
        int left = (getArea().width - width) / 2;
        int top = (getArea().height - height) / 2;
        int right = left + width;
        int bottom = top + height;
        return new PendingConstructionCancellationLayout(
            left,
            top,
            right,
            bottom,
            new ButtonRect(left + 18, bottom - 34, left + 148, bottom - 14),
            new ButtonRect(right - 148, bottom - 34, right - 18, bottom - 14));
    }

    private PendingResourceTransferLayout getPendingResourceTransferLayout() {
        if (pendingResourceTransfer == null) {
            return null;
        }

        int width = 420;
        int height = Math.min(280, 120 + pendingResourceTransfer.targets.size() * 42);
        int left = (getArea().width - width) / 2;
        int top = (getArea().height - height) / 2;
        int right = left + width;
        int bottom = top + height;

        List<TransferTargetRow> rows = new ArrayList<>();
        int rowTop = top + 66;
        for (int i = 0; i < pendingResourceTransfer.targets.size(); i++) {
            StationTransferTarget target = pendingResourceTransfer.targets.get(i);
            int currentTop = rowTop + i * 42;
            int currentBottom = currentTop + 36;
            rows.add(new TransferTargetRow(
                target,
                left + 14,
                currentTop,
                right - 14,
                currentBottom,
                new ButtonRect(right - 92, currentTop + 8, right - 20, currentTop + 26)));
        }

        return new PendingResourceTransferLayout(
            left,
            top,
            right,
            bottom,
            new ButtonRect(right - 96, top + 8, right - 18, top + 26),
            rows);
    }

    private List<CelestialManagedAsset> getAssetsWithStatus(List<CelestialManagedAsset> assets, CelestialAssetStatus status) {
        List<CelestialManagedAsset> filtered = new ArrayList<>();
        for (CelestialManagedAsset asset : assets) {
            if (asset.status() == status) {
                filtered.add(asset);
            }
        }
        return filtered;
    }

    private String formatAssetDisplayName(CelestialManagedAsset asset) {
        return switch (asset.status()) {
            case CONSTRUCTION_SITE -> asset.displayName() + " (In construction)";
            case DECONSTRUCTION -> asset.displayName() + " (Deconstruction)";
            default -> asset.displayName();
        };
    }

    private String buildRequirementsSummary(List<CelestialAssetRequirement> requiredResources) {
        if (requiredResources.isEmpty()) {
            return "No requirements";
        }
        List<String> parts = new ArrayList<>();
        for (CelestialAssetRequirement requirement : requiredResources) {
            parts.add(requirement.amount() + " " + requirement.displayName());
        }
        return String.join(", ", parts);
    }

    private String buildConstructionInventorySummary(CelestialManagedAsset asset) {
        if (asset.status() == CelestialAssetStatus.DECONSTRUCTION) {
            return buildStoredInventorySummary(asset.constructionInventory());
        }
        if (asset.requiredResources().isEmpty()) {
            return "Empty";
        }
        List<String> parts = new ArrayList<>();
        for (CelestialAssetRequirement required : asset.requiredResources()) {
            long storedAmount = 0;
            for (CelestialAssetRequirement stored : asset.constructionInventory()) {
                if (required.matches(stored.stack())) {
                    storedAmount += stored.amount();
                }
            }
            parts.add(storedAmount + "/" + required.amount() + " " + required.displayName());
        }
        return String.join(", ", parts);
    }

    private String buildStoredInventorySummary(List<CelestialAssetRequirement> storedResources) {
        if (storedResources.isEmpty()) {
            return "Empty";
        }
        List<String> parts = new ArrayList<>();
        for (CelestialAssetRequirement stored : storedResources) {
            parts.add(stored.amount() + " " + stored.displayName());
        }
        return String.join(", ", parts);
    }

    private List<StationTransferTarget> getTransferTargetsInSystem(OrbitalCelestialBody body) {
        List<StationTransferTarget> targets = new ArrayList<>();
        if (body == null) {
            return targets;
        }

        OrbitalCelestialBody hostStar = findHostStar(root, body, null);
        if (hostStar == null) {
            return targets;
        }

        List<OrbitalCelestialBody> systemBodies = new ArrayList<>();
        collectBodies(hostStar, systemBodies);
        for (OrbitalCelestialBody systemBody : systemBodies) {
            CelestialBodyAssetState state = CelestialAssetStore.getState(systemBody.id());
            for (CelestialManagedAsset asset : state.assets()) {
                boolean isStationTarget = asset.status() == CelestialAssetStatus.OPERATIONAL
                    && asset.location() == CelestialAssetLocation.ORBIT
                    && (asset.kind() == CelestialAssetKind.STATION
                        || asset.kind() == CelestialAssetKind.AUTOMATED_STATION);
                if (isStationTarget) {
                    targets.add(new StationTransferTarget(asset.assetId(), asset.displayName(), systemBody.displayName()));
                }
            }
        }
        return targets;
    }

    private OrbitalCelestialBody findHostStar(OrbitalCelestialBody current, OrbitalCelestialBody target,
        OrbitalCelestialBody currentStar) {
        OrbitalCelestialBody nextStar = current.objectClass() == CelestialObjectClass.STAR ? current : currentStar;
        if (current == target) {
            return nextStar;
        }
        for (OrbitalCelestialBody child : current.children()) {
            OrbitalCelestialBody found = findHostStar(child, target, nextStar);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private void collectBodies(OrbitalCelestialBody current, List<OrbitalCelestialBody> out) {
        out.add(current);
        for (OrbitalCelestialBody child : current.children()) {
            collectBodies(child, out);
        }
    }

    private String formatAssetKind(CelestialAssetKind kind) {
        return switch (kind) {
            case STATION -> "Station";
            case AUTOMATED_STATION -> "Automated Station";
            case AUTOMATED_OUTPOST -> "Automated Outpost";
        };
    }

    private String formatAssetLocation(CelestialAssetLocation location) {
        return switch (location) {
            case ORBIT -> "Orbit";
            case SURFACE -> "Surface";
        };
    }

    private String formatObjectClass(CelestialObjectClass objectClass) {
        String raw = objectClass.name().toLowerCase().replace('_', ' ');
        return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
    }

    private boolean isLandable(OrbitalCelestialBody body) {
        return switch (body.objectClass()) {
            case PLANET, MOON, ASTEROID -> body.properties().visitable();
            default -> false;
        };
    }

    private String formatSurfaceType(OrbitalCelestialBody body) {
        String oreProfile = body.properties().oreProfile();
        if (oreProfile == null || oreProfile.isEmpty()) {
            return body.objectClass() == CelestialObjectClass.STATION ? "Artificial" : "Unknown";
        }
        String[] parts = oreProfile.split("_");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }

    private void drawSelectionOverlay(float centerX, float centerY, float boxSize, float alpha) {
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        int color = withAlpha(0xFF18C8FF, alpha);
        int thickness = 2;
        int left = Math.round(centerX - boxSize);
        int right = Math.round(centerX + boxSize);
        int top = Math.round(centerY - boxSize);
        int bottom = Math.round(centerY + boxSize);
        int corner = Math.max(5, Math.min(12, Math.round(boxSize * 0.55f)));

        drawCorner(left, top, corner, thickness, true, true, color);
        drawCorner(right, top, corner, thickness, false, true, color);
        drawCorner(left, bottom, corner, thickness, true, false, color);
        drawCorner(right, bottom, corner, thickness, false, false, color);
    }

    private void drawCorner(int x, int y, int length, int thickness, boolean leftAligned, boolean topAligned, int color) {
        int horizontalStart = leftAligned ? x : x - length;
        int horizontalEnd = leftAligned ? x + length : x;
        int horizontalTop = topAligned ? y : y - thickness;
        int horizontalBottom = topAligned ? y + thickness : y;

        int verticalLeft = leftAligned ? x : x - thickness;
        int verticalRight = leftAligned ? x + thickness : x;
        int verticalTop = topAligned ? y : y - length;
        int verticalBottom = topAligned ? y + length : y;

        Gui.drawRect(horizontalStart, horizontalTop, horizontalEnd, horizontalBottom, color);
        Gui.drawRect(verticalLeft, verticalTop, verticalRight, verticalBottom, color);
    }

    private float getSelectionBoxRadius(OrbitalCelestialBody body) {
        return getRenderedBodyRadius(body) + 4f;
    }

    private float getSelectionBoxRadius(ScreenBodyBounds bounds) {
        return bounds.renderedRadius + 4f;
    }

    private boolean isGT5AutomationAvailable() {
        return Galaxia.hasGT5U();
    }

    private boolean canCreateBaseStation(OrbitalCelestialBody body) {
        return body != null && body.properties().canCreateStation();
    }

    private boolean canCreateAutomatedStation(OrbitalCelestialBody body) {
        return canCreateBaseStation(body) && isGT5AutomationAvailable();
    }

    private boolean canCreateAutomatedOutpost(OrbitalCelestialBody body) {
        return body != null && isGT5AutomationAvailable() && body.properties().canCreateOutpost();
    }

    private float getInteractionRadius(OrbitalCelestialBody body) {
        return Math.max(5f, getRenderedBodyRadius(body));
    }

    private boolean isOnScreen(float sx, float sy, float radius) {
        return sx >= 0 && sy >= 0 && sx <= getArea().width && sy <= getArea().height;
    }

    private float getLabelYOffset(OrbitalCelestialBody body) {
        return getRenderedBodyRadius(body) + 6f;
    }

    private static int withAlpha(int colour, float alpha) {
        int a = Math.max(0, Math.min(255, (int) (((colour >> 24) & 0xFF) * alpha)));
        return (colour & 0x00FFFFFF) | (a << 24);
    }

    private static int getFallbackBodyColor(CelestialObjectClass objectClass) {
        return switch (objectClass) {
            case GALAXY -> 0xFFFFFFFF;
            case BLACK_HOLE -> 0xFF5A4B7A;
            case STAR -> 0xFFFFD36B;
            case GAS_GIANT -> 0xFFD9A066;
            case PLANET -> 0xFF7FC7A6;
            case MOON -> 0xFFD8DCE6;
            case ASTEROID, ASTEROID_BELT -> 0xFF9CA3AF;
            case STATION -> 0xFF89C2FF;
            case COMET -> 0xFFAEE7FF;
        };
    }

    private static final class ScreenBodyBounds {

        private final OrbitalCelestialBody body;
        private final float centerX;
        private final float centerY;
        private final float renderedRadius;
        private final float interactionRadius;

        private ScreenBodyBounds(OrbitalCelestialBody body, float centerX, float centerY, float renderedRadius,
            float interactionRadius) {
            this.body = body;
            this.centerX = centerX;
            this.centerY = centerY;
            this.renderedRadius = renderedRadius;
            this.interactionRadius = interactionRadius;
        }

        private double bodyScore(float x, float y) {
            double dx = x - centerX;
            double dy = y - centerY;
            double absDx = Math.abs(dx);
            double absDy = Math.abs(dy);
            if (absDx > interactionRadius || absDy > interactionRadius) {
                return Double.MAX_VALUE;
            }
            double normalizedDx = absDx / Math.max(1.0, interactionRadius);
            double normalizedDy = absDy / Math.max(1.0, interactionRadius);
            return Math.max(normalizedDx, normalizedDy);
        }
    }

    private static final class LabelDrawCall {

        private final String text;
        private final float x;
        private final float y;
        private final int color;

        private LabelDrawCall(String text, float x, float y, int color) {
            this.text = text;
            this.x = x;
            this.y = y;
            this.color = color;
        }
    }

    private static final class ContextMenuAction {

        private final String label;
        private final boolean enabled;
        private final String feedbackMessage;
        private final ContextMenuActionType actionType;

        private ContextMenuAction(String label, boolean enabled, String feedbackMessage, ContextMenuActionType actionType) {
            this.label = label;
            this.enabled = enabled;
            this.feedbackMessage = feedbackMessage;
            this.actionType = actionType;
        }
    }

    private enum ContextMenuActionType {
        MESSAGE,
        MANAGE_ASSETS,
        CREATE_STATION,
        OPEN_AUTOMATED_STATION_CONFIRM,
        OPEN_AUTOMATED_OUTPOST_CONFIRM
    }

    private static final class ContextMenuLayout {

        private final int left;
        private final int top;
        private final int right;
        private final int bottom;
        private final int headerHeight;
        private final int rowHeight;
        private final List<ContextMenuAction> actions;

        private ContextMenuLayout(int left, int top, int right, int bottom, int headerHeight, int rowHeight,
            List<ContextMenuAction> actions) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.headerHeight = headerHeight;
            this.rowHeight = rowHeight;
            this.actions = actions;
        }
    }

    private static final class ButtonRect {

        private final int left;
        private final int top;
        private final int right;
        private final int bottom;

        private ButtonRect(int left, int top, int right, int bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        private boolean contains(int x, int y) {
            return x >= left && x <= right && y >= top && y <= bottom;
        }
    }

    private static final class ConstructionSiteRow {

        private final CelestialManagedAsset asset;
        private final int left;
        private final int top;
        private final int right;
        private final int bottom;
        private final ButtonRect actionButton;
        private final ConstructionRowActionType actionType;

        private ConstructionSiteRow(CelestialManagedAsset asset, int left, int top, int right, int bottom,
            ButtonRect actionButton, ConstructionRowActionType actionType) {
            this.asset = asset;
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.actionButton = actionButton;
            this.actionType = actionType;
        }
    }

    private enum ConstructionRowActionType {
        CANCEL_BUILD("Cancel Build"),
        SEND_RESOURCES("Send To...");

        private final String buttonLabel;

        ConstructionRowActionType(String buttonLabel) {
            this.buttonLabel = buttonLabel;
        }
    }

    private static final class AssetRow {

        private final CelestialManagedAsset asset;
        private final int left;
        private final int top;
        private final int right;
        private final int bottom;
        private final ButtonRect destroyButton;

        private AssetRow(CelestialManagedAsset asset, int left, int top, int right, int bottom,
            ButtonRect destroyButton) {
            this.asset = asset;
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.destroyButton = destroyButton;
        }
    }

    private static final class AssetManagementLayout {

        private final int left;
        private final int top;
        private final int right;
        private final int bottom;
        private final int assetsSectionTop;
        private final ButtonRect closeButton;
        private final ButtonRect createStationButton;
        private final ButtonRect createAutomatedStationButton;
        private final ButtonRect createOutpostButton;
        private final List<ConstructionSiteRow> siteRows;
        private final List<AssetRow> assetRows;

        private AssetManagementLayout(int left, int top, int right, int bottom, int assetsSectionTop,
            ButtonRect closeButton, ButtonRect createStationButton, ButtonRect createAutomatedStationButton,
            ButtonRect createOutpostButton,
            List<ConstructionSiteRow> siteRows, List<AssetRow> assetRows) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.assetsSectionTop = assetsSectionTop;
            this.closeButton = closeButton;
            this.createStationButton = createStationButton;
            this.createAutomatedStationButton = createAutomatedStationButton;
            this.createOutpostButton = createOutpostButton;
            this.siteRows = siteRows;
            this.assetRows = assetRows;
        }
    }

    private static final class PendingAssetCreation {

        private final String celestialObjectId;
        private final String displayName;
        private final CelestialAssetKind kind;
        private final CelestialAssetLocation location;
        private final List<CelestialAssetRequirement> requiredResources;

        private PendingAssetCreation(String celestialObjectId, String displayName, CelestialAssetKind kind,
            CelestialAssetLocation location, List<CelestialAssetRequirement> requiredResources) {
            this.celestialObjectId = celestialObjectId;
            this.displayName = displayName;
            this.kind = kind;
            this.location = location;
            this.requiredResources = requiredResources;
        }
    }

    private static final class PendingAssetCreationLayout {

        private final int left;
        private final int top;
        private final int right;
        private final int bottom;
        private final ButtonRect cancelButton;
        private final ButtonRect confirmButton;

        private PendingAssetCreationLayout(int left, int top, int right, int bottom, ButtonRect cancelButton,
            ButtonRect confirmButton) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.cancelButton = cancelButton;
            this.confirmButton = confirmButton;
        }
    }

    private static final class PendingAssetDestruction {

        private final CelestialManagedAsset asset;
        private final boolean armed;

        private PendingAssetDestruction(CelestialManagedAsset asset, boolean armed) {
            this.asset = asset;
            this.armed = armed;
        }
    }

    private static final class PendingAssetDestructionLayout {

        private final int left;
        private final int top;
        private final int right;
        private final int bottom;
        private final ButtonRect cancelButton;
        private final ButtonRect destroyButton;

        private PendingAssetDestructionLayout(int left, int top, int right, int bottom, ButtonRect cancelButton,
            ButtonRect destroyButton) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.cancelButton = cancelButton;
            this.destroyButton = destroyButton;
        }
    }

    private static final class PendingConstructionCancellation {

        private final CelestialManagedAsset asset;

        private PendingConstructionCancellation(CelestialManagedAsset asset) {
            this.asset = asset;
        }
    }

    private static final class PendingConstructionCancellationLayout {

        private final int left;
        private final int top;
        private final int right;
        private final int bottom;
        private final ButtonRect cancelButton;
        private final ButtonRect confirmButton;

        private PendingConstructionCancellationLayout(int left, int top, int right, int bottom, ButtonRect cancelButton,
            ButtonRect confirmButton) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.cancelButton = cancelButton;
            this.confirmButton = confirmButton;
        }
    }

    private static final class PendingResourceTransfer {

        private final CelestialManagedAsset asset;
        private final List<StationTransferTarget> targets;

        private PendingResourceTransfer(CelestialManagedAsset asset, List<StationTransferTarget> targets) {
            this.asset = asset;
            this.targets = targets;
        }
    }

    private static final class PendingResourceTransferLayout {

        private final int left;
        private final int top;
        private final int right;
        private final int bottom;
        private final ButtonRect closeButton;
        private final List<TransferTargetRow> rows;

        private PendingResourceTransferLayout(int left, int top, int right, int bottom, ButtonRect closeButton,
            List<TransferTargetRow> rows) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.closeButton = closeButton;
            this.rows = rows;
        }
    }

    private static final class StationTransferTarget {

        private final String assetId;
        private final String displayName;
        private final String hostBodyName;

        private StationTransferTarget(String assetId, String displayName, String hostBodyName) {
            this.assetId = assetId;
            this.displayName = displayName;
            this.hostBodyName = hostBodyName;
        }
    }

    private static final class TransferTargetRow {

        private final StationTransferTarget target;
        private final int left;
        private final int top;
        private final int right;
        private final int bottom;
        private final ButtonRect sendButton;

        private TransferTargetRow(StationTransferTarget target, int left, int top, int right, int bottom,
            ButtonRect sendButton) {
            this.target = target;
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.sendButton = sendButton;
        }
    }

    private static final class InfoRow {

        private final String label;
        private final String value;

        private InfoRow(String label, String value) {
            this.label = label;
            this.value = value;
        }
    }

}
