package com.gtnewhorizons.galaxia.client.gui.station.layer;

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
import com.gtnewhorizons.galaxia.registry.outpost.station.PlacedTile;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

public final class ConnectionLayerRenderer {

    private static final List<ConnectorQuad> HORIZONTAL_QUADS = new java.util.ArrayList<>();
    private static final List<ConnectorQuad> VERTICAL_QUADS = new java.util.ArrayList<>();

    private ConnectionLayerRenderer() {}

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
        HORIZONTAL_QUADS.clear();
        VERTICAL_QUADS.clear();

        for (Map.Entry<StationTileCoord, PlacedTile> e : tiles.entrySet()) {
            StationTileCoord coord = e.getKey();
            PlacedTile tile = e.getValue();
            if (tile == null) continue;

            StationTileCoord right = StationTileCoord.of(coord.dx() + 1, coord.dy());
            PlacedTile rightTile = tiles.get(right);
            if (shouldDrawConnectorBetween(tile, rightTile)) {
                int cx = StationMapViewport.connectorLeftX(coord, widgetWidth, contentLeft, contentRightPadding, panX);
                int cy = StationMapViewport.tileTopY(coord, widgetHeight, contentVerticalPadding, panY)
                    + (tileSize - connH) / 2;
                drawConnector(
                    cx,
                    cy,
                    connW,
                    connH,
                    connectorActive(tile, rightTile),
                    hasHorizontalTexture,
                    HORIZONTAL_QUADS);
            }

            StationTileCoord down = StationTileCoord.of(coord.dx(), coord.dy() + 1);
            PlacedTile downTile = tiles.get(down);
            if (shouldDrawConnectorBetween(tile, downTile)) {
                int cx = StationMapViewport.tileLeftX(coord, widgetWidth, contentLeft, contentRightPadding, panX)
                    + (tileSize - connW) / 2;
                int cy = StationMapViewport.connectorTopY(coord, widgetHeight, contentVerticalPadding, panY);
                drawConnector(
                    cx,
                    cy,
                    connW,
                    connH,
                    connectorActive(tile, downTile),
                    hasVerticalTexture,
                    VERTICAL_QUADS);
            }
        }

        drawTextureBatch(horizontalTexture, HORIZONTAL_QUADS);
        drawTextureBatch(verticalTexture, VERTICAL_QUADS);
    }

    static boolean shouldDrawConnectorBetween(PlacedTile a, PlacedTile b) {
        return ConnectorRoutePolicy.hasModuleConnector(a, b);
    }

    private static void drawConnector(int x, int y, int w, int h, boolean active, boolean hasTexture,
        List<ConnectorQuad> textureQuads) {
        if (active && hasTexture) {
            textureQuads.add(new ConnectorQuad(x, y, w, h));
            return;
        }

        int color = active ? EnumColors.MAP_COLOR_STATION_CONNECTOR_ACTIVE.getColor()
            : EnumColors.MAP_COLOR_STATION_CONNECTOR_INACTIVE.getColor();
        Gui.drawRect(x, y, x + w, y + h, color);
    }

    private static void drawTextureBatch(ResourceLocation texture, List<ConnectorQuad> quads) {
        if (texture == null || quads.isEmpty()) return;
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(texture);
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(1f, 1f, 1f, 1f);

        Tessellator tess = Tessellator.instance;
        tess.startDrawingQuads();
        for (ConnectorQuad quad : quads) {
            tess.addVertexWithUV(quad.x(), quad.y() + quad.h(), 0, 0, 1);
            tess.addVertexWithUV(quad.x() + quad.w(), quad.y() + quad.h(), 0, 1, 1);
            tess.addVertexWithUV(quad.x() + quad.w(), quad.y(), 0, 1, 0);
            tess.addVertexWithUV(quad.x(), quad.y(), 0, 0, 0);
        }
        tess.draw();
    }

    private static boolean connectorActive(PlacedTile a, PlacedTile b) {
        if (a == null || b == null) return false;
        return a.state() != null && a.state()
            .isConnectorActive()
            && b.state() != null
            && b.state()
                .isConnectorActive();
    }

    private record ConnectorQuad(int x, int y, int w, int h) {}
}
