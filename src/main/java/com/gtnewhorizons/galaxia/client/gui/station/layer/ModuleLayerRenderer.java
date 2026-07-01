package com.gtnewhorizons.galaxia.client.gui.station.layer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import com.gtnewhorizons.galaxia.client.gui.station.ModuleFootprintProjection;
import com.gtnewhorizons.galaxia.client.gui.station.StationMapViewport;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.PlacedTile;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationModuleCategory;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileState;

public final class ModuleLayerRenderer {

    private ModuleLayerRenderer() {}

    public static void drawOccupied(GuiContext ctx, int x, int y, PlacedTile tile) {
        drawOccupied(ctx, x, y, null, tile);
    }

    public static void drawOccupied(GuiContext ctx, int x, int y, StationTileCoord coord, PlacedTile tile) {
        int size = StationMapViewport.TILE_SIZE;
        ModuleInstance module = tile == null ? null : tile.module();
        if (!shouldDrawFootprintTexture(module)) {
            TextureRegion region = textureRegion(module, coord);
            if (!drawModuleTextureRegion(
                x,
                y,
                size,
                size,
                moduleKindOf(tile),
                region.u0(),
                region.v0(),
                region.u1(),
                region.v1())) {
                int fillColor = categoryColor(categoryOf(tile));
                Gui.drawRect(x, y, x + size, y + size, fillColor);
                drawLabel(ctx, x, y, size, labelOf(tile));
            }
            drawBorder(x, y, size, EnumColors.MAP_COLOR_STATION_TILE_BORDER_DEFAULT.getColor());
        }

        if (!shouldDrawFootprintTexture(module)) {
            drawStateOverlay(x, y, size, size, tile.state());
        }
    }

