package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;

import org.lwjgl.opengl.GL11;

import com.cleanroommc.modularui.api.UpOrDown;
import com.cleanroommc.modularui.api.widget.IGuiAction;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.utils.GlStateManager;
import com.cleanroommc.modularui.widget.Widget;
import com.gtnewhorizons.galaxia.orbitalGUI.Hierarchy.OrbitalCelestialBody;
import com.gtnewhorizons.galaxia.orbitalGUI.Hierarchy.OrbitalParams;
import com.gtnewhorizons.galaxia.utility.EnumColors;

public class OrbitalMapWidget extends Widget {

    private final OrbitalCelestialBody root;
    private static final boolean DEBUG = true;
    private long lastPositionDebug = 0;

    private double cameraX = 0, cameraY = 0;
    private double zoomLevel = -0.8;
    private double targetCameraX = 0, targetCameraY = 0;
    private double targetZoomLevel = -0.8;

    private boolean isFocusing = false;
    private boolean dragging = false;
    private double lastMouseX, lastMouseY;

    private double globalTime = 0.0;
    private double timeScale = 42.0;
    private boolean paused = false;
    private long lastFrameTime = System.currentTimeMillis();
    private OrbitalCelestialBody focusedBody = null;
    private boolean isFollowing = false;

    private static final double ZOOM_BASE = 1.18;
    private static final double BASE_SCALE = 82.0;
    private static final double LERP_SPEED = 0.18;
    private static final double KEPLER_BASE = 0.42;

    public OrbitalMapWidget(OrbitalCelestialBody root) {
        this.root = root;
        this.targetCameraX = cameraX;
        this.targetCameraY = cameraY;
        this.targetZoomLevel = zoomLevel;
    }

    @Override
    public void onInit() {
        super.onInit();

        listenGuiAction(
            (IGuiAction.MouseScroll) (direction,
                amount) -> handleMouseWheel(direction, getContext().getMouseX(), getContext().getMouseY()));

        listenGuiAction(
            (IGuiAction.MouseDrag) (mouseButton,
                time) -> handleMouseDragged(getContext().getMouseX(), getContext().getMouseY(), mouseButton, time));

        listenGuiAction(
            (IGuiAction.MouseReleased) mouseButton -> handleMouseReleased(
                getContext().getMouseX(),
                getContext().getMouseY(),
                mouseButton));

        listenGuiAction((IGuiAction.KeyPressed) this::handleKeyPressed);
    }

    private boolean handleKeyPressed(char typedChar, int keyCode) {
        if (keyCode == 57) {
            paused = !paused;
            return true;
        }
        if (typedChar == '+' || typedChar == '=') {
            timeScale *= 1.35;
            timeScale = Math.min(timeScale, 800000.0);
            return true;
        }
        if (typedChar == '-') {
            timeScale /= 1.35;
            timeScale = Math.max(timeScale, 0.01);
            return true;
        }
        return false;
    }

    private void updateSimulationTime() {
        if (paused) return;

        long now = System.currentTimeMillis();
        double delta = (now - lastFrameTime) / 1000.0;
        globalTime += delta * timeScale;
        lastFrameTime = now;
    }

    private double[] calculatePosition(OrbitalParams p, double t) {
        double a = p.semiMajorAxis();
        if (a < 1e-8) return new double[] { 0.0, 0.0 };

        double n = KEPLER_BASE * Math.pow(a, -1.5);
        double M = p.meanAnomalyAtEpoch() + n * t;

        double e = p.eccentricity();
        double E = M;
        for (int i = 0; i < 8; i++) {
            E = M + e * Math.sin(E);
        }

        double trueAnomaly = 2 * Math.atan(Math.sqrt((1 + e) / (1 - e)) * Math.tan(E / 2));

        double r = a * (1 - e * e) / (1 + e * Math.cos(trueAnomaly));
        double angle = trueAnomaly + p.argumentOfPeriapsis();

        return new double[] { r * Math.cos(angle), r * Math.sin(angle) };
    }

    private boolean handleMouseWheel(UpOrDown direction, int mouseX, int mouseY) {
        int multiplier = direction.isUp() ? 1 : direction.isDown() ? -1 : 0;
        if (multiplier == 0) return false;

        double oldScale = getScale();
        zoomLevel += multiplier * 0.78;
        zoomLevel = Math.max(-7000.0, Math.min(14000.0, zoomLevel));

        int localX = mouseX - getArea().rx;
        int localY = mouseY - getArea().ry;

        double worldMouseX = cameraX + (localX - getArea().width / 2.0) / oldScale;
        double worldMouseY = cameraY + (localY - getArea().height / 2.0) / oldScale;

        double newScale = getScale();
        cameraX = worldMouseX - (localX - getArea().width / 2.0) / newScale;
        cameraY = worldMouseY - (localY - getArea().height / 2.0) / newScale;

        targetCameraX = cameraX;
        targetCameraY = cameraY;
        targetZoomLevel = zoomLevel;
        isFollowing = false;
        return true;
    }

