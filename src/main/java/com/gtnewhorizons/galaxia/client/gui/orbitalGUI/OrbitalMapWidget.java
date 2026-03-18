package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import java.util.List;

import com.gtnewhorizons.galaxia.client.EnumTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;

import org.lwjgl.opengl.GL11;

import com.cleanroommc.modularui.api.UpOrDown;
import com.cleanroommc.modularui.api.widget.IGuiAction;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.utils.GlStateManager;
import com.cleanroommc.modularui.widget.Widget;
import com.gtnewhorizons.galaxia.orbitalGUI.Hierarchy.OrbitalCelestialBody;
import com.gtnewhorizons.galaxia.orbitalGUI.Hierarchy.OrbitalParams;
import com.gtnewhorizons.galaxia.utility.EnumColors;

public class OrbitalMapWidget extends Widget<OrbitalMapWidget> {

    private final OrbitalCelestialBody root;

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
    private boolean isFollowing = false;

    private double isometricProgress = 0.0;
    private double targetIsometricProgress = 0.0;

    private OrbitalCelestialBody pendingFocusBody = null;

    private static final double ZOOM_BASE = 1.18;
    private static final double BASE_SCALE = 82.0;
    private static final double LERP_SPEED = 0.06;
    private static final double KEPLER_BASE = 0.42;

    private static final float ISO_BASE_CUBE_SIZE = 42f;
    private static final float ISO_SPACING = 90f;
    private static final float ISO_OFFSET = 110f;
    private static final float ISO_Y_OFFSET = 20f;

    private static final double CONVERGE_THRESHOLD = 0.001;

    public OrbitalMapWidget(OrbitalCelestialBody root) {
        this.root = root;
    }