    public static void drawFootprintTextures(Map<StationTileCoord, PlacedTile> tiles, int widgetWidth, int widgetHeight,
        int contentLeft, int contentRightPadding, int contentVerticalPadding, int panX, int panY) {
        Set<ModuleInstance.ID> drawn = new HashSet<>();
        for (PlacedTile tile : tiles.values()) {
            ModuleInstance module = tile == null ? null : tile.module();
            if (!shouldDrawFootprintTexture(module) || !drawn.add(module.id)) continue;
            FootprintTextureBounds bounds = footprintTextureBounds(
                module,
                widgetWidth,
                widgetHeight,
                contentLeft,
                contentRightPadding,
                contentVerticalPadding,
                panX,
                panY);
            drawModuleTextureFootprint(bounds.x(), bounds.y(), bounds.width(), bounds.height(), module);
            drawFootprintOverlaySegments(
                module.shape(),
                module.anchor(),
                module.rotation(),
                widgetWidth,
                widgetHeight,
                contentLeft,
                contentRightPadding,
                contentVerticalPadding,
                panX,
                panY,
                tile.state());
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

    public static boolean drawPreviewFootprint(GuiContext ctx, FacilityModuleKind kind, ModuleShape footprint,
        StationTileCoord anchor, int rotation, int widgetWidth, int widgetHeight, int contentLeft,
        int contentRightPadding, int contentVerticalPadding, int panX, int panY) {
        if (kind == null || footprint == null || anchor == null) return false;
        FootprintTextureBounds bounds = footprintTextureBounds(
            footprint,
            anchor,
            rotation,
            widgetWidth,
            widgetHeight,
            contentLeft,
            contentRightPadding,
            contentVerticalPadding,
            panX,
            panY);
        if (!drawModuleTextureFootprint(
            bounds.x(),
            bounds.y(),
            bounds.width(),
            bounds.height(),
            kind,
            rotation,
            0.55f,
            0.55f,
            0.55f,
            0.7f)) {
            return false;
        }
        for (ModuleFootprintProjection.Segment segment : ModuleFootprintProjection.filledSegments(
            footprint,
            anchor,
            rotation,
            widgetWidth,
            widgetHeight,
            contentLeft,
            contentRightPadding,
            contentVerticalPadding,
            panX,
            panY)) {
            Gui.drawRect(
                segment.x(),
                segment.y(),
                segment.x() + segment.width(),
                segment.y() + segment.height(),
                EnumColors.MAP_COLOR_STATION_TILE_PREVIEW_DIM.getColor());
        }
        return true;
    }

    public static boolean drawModuleTextureRegion(int x, int y, int w, int h, FacilityModuleKind kind, float u0,
        float v0, float u1, float v1) {
        return drawModuleTextureRegion(x, y, w, h, kind, u0, v0, u1, v1, 1f, 1f, 1f, 1f);
    }

    static TextureRegion textureRegion(ModuleInstance module, StationTileCoord coord) {
        if (module == null || coord == null || module.anchorOrNull() == null) return TextureRegion.FULL;
        ModuleShape shape = module.shape();
        int width = shape.textureGridWidth();
        int height = shape.textureGridHeight();
        if (width <= 0 || height <= 0) return TextureRegion.FULL;
        ModuleShape.TextureTile textureTile = shape.textureTile(module.anchor(), coord, module.rotation());
        int column = textureTile.column();
        int row = textureTile.row();
        if (column < 0 || column >= width || row < 0 || row >= height) return TextureRegion.FULL;
        float u0 = (float) column / width;
        float v0 = (float) row / height;
        return new TextureRegion(u0, v0, (float) (column + 1) / width, (float) (row + 1) / height);
    }

    static FootprintTextureBounds footprintTextureBounds(ModuleInstance module, int widgetWidth, int widgetHeight,
        int contentLeft, int contentRightPadding, int contentVerticalPadding, int panX, int panY) {
        return footprintTextureBounds(
            module.shape(),
            module.anchor(),
            module.rotation(),
            widgetWidth,
            widgetHeight,
            contentLeft,
            contentRightPadding,
            contentVerticalPadding,
            panX,
            panY);
    }

    static FootprintTextureBounds footprintTextureBounds(ModuleShape shape, StationTileCoord anchor, int rotation,
        int widgetWidth, int widgetHeight, int contentLeft, int contentRightPadding, int contentVerticalPadding,
        int panX, int panY) {
        StationTileCoord[] tiles = shape.tiles(anchor, rotation);
        int minDx = Integer.MAX_VALUE;
        int minDy = Integer.MAX_VALUE;
        int maxDx = Integer.MIN_VALUE;
        int maxDy = Integer.MIN_VALUE;
        for (StationTileCoord tile : tiles) {
            minDx = Math.min(minDx, tile.dx());
            minDy = Math.min(minDy, tile.dy());
            maxDx = Math.max(maxDx, tile.dx());
            maxDy = Math.max(maxDy, tile.dy());
        }
        int x = StationMapViewport.tileLeftX(minDx, widgetWidth, contentLeft, contentRightPadding, panX);
        int y = StationMapViewport.tileTopY(minDy, widgetHeight, contentVerticalPadding, panY);
        int width = (maxDx - minDx) * StationMapViewport.TILE_STEP + StationMapViewport.TILE_SIZE;
        int height = (maxDy - minDy) * StationMapViewport.TILE_STEP + StationMapViewport.TILE_SIZE;
        return new FootprintTextureBounds(x, y, width, height);
    }

    public static List<FootprintSegment> footprintOverlaySegments(ModuleShape shape, StationTileCoord anchor,
        int rotation, int widgetWidth, int widgetHeight, int contentLeft, int contentRightPadding,
        int contentVerticalPadding, int panX, int panY) {
        List<FootprintSegment> segments = new ArrayList<>();
        for (ModuleFootprintProjection.Segment segment : ModuleFootprintProjection.filledSegments(
            shape,
            anchor,
            rotation,
            widgetWidth,
            widgetHeight,
            contentLeft,
            contentRightPadding,
            contentVerticalPadding,
            panX,
            panY)) {
            segments.add(new FootprintSegment(segment.x(), segment.y(), segment.width(), segment.height()));
        }
        return segments;
    }

    static boolean shouldDrawFootprintTexture(ModuleInstance module) {
        if (module == null || module.shape()
            .tileCount() <= 1) {
            return false;
        }
        return StationTextureRegistry.hasTexture(StationTextureRegistry.moduleTexture(module.kind()));
    }

    record TextureRegion(float u0, float v0, float u1, float v1) {

        private static final TextureRegion FULL = new TextureRegion(0f, 0f, 1f, 1f);
    }

    record FootprintTextureBounds(int x, int y, int width, int height) {}

    public record FootprintSegment(int x, int y, int width, int height) {}

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

    private static boolean drawModuleTextureFootprint(int x, int y, int w, int h, ModuleInstance module) {
        if (module == null) return false;
        return drawModuleTextureFootprint(x, y, w, h, module.kind(), module.rotation(), 1f, 1f, 1f, 1f);
    }

    private static boolean drawModuleTextureFootprint(int x, int y, int w, int h, FacilityModuleKind kind, int rotation,
        float red, float green, float blue, float alpha) {
        TextureCorners corners = textureCorners(rotation);
        return drawModuleTextureQuad(x, y, w, h, kind, corners, red, green, blue, alpha);
    }

    private static boolean drawModuleTextureRegion(int x, int y, int w, int h, FacilityModuleKind kind, float u0,
        float v0, float u1, float v1, float red, float green, float blue, float alpha) {
        return drawModuleTextureQuad(
            x,
            y,
            w,
            h,
            kind,
            new TextureCorners(u0, v1, u1, v1, u1, v0, u0, v0),
            red,
            green,
            blue,
            alpha);
    }

    private static boolean drawModuleTextureQuad(int x, int y, int w, int h, FacilityModuleKind kind,
        TextureCorners corners, float red, float green, float blue, float alpha) {
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
        tess.addVertexWithUV(x, y + h, 0, corners.bottomLeftU(), corners.bottomLeftV());
        tess.addVertexWithUV(x + w, y + h, 0, corners.bottomRightU(), corners.bottomRightV());
        tess.addVertexWithUV(x + w, y, 0, corners.topRightU(), corners.topRightV());
        tess.addVertexWithUV(x, y, 0, corners.topLeftU(), corners.topLeftV());
        tess.draw();
        GL11.glColor4f(1f, 1f, 1f, 1f);
        return true;
    }

    private static void drawFootprintOverlaySegments(ModuleShape shape, StationTileCoord anchor, int rotation,
        int widgetWidth, int widgetHeight, int contentLeft, int contentRightPadding, int contentVerticalPadding,
        int panX, int panY, StationTileState state) {
        int color = stateOverlayColor(state);
        if (color == 0) return;
        for (ModuleFootprintProjection.Segment segment : ModuleFootprintProjection.filledSegments(
            shape,
            anchor,
            rotation,
            widgetWidth,
            widgetHeight,
            contentLeft,
            contentRightPadding,
            contentVerticalPadding,
            panX,
            panY)) {
            Gui.drawRect(
                segment.x(),
                segment.y(),
                segment.x() + segment.width(),
                segment.y() + segment.height(),
                color);
        }
    }

    private static void drawStateOverlay(int x, int y, int width, int height, StationTileState state) {
        int color = stateOverlayColor(state);
        if (color == 0) return;
        Gui.drawRect(x, y, x + width, y + height, color);
    }

    private static int stateOverlayColor(StationTileState state) {
        return switch (state) {
            case UNDER_CONSTRUCTION -> EnumColors.MAP_COLOR_STATION_TILE_UNDER_CONSTRUCTION.getColor();
            case UNDER_DECONSTRUCTION -> EnumColors.MAP_COLOR_STATION_TILE_UNDER_DECONSTRUCTION.getColor();
            case OCCUPIED_DISABLED -> EnumColors.MAP_COLOR_STATION_TILE_DISABLED_DIM.getColor();
            case BLOCKED -> EnumColors.MAP_COLOR_STATION_TILE_BLOCKED.getColor();
            case OCCUPIED_OPERATIONAL, EMPTY -> 0;
        };
    }

    static TextureCorners textureCorners(int rotation) {
        return switch (ModuleShape.normalizeRotation(rotation)) {
            case 1 -> new TextureCorners(1f, 1f, 1f, 0f, 0f, 0f, 0f, 1f);
            case 2 -> new TextureCorners(1f, 0f, 0f, 0f, 0f, 1f, 1f, 1f);
            case 3 -> new TextureCorners(0f, 0f, 0f, 1f, 1f, 1f, 1f, 0f);
            default -> new TextureCorners(0f, 1f, 1f, 1f, 1f, 0f, 0f, 0f);
        };
    }

    record TextureCorners(float bottomLeftU, float bottomLeftV, float bottomRightU, float bottomRightV, float topRightU,
        float topRightV, float topLeftU, float topLeftV) {}

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
