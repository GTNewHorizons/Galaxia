package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import static org.lwjgl.opengl.Display.getHeight;
import static org.lwjgl.opengl.Display.getWidth;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;

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

    private double cameraX = 0, cameraY = 0;
    private double zoomLevel = -0.8;
    private boolean dragging = false;
    private double lastMouseX, lastMouseY;

    private static final double ZOOM_BASE = 1.18;
    private static final double BASE_SCALE = 82.0;

    public OrbitalMapWidget(OrbitalCelestialBody root) {
        this.root = root;
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
        int multiplier = 0;
        if (direction.isDown()) multiplier = -1;
        if (direction.isUp()) multiplier = 1;
        if (multiplier == 0) return false;

        double oldScale = getScale();
        zoomLevel += multiplier * 0.78;

        zoomLevel = Math.max(-7.0, Math.min(14.0, zoomLevel));

        int localX = mouseX - (int) getArea().rx;
        int localY = mouseY - (int) getArea().ry;

        double worldMouseX = cameraX + (localX - getWidth() / 2.0) / oldScale;
        double worldMouseY = cameraY + (localY - getHeight() / 2.0) / oldScale;

        double newScale = getScale();
        cameraX = worldMouseX - (localX - getWidth() / 2.0) / newScale;
        cameraY = worldMouseY - (localY - getHeight() / 2.0) / newScale;

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
        return (float) ((wx - cameraX) * getScale() + getWidth() / 2.0);
    }

    private float worldToScreenY(double wy) {
        return (float) ((wy - cameraY) * getScale() + getHeight() / 2.0);
    }

    @Override
    public void drawBackground(ModularGuiContext context, WidgetThemeEntry widgetTheme) {
        super.drawBackground(context, widgetTheme);

        Gui.drawRect(0, 0, getWidth(), getHeight(), 0xFF0F1621);

        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();

        drawTree(root, 0, 0);

        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();

        Minecraft.getMinecraft().fontRenderer
            .drawStringWithShadow("Zoom: ×" + String.format("%.2f", getScale()), 12, 12, 0xAAFFFFFF);
    }

    private void drawTree(OrbitalCelestialBody body, double parentWX, double parentWY) {
        if (body != root) {
            drawEllipse(body.orbitalParams(), parentWX, parentWY);
        }

        double[] pos = calculatePosition(body.orbitalParams());
        double wx = parentWX + pos[0];
        double wy = parentWY + pos[1];

        float sx = worldToScreenX(wx);
        float sy = worldToScreenY(wy);

        int color = switch (body.type()) {
            case BLACK_HOLE -> 0xFF111111;
            case STAR -> 0xFFFFEE88;
            case PLANET -> 0xFF44AAFF;
            case MOON -> 0xFFEEEEEE;
            default -> 0xFF00FF99;
        };

        drawFilledCircle(sx, sy, body == root ? 11 : 7, color);

        if (getScale() > 0.6) {
            drawCenteredString(body.name(), sx, sy + 14, 0xFFFFFFFF);
        }

        for (OrbitalCelestialBody child : body.children()) {
            drawTree(child, wx, wy);
        }
    }

    private void drawEllipse(OrbitalParams p, double parentX, double parentY) {
        double a = p.semiMajorAxis();
        double e = p.eccentricity();
        double b = a * Math.sqrt(Math.max(0, 1 - e * e));
        double rot = p.argumentOfPeriapsis();

        GL11.glBegin(GL11.GL_LINE_LOOP);
        GL11.glColor4f(0.4f, 0.4f, 0.45f, 0.7f);

        for (int i = 0; i <= 96; i++) {
            double theta = i * Math.PI * 2 / 96;
            double ex = a * Math.cos(theta);
            double ey = b * Math.sin(theta);

            double rx = ex * Math.cos(rot) - ey * Math.sin(rot);
            double ry = ex * Math.sin(rot) + ey * Math.cos(rot);

            GL11.glVertex2d(worldToScreenX(parentX + rx), worldToScreenY(parentY + ry));
        }
        GL11.glEnd();
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
        mc.fontRenderer
            .drawStringWithShadow(text, (int) (x - mc.fontRenderer.getStringWidth(text) / 2f), (int) y, color);
    }
}
