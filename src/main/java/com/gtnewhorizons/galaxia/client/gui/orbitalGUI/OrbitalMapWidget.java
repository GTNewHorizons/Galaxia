package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.cleanroommc.modularui.api.UpOrDown;
import com.cleanroommc.modularui.api.widget.IGuiAction;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.utils.GlStateManager;
import com.cleanroommc.modularui.widget.Widget;
import com.gtnewhorizons.galaxia.orbitalGUI.Hierarchy.OrbitalCelestialBody;
import com.gtnewhorizons.galaxia.orbitalGUI.Hierarchy.OrbitalParams;

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

    private static final double ZOOM_BASE = 1.18;
    private static final double BASE_SCALE = 82.0;
    private static final double LERP_SPEED = 0.18;

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
    }

    private boolean handleMouseWheel(UpOrDown direction, int mouseX, int mouseY) {
        int multiplier = direction.isUp() ? 1 : direction.isDown() ? -1 : 0;
        if (multiplier == 0) return false;

        double oldScale = getScale();
        zoomLevel += multiplier * 0.78;
        zoomLevel = Math.max(-7000.0, Math.min(14000.0, zoomLevel));

        int localX = mouseX - (int) getArea().rx;
        int localY = mouseY - (int) getArea().ry;

        double worldMouseX = cameraX + (localX - getArea().width / 2.0) / oldScale;
        double worldMouseY = cameraY + (localY - getArea().height / 2.0) / oldScale;

        double newScale = getScale();
        cameraX = worldMouseX - (localX - getArea().width / 2.0) / newScale;
        cameraY = worldMouseY - (localY - getArea().height / 2.0) / newScale;

        targetCameraX = cameraX;
        targetCameraY = cameraY;
        targetZoomLevel = zoomLevel;
        isFocusing = false;
        return true;
    }

    private boolean handleMouseDragged(int mouseX, int mouseY, int button, long time) {
        if (button == 0) {
            int localX = mouseX - (int) getArea().rx;
            int localY = mouseY - (int) getArea().ry;

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
            isFocusing = false;
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

        Gui.drawRect(0, 0, getArea().width, getArea().height, 0xFF0F1621);

        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GL11.glEnable(GL11.GL_LINE_SMOOTH);

        drawTree(root, 0, 0);

        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();

        Minecraft.getMinecraft().fontRenderer
            .drawStringWithShadow("Zoom: ×" + String.format("%.2f", getScale()), 12, 12, 0xAAFFFFFF);

        if (DEBUG) {
            drawDebugOverlay();
        }
    }

    private void drawTree(OrbitalCelestialBody body, double parentWX, double parentWY) {
        double wx, wy;

        if (body == root) {
            wx = 0.0;
            wy = 0.0;
        } else {
            drawEllipse(body.orbitalParams(), parentWX, parentWY);
            double[] pos = calculatePosition(body.orbitalParams());
            wx = parentWX + pos[0];
            wy = parentWY + pos[1];
        }

        float sx = worldToScreenX(wx);
        float sy = worldToScreenY(wy);

        if (body.texture() != null && body.spriteSize() > 0.0001) {
            drawSprite(body.texture(), sx, sy, body.spriteSize());
        } else {
            int color = switch (body.type()) {
                case BLACK_HOLE -> 0xFF111111;
                case STAR -> 0xFFFFEE88;
                case PLANET -> 0xFF44AAFF;
                case MOON -> 0xFFEEEEEE;
                default -> 0xFF00FF99;
            };
            drawFilledCircle(sx, sy, body == root ? 11 : 7, color);
        }

        drawCenteredString(body.name(), sx, sy + 14, 0xFFFFFFFF);

        if (DEBUG && System.currentTimeMillis() - lastPositionDebug > 2000) {
            System.out.printf(
                "[DEBUG DRAW] %s | World: %.4f, %.4f | Screen: %.1f, %.1f | Scale: %.2f%n",
                body.name(),
                wx,
                wy,
                sx,
                sy,
                getScale());
            lastPositionDebug = System.currentTimeMillis();
        }

        for (OrbitalCelestialBody child : body.children()) {
            drawTree(child, wx, wy);
        }
    }

    private void drawSprite(ResourceLocation texture, float x, float y, double worldRadius) {
        float radius = (float) (worldRadius * getScale());
        if (radius < 6) radius = 6;

        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(texture);

        GlStateManager.enableTexture2D();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);

        float half = radius;

        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0.0f, 0.0f);
        GL11.glVertex2f(x - half, y - half);
        GL11.glTexCoord2f(1.0f, 0.0f);
        GL11.glVertex2f(x + half, y - half);
        GL11.glTexCoord2f(1.0f, 1.0f);
        GL11.glVertex2f(x + half, y + half);
        GL11.glTexCoord2f(0.0f, 1.0f);
        GL11.glVertex2f(x - half, y + half);
        GL11.glEnd();

        GlStateManager.disableTexture2D();
    }

    private double[] calculatePosition(OrbitalParams p) {
        double M = p.meanAnomalyAtEpoch();
        double e = p.eccentricity();
        double E = M;
        for (int i = 0; i < 6; i++) E = M + e * Math.sin(E);

        double trueAnomaly = 2 * Math.atan(Math.sqrt((1 + e) / (1 - e)) * Math.tan(E / 2));

        double r = p.semiMajorAxis() * (1 - e * e) / (1 + e * Math.cos(trueAnomaly));
        double angle = trueAnomaly + p.argumentOfPeriapsis();

        return new double[] { r * Math.cos(angle), r * Math.sin(angle) };
    }

    private void drawFilledCircle(float x, float y, float r, int color) {
        GlStateManager.color(((color >> 16) & 0xFF) / 255f, ((color >> 8) & 0xFF) / 255f, (color & 0xFF) / 255f, 1f);
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
        double[] pos = getAbsoluteWorldPos(body);
        if (pos == null) {
            System.out.println("§c[DEBUG] focusOn FAILED for " + body.name());
            return;
        }

        System.out.println("§6[DEBUG] === FOCUS ON: " + body.name() + " ===");
        System.out.printf("   World position: %.4f, %.4f%n", pos[0], pos[1]);

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
        isFocusing = true;

        System.out.printf(
            "   → Camera target: %.4f, %.4f | ZoomLevel: %.3f | Scale ≈ %.1f%n",
            targetCameraX,
            targetCameraY,
            targetZoomLevel,
            BASE_SCALE * Math.pow(ZOOM_BASE, targetZoomLevel));
        System.out.println("========================================");
    }

    private double[] getAbsoluteWorldPos(OrbitalCelestialBody target) {
        return getAbsoluteWorldPos(root, target, 0.0, 0.0);
    }

    private double[] getAbsoluteWorldPos(OrbitalCelestialBody current, OrbitalCelestialBody target, double wx,
        double wy) {
        if (current == target) {
            return (current == root) ? new double[] { 0.0, 0.0 } : new double[] { wx, wy };
        }

        double currentX = (current == root) ? 0.0 : wx;
        double currentY = (current == root) ? 0.0 : wy;

        for (OrbitalCelestialBody child : current.children()) {
            double[] local = calculatePosition(child.orbitalParams());
            double[] res = getAbsoluteWorldPos(child, target, currentX + local[0], currentY + local[1]);
            if (res != null) return res;
        }
        return null;
    }

    private void drawDebugOverlay() {
        Minecraft mc = Minecraft.getMinecraft();
        int y = 40;
        mc.fontRenderer.drawStringWithShadow("§6=== GALACTIC MAP DEBUG ===", 12, y, 0xFFFF5555);
        y += 14;
        mc.fontRenderer.drawStringWithShadow(String.format("Camera: %.4f, %.4f", cameraX, cameraY), 12, y, 0x88FF88);
        y += 12;
        mc.fontRenderer
            .drawStringWithShadow(String.format("Target: %.4f, %.4f", targetCameraX, targetCameraY), 12, y, 0x88FF88);
        y += 12;
        mc.fontRenderer.drawStringWithShadow(
            String.format("ZoomLevel: %.2f | Scale: %.2f", zoomLevel, getScale()),
            12,
            y,
            0x88FF88);
        y += 12;
        mc.fontRenderer.drawStringWithShadow("Focusing: " + isFocusing + "  |  Dragging: " + dragging, 12, y, 0x88FF88);
    }
}
