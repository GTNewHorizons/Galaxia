package com.gtnewhorizons.galaxia.client.gui.station.layer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.cleanroommc.modularui.screen.viewport.GuiContext;
import com.cleanroommc.modularui.utils.GlStateManager;
import com.gtnewhorizons.galaxia.client.EnumColors;
import com.gtnewhorizons.galaxia.client.gui.orbitalGUI.BorderedRect;
import com.gtnewhorizons.galaxia.client.gui.station.StationMapViewport;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.station.PlacedTile;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationModuleCategory;

public final class ModuleLayerRenderer {

    private ModuleLayerRenderer() {}

    public static void drawOccupied(GuiContext ctx, int x, int y, PlacedTile tile) {
        int size = StationMapViewport.TILE_SIZE;
        if (!drawModuleTexture(x, y, size, moduleKindOf(tile))) {
            int fillColor = categoryColor(categoryOf(tile));
            Gui.drawRect(x, y, x + size, y + size, fillColor);
            drawLabel(ctx, x, y, size, labelOf(tile));
        }
        drawBorder(x, y, size, EnumColors.MAP_COLOR_STATION_TILE_BORDER_DEFAULT.getColor());

        switch (tile.state()) {
            case UNDER_CONSTRUCTION -> Gui
                .drawRect(x, y, x + size, y + size, EnumColors.MAP_COLOR_STATION_TILE_UNDER_CONSTRUCTION.getColor());
            case UNDER_DECONSTRUCTION -> Gui
                .drawRect(x, y, x + size, y + size, EnumColors.MAP_COLOR_STATION_TILE_UNDER_DECONSTRUCTION.getColor());
            case OCCUPIED_DISABLED -> Gui
                .drawRect(x, y, x + size, y + size, EnumColors.MAP_COLOR_STATION_TILE_DISABLED_DIM.getColor());
            case BLOCKED -> Gui
                .drawRect(x, y, x + size, y + size, EnumColors.MAP_COLOR_STATION_TILE_BLOCKED.getColor());
            case OCCUPIED_OPERATIONAL, EMPTY -> {}
        }
    }

    public static void drawExpansionSlot(GuiContext ctx, int x, int y) {
        int size = StationMapViewport.TILE_SIZE;
        Gui.drawRect(x, y, x + size, y + size, EnumColors.MAP_COLOR_STATION_TILE_EMPTY_FILL.getColor());
        drawDashedBorder(x, y, size, EnumColors.MAP_COLOR_STATION_TILE_EMPTY_BORDER.getColor());
    }

    public static void drawPreview(GuiContext ctx, int x, int y, FacilityModuleKind kind) {
        int size = StationMapViewport.TILE_SIZE;
        if (!drawModuleTexture(x, y, size, kind, 0.55f, 0.55f, 0.55f, 0.7f)) {
            Gui.drawRect(x, y, x + size, y + size, EnumColors.MAP_COLOR_STATION_TILE_PREVIEW_FALLBACK_FILL.getColor());
            drawLabel(
                ctx,
                x,
                y,
                size,
                kind == null ? "?"
                    : kind.name()
                        .substring(0, 1));
        }
        Gui.drawRect(x, y, x + size, y + size, EnumColors.MAP_COLOR_STATION_TILE_PREVIEW_DIM.getColor());
        drawBorder(x, y, size, EnumColors.MAP_COLOR_STATION_PICKER_COMPATIBLE.getColor());
    }

    public static boolean drawModuleTextureRegion(int x, int y, int w, int h, FacilityModuleKind kind, float u0,
        float v0, float u1, float v1) {
        return drawModuleTextureRegion(x, y, w, h, kind, u0, v0, u1, v1, 1f, 1f, 1f, 1f);
    }

    private static StationModuleCategory categoryOf(PlacedTile tile) {
        if (tile == null) return StationModuleCategory.COMMAND;
        FacilityModuleKind kind = moduleKindOf(tile);
        return kind == null ? StationModuleCategory.COMMAND : kind.getCategory();
    }

