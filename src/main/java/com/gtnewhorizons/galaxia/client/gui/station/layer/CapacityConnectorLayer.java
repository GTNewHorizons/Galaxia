package com.gtnewhorizons.galaxia.client.gui.station.layer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.cleanroommc.modularui.screen.viewport.GuiContext;
import com.cleanroommc.modularui.utils.GlStateManager;
import com.gtnewhorizons.galaxia.client.EnumColors;
import com.gtnewhorizons.galaxia.client.gui.station.StationMapViewport;
import com.gtnewhorizons.galaxia.client.gui.station.layer.StationTextureRegistry.ConnectorKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.station.PlacedTile;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

public final class CapacityConnectorLayer {

    private static final int COLOR_ALPHA_ACTIVE = 0xFF;
    private static final int COLOR_ALPHA_INACTIVE = 0x66;
    private static final int RGB_CHANNEL_MASK = 0x00FFFFFF;

    private CapacityConnectorLayer() {}

    public static void draw(GuiContext ctx, Map<StationTileCoord, PlacedTile> tiles, int widgetWidth, int widgetHeight,
        int contentLeft, int contentRightPadding, int contentVerticalPadding, int panX, int panY) {
        if (tiles == null) return;
        int connW = StationMapViewport.connectorWidth();
        int connH = StationMapViewport.connectorHeight();
        int tileSize = StationMapViewport.TILE_SIZE;
        ResourceLocation horizontalTexture = StationTextureRegistry.connectorTexture(ConnectorKind.HORIZONTAL);
        ResourceLocation verticalTexture = StationTextureRegistry.connectorTexture(ConnectorKind.VERTICAL);
        boolean hasHorizontalTexture = StationTextureRegistry.hasTexture(horizontalTexture);
        boolean hasVerticalTexture = StationTextureRegistry.hasTexture(verticalTexture);
        List<CapacityConnectorQuad> horizontalQuads = new ArrayList<>();
        List<CapacityConnectorQuad> verticalQuads = new ArrayList<>();

        for (Map.Entry<StationTileCoord, PlacedTile> e : tiles.entrySet()) {
            StationTileCoord coord = e.getKey();
            PlacedTile a = e.getValue();
            FacilityModuleKind kindA = moduleKindOf(a);
            if (kindA == null || !kindA.isCapacityModule()) continue;

            // Check right neighbor
            StationTileCoord right = StationTileCoord.of(coord.dx() + 1, coord.dy());
            PlacedTile b = tiles.get(right);
            if (b != null && !sameModule(a, b) && sameCapacityKind(kindA, b)) {
                int cx = StationMapViewport.connectorLeftX(coord, widgetWidth, contentLeft, contentRightPadding, panX);
                int cy = StationMapViewport.tileTopY(coord, widgetHeight, contentVerticalPadding, panY)
                    + (tileSize - connH) / 2;
                addConnector(cx, cy, connW, connH, kindA, hasHorizontalTexture, horizontalQuads);
            }

            // Check down neighbor
            StationTileCoord down = StationTileCoord.of(coord.dx(), coord.dy() + 1);
            PlacedTile c = tiles.get(down);
            if (c != null && !sameModule(a, c) && sameCapacityKind(kindA, c)) {
                int cx = StationMapViewport.tileLeftX(coord, widgetWidth, contentLeft, contentRightPadding, panX)
                    + (tileSize - connW) / 2;
                int cy = StationMapViewport.connectorTopY(coord, widgetHeight, contentVerticalPadding, panY);
                addConnector(cx, cy, connW, connH, kindA, hasVerticalTexture, verticalQuads);
            }
        }

        drawColoredBatch(horizontalTexture, horizontalQuads);
        drawColoredBatch(verticalTexture, verticalQuads);
    }

    private static FacilityModuleKind moduleKindOf(PlacedTile tile) {
        if (tile == null) return null;
        ModuleInstance module = tile.module();
        return module == null ? null : module.kind();
    }

    private static boolean sameCapacityKind(FacilityModuleKind kindA, PlacedTile tileB) {
        FacilityModuleKind kindB = moduleKindOf(tileB);
        return kindB != null && kindB.isCapacityModule() && kindA == kindB;
    }

    private static boolean sameModule(PlacedTile a, PlacedTile b) {
        if (a == null || b == null) return false;
        ModuleInstance moduleA = a.module();
        ModuleInstance moduleB = b.module();
        return moduleA != null && moduleB != null && moduleA.id.equals(moduleB.id);
    }

    private static void addConnector(int x, int y, int w, int h, FacilityModuleKind kind, boolean hasTexture,
        List<CapacityConnectorQuad> textureQuads) {
        int alpha = hasTexture ? COLOR_ALPHA_ACTIVE : COLOR_ALPHA_INACTIVE;
        int color = connectorColor(kind);
        int argb = (alpha << 24) | (color & RGB_CHANNEL_MASK);

        if (hasTexture) {
            textureQuads.add(new CapacityConnectorQuad(x, y, w, h, argb));
        } else {
            Gui.drawRect(x, y, x + w, y + h, argb);
        }
    }

    private static void drawColoredBatch(ResourceLocation texture, List<CapacityConnectorQuad> quads) {
        if (texture == null || quads.isEmpty()) return;
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(texture);
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        Tessellator tess = Tessellator.instance;
        tess.startDrawingQuads();
        for (CapacityConnectorQuad quad : quads) {
            // Modulate the texture with the per-kind color
            float r = ((quad.argb >> 16) & 0xFF) / 255f;
            float g = ((quad.argb >> 8) & 0xFF) / 255f;
            float b = (quad.argb & 0xFF) / 255f;
            float a = ((quad.argb >> 24) & 0xFF) / 255f;
            tess.setColorRGBA_F(r, g, b, a);

            tess.addVertexWithUV(quad.x, quad.y + quad.h, 0, 0, 1);
            tess.addVertexWithUV(quad.x + quad.w, quad.y + quad.h, 0, 1, 1);
            tess.addVertexWithUV(quad.x + quad.w, quad.y, 0, 1, 0);
            tess.addVertexWithUV(quad.x, quad.y, 0, 0, 0);
        }
        tess.draw();
    }

    private static int connectorColor(FacilityModuleKind kind) {
        return switch (kind) {
            case STORAGE -> EnumColors.MAP_COLOR_CONNECTOR_STORAGE.getColor();
            case TANK -> EnumColors.MAP_COLOR_CONNECTOR_TANK.getColor();
            case BATTERY -> EnumColors.MAP_COLOR_CONNECTOR_BATTERY.getColor();
            default -> EnumColors.MAP_COLOR_CONNECTOR_DEFAULT.getColor();
        };
    }

    private static final class CapacityConnectorQuad {

        private final int x;
        private final int y;
        private final int w;
        private final int h;
        private final int argb;

        private CapacityConnectorQuad(int x, int y, int w, int h, int argb) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.argb = argb;
        }
    }
}
