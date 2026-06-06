package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.editor;

import java.util.function.Supplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.Tessellator;

import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import com.cleanroommc.modularui.api.UpOrDown;
import com.cleanroommc.modularui.api.widget.IGuiAction;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.utils.GlStateManager;
import com.cleanroommc.modularui.widget.Widget;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketBlueprint;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartInstance;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.IRocketPartDef;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.TileEntitySilo;

public class RocketCanvasWidget extends Widget<RocketCanvasWidget> {

    private static final int CELL = 28;
    private static final double MIN_SCALE = 0.35;
    private static final double MAX_SCALE = 6.0;
    private static final double ZOOM_FACTOR_IN = 1.18;
    private static final double ZOOM_FACTOR_OUT = 0.85;
    private static final int DRAG_THRESHOLD = 5;
    private static final float PREVIEW_ALPHA = 0.30f;

    private final RocketBlueprint blueprint;
    private final TileEntitySilo silo;
    private final Minecraft mc = Minecraft.getMinecraft();

    private Supplier<IRocketPartDef> selectedPartSupplier = () -> null;

    private double panX = 40;
    private double panY = 40;
    private double scale = 1.0;

    private boolean lmbDown = false;
    private boolean isDragging = false;
    private double pressMouseX;
    private double pressMouseY;
    private double lastDragMouseX;
    private double lastDragMouseY;