    private static FacilityModuleKind moduleKindOf(PlacedTile tile) {
        if (tile == null) return null;
        ModuleInstance module = tile.module();
        return module == null ? null : module.kind();
    }

    private static int categoryColor(StationModuleCategory category) {
        return switch (category) {
            case COMMAND -> EnumColors.MAP_COLOR_STATION_CATEGORY_COMMAND.getColor();
            case MINING_SUPPORT -> EnumColors.MAP_COLOR_STATION_CATEGORY_MINING_SUPPORT.getColor();
            case LOGISTICS -> EnumColors.MAP_COLOR_STATION_CATEGORY_LOGISTICS.getColor();
            case STORAGE -> EnumColors.MAP_COLOR_STATION_CATEGORY_STORAGE.getColor();
            case POWER -> EnumColors.MAP_COLOR_STATION_CATEGORY_POWER.getColor();
            case PROCESSING -> EnumColors.MAP_COLOR_STATION_CATEGORY_PROCESSING.getColor();
            case HABITATION -> EnumColors.MAP_COLOR_STATION_CATEGORY_HABITATION.getColor();
            case INFRASTRUCTURE -> EnumColors.MAP_COLOR_STATION_CATEGORY_INFRASTRUCTURE.getColor();
            case SUPPORT -> EnumColors.MAP_COLOR_STATION_CATEGORY_SUPPORT.getColor();
        };
    }

    private static String labelOf(PlacedTile tile) {
        if (tile == null) return "";
        ModuleInstance module = tile.module();
        if (module == null) return "C";
        FacilityModuleKind kind = module.kind();
        return kind == null ? "?"
            : kind.name()
                .substring(0, 1);
    }

    private static void drawLabel(GuiContext ctx, int x, int y, int size, String label) {
        if (label.isEmpty()) return;
        FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
        int textWidth = fr.getStringWidth(label);
        int textX = x + (size - textWidth) / 2;
        int textY = y + (size - fr.FONT_HEIGHT) / 2 + 1;
        fr.drawStringWithShadow(label, textX, textY, EnumColors.MAP_COLOR_TEXT_TITLE.getColor());
    }

    private static boolean drawModuleTexture(int x, int y, int size, FacilityModuleKind kind) {
        return drawModuleTexture(x, y, size, kind, 1f, 1f, 1f, 1f);
    }

    private static boolean drawModuleTexture(int x, int y, int size, FacilityModuleKind kind, float red, float green,
        float blue, float alpha) {
        return drawModuleTextureRegion(x, y, size, size, kind, 0f, 0f, 1f, 1f, red, green, blue, alpha);
    }

    private static boolean drawModuleTextureRegion(int x, int y, int w, int h, FacilityModuleKind kind, float u0,
        float v0, float u1, float v1, float red, float green, float blue, float alpha) {
        if (kind == null) return false;
        ResourceLocation texture = StationTextureRegistry.moduleTexture(kind);
        if (!StationTextureRegistry.hasTexture(texture)) return false;
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(texture);
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(red, green, blue, alpha);

        Tessellator tess = Tessellator.instance;
        tess.startDrawingQuads();
        tess.addVertexWithUV(x, y + h, 0, u0, v1);
        tess.addVertexWithUV(x + w, y + h, 0, u1, v1);
        tess.addVertexWithUV(x + w, y, 0, u1, v0);
        tess.addVertexWithUV(x, y, 0, u0, v0);
        tess.draw();
        GL11.glColor4f(1f, 1f, 1f, 1f);
        return true;
    }

    private static void drawBorder(int x, int y, int size, int color) {
        BorderedRect.drawBorderOnly(x, y, size, size, color);
    }

    private static void drawDashedBorder(int x, int y, int size, int color) {
        int step = 3;
        int dash = 2;
        for (int i = 0; i < size; i += step) {
            int end = Math.min(i + dash, size);
            Gui.drawRect(x + i, y, x + end, y + 1, color);
            Gui.drawRect(x + i, y + size - 1, x + end, y + size, color);
            Gui.drawRect(x, y + i, x + 1, y + end, color);
            Gui.drawRect(x + size - 1, y + i, x + size, y + end, color);
        }
    }
}
