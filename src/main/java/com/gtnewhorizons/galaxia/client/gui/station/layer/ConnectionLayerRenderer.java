package com.gtnewhorizons.galaxia.client.gui.station.layer;

import java.util.List;
import java.util.Map;

import net.minecraft.client.gui.Gui;
import net.minecraft.util.ResourceLocation;

import com.cleanroommc.modularui.screen.viewport.GuiContext;
import com.gtnewhorizons.galaxia.client.EnumColors;
import com.gtnewhorizons.galaxia.client.gui.station.StationMapFrame;
import com.gtnewhorizons.galaxia.client.gui.station.StationMapViewport;
import com.gtnewhorizons.galaxia.client.gui.station.layer.ConnectorTextureBatchRenderer.Quad;
import com.gtnewhorizons.galaxia.client.gui.station.layer.StationTextureRegistry.ConnectorKind;
import com.gtnewhorizons.galaxia.registry.outpost.station.PlacedTile;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

public final class ConnectionLayerRenderer {

    private static final List<Quad> HORIZONTAL_QUADS = new java.util.ArrayList<>();
    private static final List<Quad> VERTICAL_QUADS = new java.util.ArrayList<>();

    private ConnectionLayerRenderer() {}

    public static void draw(GuiContext ctx, Map<StationTileCoord, PlacedTile> tiles, StationMapFrame frame) {
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
                int cx = frame.connectorLocalX(coord);
                int cy = frame.tileLocalY(coord) + (tileSize - connH) / 2;
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
                int cx = frame.tileLocalX(coord) + (tileSize - connW) / 2;
                int cy = frame.connectorLocalY(coord);
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

        ConnectorTextureBatchRenderer.draw(horizontalTexture, HORIZONTAL_QUADS);
        ConnectorTextureBatchRenderer.draw(verticalTexture, VERTICAL_QUADS);
    }

    static boolean shouldDrawConnectorBetween(PlacedTile a, PlacedTile b) {
        return ConnectorRoutePolicy.hasModuleConnector(a, b);
    }

    private static void drawConnector(int x, int y, int w, int h, boolean active, boolean hasTexture,
        List<Quad> textureQuads) {
        if (active && hasTexture) {
            textureQuads.add(new Quad(x, y, w, h));
            return;
        }

        int color = active ? EnumColors.MAP_COLOR_STATION_CONNECTOR_ACTIVE.getColor()
            : EnumColors.MAP_COLOR_STATION_CONNECTOR_INACTIVE.getColor();
        Gui.drawRect(x, y, x + w, y + h, color);
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