    public RocketCanvasWidget(RocketBlueprint blueprint, TileEntitySilo silo) {
        this.blueprint = blueprint;
        this.silo = silo;

        size(520, 400);

        onUpdateListener(w -> pollDrag(), true);

        listenGuiAction((IGuiAction.MousePressed) button -> {
            if (!isHovering()) return false;

            switch (button) {
                case 0 -> {
                    lmbDown = true;
                    isDragging = false;
                    pressMouseX = localMouseX();
                    pressMouseY = localMouseY();
                    lastDragMouseX = pressMouseX;
                    lastDragMouseY = pressMouseY;
                    return true;
                }
                case 1 -> {
                    // Right-click: remove only while editable
                    if (isEditable()) removePart();
                    return true;
                }
            }

            return false;
        });

        listenGuiAction((IGuiAction.MouseReleased) button -> {
            if (button == 0) {
                if (lmbDown && !isDragging) {
                    // Left-click place only while editable
                    if (isEditable()) placePart();
                }
                lmbDown = false;
                isDragging = false;
                return true;
            }
            return false;
        });

        listenGuiAction((IGuiAction.MouseScroll) (direction, amount) -> {
            if (!isHovering()) return false;

            double factor = direction == UpOrDown.UP ? ZOOM_FACTOR_IN : ZOOM_FACTOR_OUT;
            double oldScale = scale;
            scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale * factor));

            double mx = localMouseX();
            double my = localMouseY();
            panX = mx - (mx - panX) * (scale / oldScale);
            panY = my - (my - panY) * (scale / oldScale);

            return true;
        });
    }

    /** True only when the silo's build state permits canvas modifications. */
    private boolean isEditable() {
        return silo.getBuildStatus()
            .canEdit();
    }

    public void setSelectedPartSupplier(Supplier<IRocketPartDef> supplier) {
        this.selectedPartSupplier = supplier != null ? supplier : () -> null;
    }

    public void resetView() {
        panX = 40;
        panY = 40;
        scale = 1.0;
    }

    @Override
    public void drawBackground(ModularGuiContext ctx, WidgetThemeEntry widgetTheme) {
        Gui.drawRect(0, 0, getArea().width, getArea().height, 0xFF1A1C1E);

        ScaledResolution sr = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        int sf = sr.getScaleFactor();
        int scissorX = getArea().x * sf;
        int scissorY = (sr.getScaledHeight() - getArea().y - getArea().height) * sf;
        int scissorW = getArea().width * sf;
        int scissorH = getArea().height * sf;

        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(scissorX, scissorY, scissorW, scissorH);

        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        GlStateManager.translate(panX, panY, 0);
        GlStateManager.scale(scale, scale, 1.0);

        drawGrid();
        drawParts();
        // Only show placement preview when the canvas is editable
        if (isEditable()) drawHoverPreview();

        GlStateManager.popMatrix();

        // Draw a subtle locked overlay when not editable
        if (!isEditable()) {
            GL11.glColor4f(1f, 1f, 1f, 1f);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            Gui.drawRect(0, 0, getArea().width, getArea().height, 0x44000000);
            return;
        }

        GL11.glColor4f(1f, 1f, 1f, 1f);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    private void drawGrid() {
        Tessellator tes = Tessellator.instance;

        double invScale = 1.0 / scale;
        double worldLeft = -panX * invScale;
        double worldTop = -panY * invScale;
        double worldRight = worldLeft + getArea().width * invScale;
        double worldBottom = worldTop + getArea().height * invScale;

        int startX = (int) Math.floor(worldLeft / CELL) - 1;
        int endX = (int) Math.ceil(worldRight / CELL) + 1;
        int startY = (int) Math.floor(worldTop / CELL) - 1;
        int endY = (int) Math.ceil(worldBottom / CELL) + 1;

        GlStateManager.disableTexture2D();
        GL11.glColor4f(0.30f, 0.33f, 0.36f, 1.0f);
        GL11.glLineWidth(1.0f);

        tes.startDrawing(GL11.GL_LINES);

        for (int x = startX; x <= endX; x++) {
            int px = x * CELL;
            tes.addVertex(px, startY * CELL, 0);
            tes.addVertex(px, endY * CELL, 0);
        }
        for (int y = startY; y <= endY; y++) {
            int py = y * CELL;
            tes.addVertex(startX * CELL, py, 0);
            tes.addVertex(endX * CELL, py, 0);
        }

        tes.draw();
        GL11.glColor4f(1f, 1f, 1f, 1f);
        GlStateManager.enableTexture2D();
    }

    private void drawParts() {
        for (RocketPartInstance part : blueprint.getParts()) {
            drawPartAt(part.def(), part.x(), part.y(), 1.0f);
        }
    }

    private void drawHoverPreview() {
        if (isDragging) return;

        IRocketPartDef sel = selectedPartSupplier.get();
        double localX = getContext().getMouseX();
        double localY = getContext().getMouseY();

        double worldX = (localX - panX) / (CELL * scale);
        double worldY = (localY - panY) / (CELL * scale);

        int cellX = (int) Math.floor(worldX + 1e-9);
        int cellY = (int) Math.floor(worldY + 1e-9);

        int originX = cellX;
        int originY = cellY;

        if (sel != null) {
            originX = centeredOrigin(cellX, sel.width());
            originY = centeredOrigin(cellY, sel.height());
        }

        boolean canPlace = sel == null
            || blueprint.canPlacePart(new RocketPartInstance(sel, originX, originY, 0, false));

        if (sel == null) {
            GlStateManager.disableTexture2D();
            GL11.glColor4f(0.4f, 0.8f, 1.0f, PREVIEW_ALPHA);

            int px = cellX * CELL;
            int py = cellY * CELL;

            Tessellator tes = Tessellator.instance;
            tes.startDrawingQuads();
            tes.addVertex(px, py, 0);
            tes.addVertex(px + CELL, py, 0);
            tes.addVertex(px + CELL, py + CELL, 0);
            tes.addVertex(px, py + CELL, 0);
            tes.draw();

            GlStateManager.enableTexture2D();
            return;
        }

        drawPartAt(sel, originX, originY, PREVIEW_ALPHA, canPlace);
    }

    private void drawPartAt(IRocketPartDef def, int partX, int partY, float alpha) {
        drawPartAt(def, partX, partY, alpha, true);
    }

    private void drawPartAt(IRocketPartDef def, int partX, int partY, float alpha, boolean valid) {
        int px = partX * CELL;
        int py = partY * CELL;
        int pw = def.width() * CELL;
        int ph = def.height() * CELL;

        float r = 1.0f;
        float g = valid ? 1.0f : 0.35f;
        float b = valid ? 1.0f : 0.35f;

        if (def.assetFolder() != null) {
            GL11.glColor4f(r, g, b, alpha);
            mc.renderEngine.bindTexture(def.spriteLocation());

            Tessellator tes = Tessellator.instance;
            tes.startDrawingQuads();
            tes.addVertexWithUV(px, py + ph, 0, 0, 1);
            tes.addVertexWithUV(px + pw, py + ph, 0, 1, 1);
            tes.addVertexWithUV(px + pw, py, 0, 1, 0);
            tes.addVertexWithUV(px, py, 0, 0, 0);
            tes.draw();
            GL11.glColor4f(1f, 1f, 1f, 1f);
            return;
        }

        GlStateManager.disableTexture2D();
        GL11.glColor4f(valid ? 0.85f : 1.0f, valid ? 0.55f : 0.35f, valid ? 0.15f : 0.35f, alpha);

        Tessellator tes = Tessellator.instance;
        tes.startDrawingQuads();
        tes.addVertex(px, py + ph, 0);
        tes.addVertex(px + pw, py + ph, 0);
        tes.addVertex(px + pw, py, 0);
        tes.addVertex(px, py, 0);
        tes.draw();

        GlStateManager.enableTexture2D();
        GL11.glColor4f(1f, 1f, 1f, 1f);
    }

    private void pollDrag() {
        if (!lmbDown || !Mouse.isButtonDown(0)) {
            lmbDown = false;
            isDragging = false;
            return;
        }

        double mx = localMouseX();
        double my = localMouseY();

        if (!isDragging) {
            if (Math.abs(mx - pressMouseX) > DRAG_THRESHOLD || Math.abs(my - pressMouseY) > DRAG_THRESHOLD) {
                isDragging = true;
                lastDragMouseX = mx;
                lastDragMouseY = my;
            }
            return;
        }

        panX += mx - lastDragMouseX;
        panY += my - lastDragMouseY;
        lastDragMouseX = mx;
        lastDragMouseY = my;
    }

    private void placePart() {
        IRocketPartDef def = selectedPartSupplier.get();
        if (def == null) return;

        int originX = centeredOrigin(mouseToCellX(), def.width());
        int originY = centeredOrigin(mouseToCellY(), def.height());

        RocketPartInstance part = new RocketPartInstance(def, originX, originY, 0, false);
        if (blueprint.addPart(part)) {
            silo.sync();
        }
    }

    private void removePart() {
        RocketPartInstance hit = findPartAt(mouseToCellX(), mouseToCellY());
        if (hit != null) {
            blueprint.removePartAt(hit.x(), hit.y(), hit.z());
            silo.sync();
        }
    }

    private RocketPartInstance findPartAt(int cellX, int cellY) {
        for (int i = blueprint.getParts()
            .size() - 1; i >= 0; i--) {
            RocketPartInstance part = blueprint.getParts()
                .get(i);
            IRocketPartDef def = part.def();

            int x1 = part.x(), y1 = part.y();
            int x2 = x1 + def.width(), y2 = y1 + def.height();

            if (cellX >= x1 && cellX < x2 && cellY >= y1 && cellY < y2) {
                return part;
            }
        }
        return null;
    }

    private int centeredOrigin(int cell, int size) {
        return (int) Math.round(cell - (size / 2.0));
    }

    private double localMouseX() {
        return getContext().getMouseX() - getArea().x;
    }

    private double localMouseY() {
        return getContext().getMouseY() - getArea().y;
    }

    private int mouseToCellX() {
        return (int) Math.floor((localMouseX() - panX) / (CELL * scale) + 1e-9);
    }

    private int mouseToCellY() {
        return (int) Math.floor((localMouseY() - panY) / (CELL * scale) + 1e-9);
    }
}