    private boolean handleMouseDragged(int mouseX, int mouseY, int button, long time) {
        if (button == 0) {
            int localX = mouseX - getArea().rx;
            int localY = mouseY - getArea().ry;

            if (!dragging) {
                dragging = true;
                lastMouseX = localX;
                lastMouseY = localY;
            }
            cameraX -= (localX - lastMouseX) / getScale();
            cameraY -= (localY - lastMouseY) / getScale();
            lastMouseX = localX;
            lastMouseY = localY;

            targetCameraX = cameraX;
            targetCameraY = cameraY;
            isFollowing = false;
            return true;
        }
        return false;
    }

    private boolean handleMouseReleased(int mouseX, int mouseY, int button) {
        dragging = false;
        return false;
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

    @Override
    public void drawBackground(ModularGuiContext context, WidgetThemeEntry widgetTheme) {
        updateSimulationTime();

        if (isFollowing && focusedBody != null) {
            double[] pos = getAbsoluteWorldPos(focusedBody);
            if (pos != null) {
                targetCameraX = pos[0];
                targetCameraY = pos[1];
            }
        }

        super.drawBackground(context, widgetTheme);

        if (isFocusing) {
            cameraX = cameraX * (1 - LERP_SPEED) + targetCameraX * LERP_SPEED;
            cameraY = cameraY * (1 - LERP_SPEED) + targetCameraY * LERP_SPEED;
            zoomLevel = zoomLevel * (1 - LERP_SPEED) + targetZoomLevel * LERP_SPEED;
        } else {
            cameraX = targetCameraX;
            cameraY = targetCameraY;
            zoomLevel = targetZoomLevel;
        }

        Gui.drawRect(0, 0, getArea().width, getArea().height, EnumColors.MapBackground.getColor());

        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GL11.glEnable(GL11.GL_LINE_SMOOTH);

        drawTree(root, 0, 0, globalTime);

        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();

        String speedText = paused ? StatCollector.translateToLocal("galaxia.gui.orbital.paused")
            : StatCollector.translateToLocalFormatted("galaxia.gui.orbital.speed_multiplier", timeScale);

        String status = StatCollector.translateToLocalFormatted("galaxia.gui.orbital.status", getScale(), speedText);

        Minecraft.getMinecraft().fontRenderer.drawStringWithShadow(status, 12, 12, EnumColors.MapStatusText.getColor());

        if (DEBUG) drawDebugOverlay();
    }

    private void drawTree(OrbitalCelestialBody body, double parentWX, double parentWY, double t) {
        double wx, wy;

        if (body == root) {
            wx = 0.0;
            wy = 0.0;
        } else {
            drawEllipse(body.orbitalParams(), parentWX, parentWY);
            double[] pos = calculatePosition(body.orbitalParams(), t);
            wx = parentWX + pos[0];
            wy = parentWY + pos[1];
        }

        float sx = worldToScreenX(wx);
        float sy = worldToScreenY(wy);

        if (body.texture() != null && body.spriteSize() > 0.0001) {
            drawSprite(body.texture(), sx, sy, body.spriteSize());
        } else {
            int color = switch (body.type()) {
                case BLACK_HOLE -> EnumColors.MapCelestialBlackHole.getColor();
                case STAR -> EnumColors.MapCelestialStar.getColor();
                case PLANET -> EnumColors.MapCelestialPlanet.getColor();
                case MOON -> EnumColors.MapCelestialMoon.getColor();
                default -> EnumColors.MapCelestialDefault.getColor();
            };
            drawFilledCircle(sx, sy, body == root ? 11 : 7, color);
        }

        drawCenteredString(body.name(), sx, sy + 14, EnumColors.MapCelestialLabelText.getColor());

        for (OrbitalCelestialBody child : body.children()) {
            drawTree(child, wx, wy, t);
        }
    }

    // TODO fix icon rendering (they are just invisible rn)
    private void drawSprite(ResourceLocation texture, float x, float y, double worldRadius) {
        float radius = (float) (worldRadius * getScale());
        if (radius < 12f) radius = 12f;

        Minecraft mc = Minecraft.getMinecraft();
        mc.getTextureManager()
            .bindTexture(texture);

        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);

        float half = radius;
        Tessellator tess = Tessellator.instance;

        tess.startDrawingQuads();
        tess.addVertexWithUV(x - half, y - half, 0, 0, 0); // TL
        tess.addVertexWithUV(x + half, y - half, 0, 1, 0); // TR
        tess.addVertexWithUV(x + half, y + half, 0, 1, 1); // BR
        tess.addVertexWithUV(x - half, y + half, 0, 0, 1); // BL
        tess.draw();
    }

