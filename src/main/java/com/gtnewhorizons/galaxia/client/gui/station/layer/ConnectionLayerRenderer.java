package com.gtnewhorizons.galaxia.client.gui.station.layer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.gui.Gui;
import net.minecraft.util.ResourceLocation;

import com.gtnewhorizons.galaxia.client.EnumColors;
import com.gtnewhorizons.galaxia.client.gui.station.StationMapFrame;
import com.gtnewhorizons.galaxia.client.gui.station.layer.ConnectorTextureBatchRenderer.Quad;
import com.gtnewhorizons.galaxia.client.gui.station.layer.StationTextureRegistry.ConnectorKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.station.PlacedTile;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

public final class ConnectionLayerRenderer {

    private static final List<Quad> HORIZONTAL_QUADS = new ArrayList<>();
    private static final List<Quad> VERTICAL_QUADS = new ArrayList<>();
    private static final Map<ResourceLocation, List<Quad>> CAPACITY_QUADS_BY_TEXTURE = new HashMap<>();

    private ConnectionLayerRenderer() {}

    public static void draw(Map<StationTileCoord, PlacedTile> tiles, StationMapFrame frame) {
        if (tiles == null) return;
        ResourceLocation horizontalTexture = StationTextureRegistry.connectorTexture(ConnectorKind.HORIZONTAL);
        ResourceLocation verticalTexture = StationTextureRegistry.connectorTexture(ConnectorKind.VERTICAL);
        boolean hasHorizontalTexture = StationTextureRegistry.hasTexture(horizontalTexture);
        boolean hasVerticalTexture = StationTextureRegistry.hasTexture(verticalTexture);
        HORIZONTAL_QUADS.clear();
        VERTICAL_QUADS.clear();
        CAPACITY_QUADS_BY_TEXTURE.clear();

        for (Map.Entry<StationTileCoord, PlacedTile> entry : tiles.entrySet()) {
            StationTileCoord coord = entry.getKey();
            PlacedTile tile = entry.getValue();
            if (tile == null) continue;

            collectEdge(
                coord,
                tile,
                tiles.get(StationTileCoord.of(coord.dx() + 1, coord.dy())),
                ConnectorKind.HORIZONTAL,
                hasHorizontalTexture,
                HORIZONTAL_QUADS,
                frame);
            collectEdge(
                coord,
                tile,
                tiles.get(StationTileCoord.of(coord.dx(), coord.dy() + 1)),
                ConnectorKind.VERTICAL,
                hasVerticalTexture,
                VERTICAL_QUADS,
                frame);
        }

        ConnectorTextureBatchRenderer.draw(horizontalTexture, HORIZONTAL_QUADS);
        ConnectorTextureBatchRenderer.draw(verticalTexture, VERTICAL_QUADS);
        for (Map.Entry<ResourceLocation, List<Quad>> entry : CAPACITY_QUADS_BY_TEXTURE.entrySet()) {
            ConnectorTextureBatchRenderer.draw(entry.getKey(), entry.getValue());
        }
    }

    private static void collectEdge(StationTileCoord coord, PlacedTile tile, PlacedTile neighbour,
        ConnectorKind connectorKind, boolean hasConnectorTexture, List<Quad> connectorQuads, StationMapFrame frame) {
        int x = connectorKind == ConnectorKind.HORIZONTAL ? frame.connectorLocalX(coord)
            : frame.tileLocalX(coord) + (StationMapFrame.TILE_SIZE - StationMapFrame.CONNECTOR_SIZE) / 2;
        int y = connectorKind == ConnectorKind.HORIZONTAL
            ? frame.tileLocalY(coord) + (StationMapFrame.TILE_SIZE - StationMapFrame.CONNECTOR_SIZE) / 2
            : frame.connectorLocalY(coord);

        if (ConnectorRoutePolicy.hasModuleConnector(tile, neighbour)) {
            drawConnector(x, y, connectorActive(tile, neighbour), hasConnectorTexture, connectorQuads);
        }

        FacilityModuleKind capacityKind = ConnectorRoutePolicy.capacityConnectorKind(tile, neighbour);
        if (capacityKind != null) addCapacityConnector(x, y, capacityKind, connectorKind);
    }

    private static void drawConnector(int x, int y, boolean active, boolean hasTexture, List<Quad> textureQuads) {
        if (active && hasTexture) {
            textureQuads.add(new Quad(x, y, StationMapFrame.CONNECTOR_SIZE, StationMapFrame.CONNECTOR_SIZE));
            return;
        }

        int color = active ? EnumColors.MAP_COLOR_STATION_CONNECTOR_ACTIVE.getColor()
            : EnumColors.MAP_COLOR_STATION_CONNECTOR_INACTIVE.getColor();
        Gui.drawRect(x, y, x + StationMapFrame.CONNECTOR_SIZE, y + StationMapFrame.CONNECTOR_SIZE, color);
    }

    private static void addCapacityConnector(int x, int y, FacilityModuleKind kind, ConnectorKind connectorKind) {
        ResourceLocation texture = StationTextureRegistry.capacityConnectorTexture(kind, connectorKind);
        if (texture == null) return;
        CAPACITY_QUADS_BY_TEXTURE.computeIfAbsent(texture, ignored -> new ArrayList<>())
            .add(new Quad(x, y, StationMapFrame.CONNECTOR_SIZE, StationMapFrame.CONNECTOR_SIZE));
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
