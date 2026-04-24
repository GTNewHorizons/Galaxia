package com.gtnewhorizons.galaxia.client.gui.station.layer;

import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.cleanroommc.modularui.screen.viewport.GuiContext;
import com.cleanroommc.modularui.utils.GlStateManager;
import com.gtnewhorizons.galaxia.client.gui.station.StationMapViewport;
import com.gtnewhorizons.galaxia.registry.outpost.station.PlacedTile;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationLayout;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

public final class ConnectionLayerRenderer {

    private static final int CONNECTOR_COLOR = 0xFF1E385A;
    private static final int CONNECTOR_UNDER_CONSTRUCTION = 0x661E385A;

    private ConnectionLayerRenderer() {}

    public static void draw(GuiContext ctx, StationLayout layout, int widgetWidth, int widgetHeight, int contentLeft,
        int contentRightPadding, int contentVerticalPadding, int panX, int panY) {
        if (layout == null) return;
        Map<StationTileCoord, PlacedTile> tiles = layout.snapshot();
        int connW = StationMapViewport.connectorWidth();
        int connH = StationMapViewport.connectorHeight();
        int tileSize = StationMapViewport.TILE_SIZE;

        for (Map.Entry<StationTileCoord, PlacedTile> e : tiles.entrySet()) {
            StationTileCoord coord = e.getKey();
            PlacedTile tile = e.getValue();
            if (tile == null) continue;

            StationTileCoord right = StationTileCoord.of(coord.dx() + 1, coord.dy());
            if (tiles.containsKey(right)) {
                int cx = StationMapViewport.connectorLeftX(coord, widgetWidth, contentLeft, contentRightPadding, panX);
                int cy = StationMapViewport.tileTopY(coord, widgetHeight, contentVerticalPadding, panY)
                    + (tileSize - connH) / 2;
                drawConnector(cx, cy, connW, connH, connectorHorizontal(), connectorActive(tile, tiles.get(right)));
            }

            StationTileCoord down = StationTileCoord.of(coord.dx(), coord.dy() + 1);
            if (tiles.containsKey(down)) {
                int cx = StationMapViewport.tileLeftX(coord, widgetWidth, contentLeft, contentRightPadding, panX)
                    + (tileSize - connW) / 2;
                int cy = StationMapViewport.connectorTopY(coord, widgetHeight, contentVerticalPadding, panY);
                drawConnector(cx, cy, connW, connH, connectorVertical(), connectorActive(tile, tiles.get(down)));
            }
        }
    }

    private static void drawConnector(int x, int y, int w, int h, ResourceLocation texture, boolean active) {
        if (active && texture != null && StationTextureRegistry.hasTexture(texture)) {
            Minecraft.getMinecraft()
                .getTextureManager()
                .bindTexture(texture);
            GlStateManager.enableTexture2D();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glColor4f(1f, 1f, 1f, 1f);
            Tessellator tess = Tessellator.instance;
            tess.startDrawingQuads();
            tess.addVertexWithUV(x, y + h, 0, 0, 1);
            tess.addVertexWithUV(x + w, y + h, 0, 1, 1);
            tess.addVertexWithUV(x + w, y, 0, 1, 0);
            tess.addVertexWithUV(x, y, 0, 0, 0);
            tess.draw();
        } else {
            int color = active ? CONNECTOR_COLOR : CONNECTOR_UNDER_CONSTRUCTION;
            Gui.drawRect(x, y, x + w, y + h, color);
        }
    }

    private static ResourceLocation connectorHorizontal() {
        return StationTextureRegistry.connectorHorizontal();
    }

    private static ResourceLocation connectorVertical() {
        return StationTextureRegistry.connectorVertical();
    }

    private static boolean connectorActive(PlacedTile a, PlacedTile b) {
        if (a == null || b == null) return false;
        return a.state() != null && a.state()
            .isConnectorActive()
            && b.state() != null
            && b.state()
                .isConnectorActive();
    }
}