    private void drawFilledCircle(float x, float y, float r, int color) {
        GlStateManager.color(
            ((color >> 16) & EnumColors.Transparent.getColor()) / 255f,
            ((color >> 8) & EnumColors.Transparent.getColor()) / 255f,
            (color & EnumColors.Transparent.getColor()) / 255f,
            1f);
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glVertex2f(x, y);
        for (int i = 0; i <= 32; i++) {
            double a = i * Math.PI * 2 / 32;
            GL11.glVertex2f(x + (float) Math.cos(a) * r, y + (float) Math.sin(a) * r);
        }
        GL11.glEnd();
    }

    private void drawCenteredString(String text, float x, float y, int color) {
        Minecraft mc = Minecraft.getMinecraft();
        int w = mc.fontRenderer.getStringWidth(text);
        mc.fontRenderer.drawStringWithShadow(text, (int) (x - w / 2f), (int) y, color);
    }

    private void drawEllipse(OrbitalParams p, double parentX, double parentY) {
        double a = p.semiMajorAxis();
        double e = p.eccentricity();
        double b = a * Math.sqrt(Math.max(0, 1 - e * e));
        double rot = p.argumentOfPeriapsis();

        float lineWidth = (float) Math.max(1.8, getScale() * 0.035);
        GL11.glLineWidth(lineWidth);

        GlStateManager.disableTexture2D();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 0.92f);

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
        GL11.glLineWidth(1f);
    }

    public void focusOn(OrbitalCelestialBody body) {
        this.focusedBody = body;
        this.isFollowing = true;
        this.isFocusing = true;

        double[] pos = getAbsoluteWorldPos(body);
        targetCameraX = pos[0];
        targetCameraY = pos[1];

        if (body == root) {
            double maxSize = 0;
            for (OrbitalCelestialBody child : body.children()) {
                maxSize = Math.max(
                    maxSize,
                    child.orbitalParams()
                        .apogee());
            }
            if (maxSize > 1e-9) {
                double desiredScale = 420.0 / maxSize;
                targetZoomLevel = Math.log(desiredScale / BASE_SCALE) / Math.log(ZOOM_BASE);
            } else {
                targetZoomLevel = -0.8;
            }
        } else {
            double apogee = body.orbitalParams()
                .apogee();
            if (apogee > 1e-9) {
                double desiredScale = 650.0 / apogee;
                targetZoomLevel = Math.log(desiredScale / BASE_SCALE) / Math.log(ZOOM_BASE);
            } else {
                targetZoomLevel = 8.0;
            }
        }

        targetZoomLevel = Math.max(-7000.0, Math.min(14000.0, targetZoomLevel));
    }

    private double[] getAbsoluteWorldPos(OrbitalCelestialBody target) {
        return getAbsoluteWorldPos(root, target, 0.0, 0.0, globalTime);
    }

    private double[] getAbsoluteWorldPos(OrbitalCelestialBody current, OrbitalCelestialBody target, double wx,
        double wy, double t) {
        if (current == target) {
            return new double[] { wx, wy };
        }

        double curX = (current == root) ? 0.0 : wx;
        double curY = (current == root) ? 0.0 : wy;

        for (OrbitalCelestialBody child : current.children()) {
            double[] local = calculatePosition(child.orbitalParams(), t);
            double[] res = getAbsoluteWorldPos(child, target, curX + local[0], curY + local[1], t);
            if (res != null) return res;
        }
        return null;
    }

    private void drawDebugOverlay() {
        Minecraft mc = Minecraft.getMinecraft();
        int y = 40;

        mc.fontRenderer.drawStringWithShadow(
            StatCollector.translateToLocal("galaxia.gui.orbital.debug.title"),
            12,
            y,
            EnumColors.DebugOverlayTitle.getColor());
        y += 14;

        mc.fontRenderer.drawStringWithShadow(
            StatCollector.translateToLocalFormatted("galaxia.gui.orbital.debug.camera", cameraX, cameraY),
            12,
            y,
            EnumColors.DebugOverlayInfo.getColor());
        y += 12;

        String follow = (isFollowing && focusedBody != null) ? focusedBody.name()
            : StatCollector.translateToLocal("galaxia.gui.orbital.debug.none");

        mc.fontRenderer.drawStringWithShadow(
            StatCollector.translateToLocalFormatted("galaxia.gui.orbital.debug.following", follow, paused),
            12,
            y,
            EnumColors.DebugOverlayFollow.getColor());
        y += 12;

        mc.fontRenderer.drawStringWithShadow(
            StatCollector.translateToLocalFormatted("galaxia.gui.orbital.debug.time", globalTime, timeScale),
            12,
            y,
            EnumColors.DebugOverlayInfo.getColor());
        y += 12;

        mc.fontRenderer.drawStringWithShadow(
            StatCollector.translateToLocalFormatted("galaxia.gui.orbital.debug.zoom", zoomLevel, getScale()),
            12,
            y,
            EnumColors.DebugOverlayInfo.getColor());
    }
}
