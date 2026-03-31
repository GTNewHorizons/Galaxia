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
import com.gtnewhorizons.galaxia.orbitalGUI.Hierarchy.AbsolutePosition;
import com.gtnewhorizons.galaxia.orbitalGUI.Hierarchy.OrbitalCelestialBody;
import com.gtnewhorizons.galaxia.orbitalGUI.Hierarchy.OrbitalParams;
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

        if (hoveredBody != null && isVisibleInCurrentLayer(hoveredBody)) {
            drawHoverPanel(hoveredBody);
        }

        if (debugOverlayEnabled) {
            drawDebugOverlay();
        }
    }

    private void drawOrbitsRecursive(OrbitalCelestialBody body, double parentWX, double parentWY) {
        float ellipseAlpha = (float) Math.max(0.0, 1.0 - isometricProgress * 2.5);

        if (!shouldTraverseChildren(body)) {
            return;
        }

        for (OrbitalCelestialBody child : body.children()) {
            if (ellipseAlpha > 0.01f && !usesAbsolutePosition(body, child)) {
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

        if (body.objectClass() != CelestialObjectClass.GALAXY && bodyAlpha > 0.01f) {
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

            float yOffset = getLabelYOffset(body);
            int col = withAlpha(EnumColors.MapCelestialLabelText.getColor(), actualLabelAlpha);
            labelDrawCalls.add(new LabelDrawCall(body.displayName(), sx, sy + yOffset, col));
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
        mc.fontRenderer.drawStringWithShadow(text, Math.round(x - w / 2f), Math.round(y), colour);
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
            mc.fontRenderer.drawStringWithShadow(
                bounds.body.displayName(),
                Math.round(bounds.centerX + bounds.interactionRadius + 4),
                Math.round(bounds.centerY - 4),
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
        int labelY = (int)(sy - box - 26);

        drawSelectionOverlay(sx, sy, box, 1.0f);

        GlStateManager.pushMatrix();
        GlStateManager.enableTexture2D();
        mc.fontRenderer.drawStringWithShadow(
            name,
            (int)(sx - mc.fontRenderer.getStringWidth(name) / 2f),
            labelY,
            0xFFFFFFFF
        );
        GlStateManager.popMatrix();
    }

    private void drawHoverHighlight(OrbitalCelestialBody body) {
        ScreenBodyBounds bounds = findScreenBodyBounds(body);
        if (bounds == null) return;

        float sx = bounds.centerX;
        float sy = bounds.centerY;
        float box = getSelectionBoxRadius(bounds);

        drawSelectionOverlay(sx, sy, box, 0.45f);
    }

    private void drawHoverPanel(OrbitalCelestialBody body) {
        Minecraft mc = Minecraft.getMinecraft();
        List<InfoRow> rows = buildHoverInfoRows(body);
        int widest = 0;
        for (InfoRow row : rows) {
            widest = Math.max(widest, mc.fontRenderer.getStringWidth(row.label));
            widest = Math.max(widest, mc.fontRenderer.getStringWidth(row.value));
        }
        int lineHeight = 18;
        int boxWidth = Math.max(132, widest + 18);
        int boxHeight = 10 + rows.size() * lineHeight;
        int x = getArea().width - boxWidth - 18;
        int y = Math.max(24, (getArea().height - boxHeight) / 2);

        Gui.drawRect(x, y, x + boxWidth, y + boxHeight, 0xE20E1526);
        Gui.drawRect(x, y, x + boxWidth, y + 3, 0xFF4B55F2);
        Gui.drawRect(x, y + boxHeight - 3, x + boxWidth, y + boxHeight, 0xFF4B55F2);
        Gui.drawRect(x, y, x + 3, y + boxHeight, 0xFF4B55F2);
        Gui.drawRect(x + boxWidth - 3, y, x + boxWidth, y + boxHeight, 0xFF4B55F2);

        int textY = y + 8;
        for (InfoRow row : rows) {
            mc.fontRenderer.drawStringWithShadow(row.label, x + 12, textY, 0xFF5A63FF);
            if (!row.value.isEmpty()) {
                mc.fontRenderer.drawStringWithShadow(row.value, x + 12, textY + 9, 0xFFD9E0FF);
            }
            textY += lineHeight;
        }
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

    private float getInteractionRadius(OrbitalCelestialBody body) {
        return Math.max(5f, getRenderedBodyRadius(body));
    }

    private boolean isOnScreen(float sx, float sy, float radius) {
        return sx >= 0 && sy >= 0 && sx <= getArea().width && sy <= getArea().height;
    }

    private float getLabelYOffset(OrbitalCelestialBody body) {
        return getRenderedBodyRadius(body) + 6f;
    }

    private List<InfoRow> buildHoverInfoRows(OrbitalCelestialBody body) {
        List<InfoRow> rows = new ArrayList<>();
        rows.add(new InfoRow("Name", body.displayName()));
        rows.add(new InfoRow("Type", formatObjectClass(body)));
        rows.add(new InfoRow("Landable?", isLandable(body) ? "Yes" : "No"));
        addDangerRows(rows, body);
        rows.add(new InfoRow("Surface Type", formatSurfaceType(body)));
        rows.add(new InfoRow("Ores", "Later"));
        return rows;
    }

    private String formatObjectClass(OrbitalCelestialBody body) {
        String raw = body.objectClass().name().toLowerCase().replace('_', ' ');
        return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
    }

    private boolean isLandable(OrbitalCelestialBody body) {
        return switch (body.objectClass()) {
            case PLANET, MOON, ASTEROID -> body.properties().visitable();
            default -> false;
        };
    }

    private void addDangerRows(List<InfoRow> rows, OrbitalCelestialBody body) {
        List<String> dangers = new ArrayList<>();
        if (body.properties().radiation() >= 0.25) {
            dangers.add("Radiation");
        }
        if (body.properties().temperature() > 360) {
            dangers.add("Extreme heat");
        }
        if (body.properties().temperature() > 0 && body.properties().temperature() < 120) {
            dangers.add("Extreme cold");
        }
        if (!body.properties().visitable() && body.properties().supportsAutomatedOutposts()) {
            dangers.add("Remote only");
        }
        if (dangers.isEmpty()) {
            rows.add(new InfoRow("Dangers", "None"));
            return;
        }
        rows.add(new InfoRow("Dangers", dangers.get(0)));
        for (int i = 1; i < dangers.size(); i++) {
            rows.add(new InfoRow("", dangers.get(i)));
        }
    }

    private String formatSurfaceType(OrbitalCelestialBody body) {
        String oreProfile = body.properties().oreProfile();
        if (oreProfile == null || oreProfile.isEmpty()) {
            return body.objectClass() == CelestialObjectClass.STATION ? "Artificial" : "Unknown";
        }
        String[] parts = oreProfile.split("_");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
        }
        return out.toString();
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

    private static final class InfoRow {

        private final String label;
        private final String value;

        private InfoRow(String label, String value) {
            this.label = label;
            this.value = value;
        }
    }

}