    @Override
    public void onInit() {
        super.onInit();

        listenGuiAction(
            (IGuiAction.MouseScroll) (direction, amount) ->
                handleMouseWheel(direction, getContext().getMouseX(), getContext().getMouseY()));

        listenGuiAction(
            (IGuiAction.MouseDrag) (mouseButton, time) ->
                handleMouseDragged(getContext().getMouseX(), getContext().getMouseY(), mouseButton, time));

        listenGuiAction((IGuiAction.MouseReleased) mouseButton -> {
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
        return false;
    }

    private boolean handleMouseWheel(UpOrDown dir, int mx, int my) {
        int sign = dir.isUp() ? 1 : dir.isDown() ? -1 : 0;
        if (sign == 0) return false;

        double oldScale = getScale();
        zoomLevel = Math.max(-7000.0, Math.min(14000.0, zoomLevel + sign * 0.78));

        int lx = mx - getArea().rx;
        int ly = my - getArea().ry;
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
        int lx = mx - getArea().rx;
        int ly = my - getArea().ry;
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
        double a = p.semiMajorAxis();
        if (a < 1e-8) return new double[] { 0.0, 0.0 };

        double n = KEPLER_BASE * Math.pow(a, -1.5);
        double M = p.meanAnomalyAtEpoch() + n * t;
        double e = p.eccentricity();

        double E = M;
        for (int i = 0; i < 8; i++) E = M + e * Math.sin(E);

        double nu = 2.0 * Math.atan2(Math.sqrt(1.0 + e) * Math.sin(E / 2.0), Math.sqrt(1.0 - e) * Math.cos(E / 2.0));
        double r = a * (1.0 - e * e) / (1.0 + e * Math.cos(nu));
        double ag = nu + p.argumentOfPeriapsis();
        return new double[] { r * Math.cos(ag), r * Math.sin(ag) };
    }

    private double getScale() {
        return BASE_SCALE * Math.pow(ZOOM_BASE, zoomLevel);
    }

    private float worldToScreenX(double wx) {
        return (float) ((wx - cameraX) * getScale() + getArea().width / 2.0);
    }

    private float worldToScreenY(double wy) {
        return (float) ((wy - cameraY) * getScale() + getArea().height / 2.0);
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
            return (float) Math.max(12.0, body.spriteSize() * getScale());
        }
        return 12f;
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

        boolean goIso = (body != root);
        targetIsometricProgress = goIso ? 1.0 : 0.0;

        if (!goIso) {
            double maxSize = 0;
            for (OrbitalCelestialBody c : body.children()) {
                maxSize = Math.max(maxSize, c.orbitalParams().apogee());
            }
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

    private double[] getAbsoluteWorldPos(OrbitalCelestialBody target) {
        return getAbsoluteWorldPos(root, target, 0.0, 0.0);
    }

    private double[] getAbsoluteWorldPos(OrbitalCelestialBody cur, OrbitalCelestialBody target, double wx, double wy) {
        if (cur == target) return new double[] { wx, wy };
        double cx = (cur == root) ? 0.0 : wx;
        double cy = (cur == root) ? 0.0 : wy;
        for (OrbitalCelestialBody child : cur.children()) {
            double[] local = calculatePosition(child.orbitalParams(), globalTime);
            double[] res = getAbsoluteWorldPos(child, target, cx + local[0], cy + local[1]);
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
        drawOrbitsRecursive(root, 0.0, 0.0);
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(1f);

        GlStateManager.enableTexture2D();
        drawBodiesRecursive(root, 0.0, 0.0);

        float labelAlpha = (float) Math.max(0.0, 1.0 - isometricProgress * 2.5);
        if (labelAlpha > 0.02f) {
            GlStateManager.color(1f, 1f, 1f, 1f);
            drawLabelsRecursive(root, 0.0, 0.0, labelAlpha);
        }

        GlStateManager.enableTexture2D();
        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.popMatrix();

        String speedText = paused ? StatCollector.translateToLocal("galaxia.gui.orbital.paused")
            : StatCollector.translateToLocalFormatted("galaxia.gui.orbital.speed_multiplier", timeScale);
        Minecraft.getMinecraft().fontRenderer.drawStringWithShadow(
            StatCollector.translateToLocalFormatted("galaxia.gui.orbital.status", getScale(), speedText),
            12,
            12,
            EnumColors.MapStatusText.getColor());

        if (focusedBody != null) drawSelectionHighlight(focusedBody);
    }

    private void drawOrbitsRecursive(OrbitalCelestialBody body, double parentWX, double parentWY) {
        double pX = (body == root) ? 0.0 : parentWX;
        double pY = (body == root) ? 0.0 : parentWY;

        float ellipseAlpha = (float) Math.max(0.0, 1.0 - isometricProgress * 2.5);

        for (OrbitalCelestialBody child : body.children()) {
            if (ellipseAlpha > 0.01f) {
                drawEllipse(child.orbitalParams(), pX, pY, ellipseAlpha);
            }
            double[] local = calculatePosition(child.orbitalParams(), globalTime);
            drawOrbitsRecursive(child, pX + local[0], pY + local[1]);
        }
    }

    private void drawBodiesRecursive(OrbitalCelestialBody body, double parentWX, double parentWY) {
        double wx, wy;
        if (body == root) {
            wx = 0.0;
            wy = 0.0;
        } else {
            double[] local = calculatePosition(body.orbitalParams(), globalTime);
            wx = parentWX + local[0];
            wy = parentWY + local[1];
        }

        float[] isoPos = getIsometricScreenPos(body);
        float sx = (float) lerp(worldToScreenX(wx), isoPos[0], isometricProgress);
        float sy = (float) lerp(worldToScreenY(wy), isoPos[1], isometricProgress);

        float bodyAlpha;
        if (isometricProgress < 0.01) {
            bodyAlpha = 1f;
        } else if (isImportantInIsoMode(body)) {
            bodyAlpha = 1f;
        } else {
            bodyAlpha = (float) Math.max(0.0, 1.0 - isometricProgress * 3.0);
        }

        if (bodyAlpha > 0.01f) {
            if (body.texture() != null && body.spriteSize() > 0.0001) {
                float spriteAlpha = bodyAlpha * (float) (1.0 - isometricProgress);
                float cubeAlpha = bodyAlpha * (float) isometricProgress;

                float spriteR = getSpriteRadius(body);
                float cubeSize = getCubeSizeForBody(body);
                float finalSize = lerp(spriteR * 2f, cubeSize, (float) isometricProgress);

                if (spriteAlpha > 0.01f) drawSprite(body.texture(), sx, sy, spriteR, spriteAlpha);
                if (cubeAlpha > 0.01f) drawCube3D(body.texture(), sx, sy, finalSize, cubeAlpha);
            }
        }

        for (OrbitalCelestialBody child : body.children()) {
            drawBodiesRecursive(child, wx, wy);
        }
    }

    private void drawLabelsRecursive(OrbitalCelestialBody body, double parentWX, double parentWY, float labelAlpha) {
        double wx, wy;
        if (body == root) {
            wx = 0.0;
            wy = 0.0;
        } else {
            double[] local = calculatePosition(body.orbitalParams(), globalTime);
            wx = parentWX + local[0];
            wy = parentWY + local[1];
        }

        if (body != root) {
            float[] isoPos = getIsometricScreenPos(body);
            float sx = (float) lerp(worldToScreenX(wx), isoPos[0], isometricProgress);
            float sy = (float) lerp(worldToScreenY(wy), isoPos[1], isometricProgress);

            float actualLabelAlpha = labelAlpha;
            if (isometricProgress >= 0.01 && !isImportantInIsoMode(body)) {
                actualLabelAlpha *= (float) Math.max(0.0, 1.0 - isometricProgress * 3.0);
            }

            float cubeSize = getCubeSizeForBody(body);
            float yOffset = (float) lerp(14.0, cubeSize / 2f + 8.0, (float) isometricProgress);
            int col = withAlpha(EnumColors.MapCelestialLabelText.getColor(), actualLabelAlpha);
            drawCenteredString(body.name(), sx, sy + yOffset, col);
        }

        for (OrbitalCelestialBody child : body.children()) {
            drawLabelsRecursive(child, wx, wy, labelAlpha);
        }
    }

    private void drawSprite(ResourceLocation tex, float x, float y, float radius, float alpha) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(tex);
        GlStateManager.enableTexture2D();
        GlStateManager.color(1f, 1f, 1f, alpha);
        Tessellator tess = Tessellator.instance;
        tess.startDrawingQuads();
        tess.addVertexWithUV(x - radius, y - radius, 0, 0, 0);
        tess.addVertexWithUV(x + radius, y - radius, 0, 1, 0);
        tess.addVertexWithUV(x + radius, y + radius, 0, 1, 1);
        tess.addVertexWithUV(x - radius, y + radius, 0, 0, 1);
        tess.draw();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    private void drawCube3D(ResourceLocation tex, float cx, float cy, float size, float alpha) {
        float rotX = 30f;
        float rotY = 45f;
        float rotZ = 0f;

        Minecraft.getMinecraft().getTextureManager().bindTexture(tex);

        GL11.glPushMatrix();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_DEPTH_TEST);

        GL11.glTranslatef(cx, cy, 0f);
        GL11.glRotatef(rotX, 1, 0, 0);
        GL11.glRotatef(rotY, 0, 1, 0);
        GL11.glRotatef(rotZ, 0, 0, 1);

        float half = size / 2f;
        Tessellator tess = Tessellator.instance;

        GL11.glColor4f(1f, 1f, 1f, alpha);
        tess.startDrawingQuads();
        tess.addVertexWithUV(-half, -half, half, 0, 0);
        tess.addVertexWithUV(half, -half, half, 1, 0);
        tess.addVertexWithUV(half, half, half, 1, 1);
        tess.addVertexWithUV(-half, half, half, 0, 1);
        tess.draw();

        GL11.glColor4f(1f, 1f, 1f, alpha);
        tess.startDrawingQuads();
        tess.addVertexWithUV(half, -half, -half, 0, 0);
        tess.addVertexWithUV(-half, -half, -half, 1, 0);
        tess.addVertexWithUV(-half, half, -half, 1, 1);
        tess.addVertexWithUV(half, half, -half, 0, 1);
        tess.draw();

        GL11.glColor4f(1f, 1f, 1f, alpha);
        tess.startDrawingQuads();
        tess.addVertexWithUV(-half, -half, -half, 0, 0);
        tess.addVertexWithUV(-half, -half, half, 1, 0);
        tess.addVertexWithUV(-half, half, half, 1, 1);
        tess.addVertexWithUV(-half, half, -half, 0, 1);
        tess.draw();

        GL11.glColor4f(0.5f, 0.5f, 0.5f, alpha);
        tess.startDrawingQuads();
        tess.addVertexWithUV(half, -half, half, 0, 0);
        tess.addVertexWithUV(half, -half, -half, 1, 0);
        tess.addVertexWithUV(half, half, -half, 1, 1);
        tess.addVertexWithUV(half, half, half, 0, 1);
        tess.draw();

        GL11.glColor4f(0.7f, 0.7f, 0.7f, alpha);
        tess.startDrawingQuads();
        tess.addVertexWithUV(-half, half, half, 0, 0);
        tess.addVertexWithUV(half, half, half, 1, 0);
        tess.addVertexWithUV(half, half, -half, 1, 1);
        tess.addVertexWithUV(-half, half, -half, 0, 1);
        tess.draw();

        GL11.glColor4f(1f, 1f, 1f, alpha);
        tess.startDrawingQuads();
        tess.addVertexWithUV(-half, -half, -half, 0, 0);
        tess.addVertexWithUV(half, -half, -half, 1, 0);
        tess.addVertexWithUV(half, -half, half, 1, 1);
        tess.addVertexWithUV(-half, -half, half, 0, 1);
        tess.draw();

        GL11.glPopMatrix();
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

    private void drawCenteredString(String text, float x, float y, int colour) {
        Minecraft mc = Minecraft.getMinecraft();
        int w = mc.fontRenderer.getStringWidth(text);
        mc.fontRenderer.drawStringWithShadow(text, (int) (x - w / 2f), (int) y, colour);
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
        double[] worldPos = getAbsoluteWorldPos(body);
        if (worldPos == null) return;

        float[] isoPos = getIsometricScreenPos(body);
        float sx = (float) lerp(worldToScreenX(worldPos[0]), isoPos[0], isometricProgress);
        float sy = (float) lerp(worldToScreenY(worldPos[1]), isoPos[1], isometricProgress);

        float spriteR = getSpriteRadius(body);
        float cubeR = getCubeSizeForBody(body) * 0.6f;
        float box = lerp(spriteR, cubeR, (float) isometricProgress) + 4f;

        Minecraft mc = Minecraft.getMinecraft();
        String name = body.name();
        int labelY = (int)(sy - box - 26);

        drawSelectionOverlay(sx, sy, box, labelY);

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

    private void drawSelectionOverlay(float centerX, float centerY, float boxSize, int labelY) {
        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.translate(centerX, centerY, 0);
        GuiDraw.drawTexture(EnumTextures.SELECTION_FRAME.get(), -64, -64, 128, 128, 0, 0, 128, 128);
        GlStateManager.popMatrix();
    }

    private static int withAlpha(int colour, float alpha) {
        int a = Math.max(0, Math.min(255, (int) (((colour >> 24) & 0xFF) * alpha)));
        return (colour & 0x00FFFFFF) | (a << 24);
    }
}
