package com.gtnewhorizons.galaxia.client.gui.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

final class StationMapVisibleTilesTest {

    @Test
    void visibleTilesIncludeEmptyTileCoordinatesWithinViewport() {
        Set<StationTileCoord> tiles = StationMapViewport.visibleTiles(320, 240, 20, 20, 12, 0, 0);

        assertTrue(tiles.contains(StationTileCoord.CORE));
        assertTrue(tiles.contains(StationTileCoord.of(1, 0)));
        assertTrue(tiles.contains(StationTileCoord.of(-1, 0)));
    }

    @Test
    void visibleTilesRespectPanOffset() {
        Set<StationTileCoord> normal = StationMapViewport.visibleTiles(320, 240, 20, 20, 12, 0, 0);
        Set<StationTileCoord> panned = StationMapViewport.visibleTiles(320, 240, 20, 20, 12, 200, 0);

        assertFalse(normal.equals(panned));
    }

    @Test
    void visibleTilePositionsAreNotClampedToBuildableStationBounds() {
        Set<StationMapViewport.TilePosition> positions = StationMapViewport
            .visibleTilePositions(320, 240, 20, 20, 12, 0, -2000);

        assertTrue(
            positions.stream()
                .anyMatch(position -> position.dy() > StationTileCoord.MAX));
    }

    @Test
    void collectedVisibleTilePositionsMatchAllocatedVisibleTilePositions() {
        Set<StationMapViewport.TilePosition> allocated = StationMapViewport
            .visibleTilePositions(320, 240, 20, 20, 12, 48, -36);
        List<StationMapViewport.TilePosition> collected = new ArrayList<>();

        StationMapViewport.collectVisibleTilePositions(320, 240, 20, 20, 12, 48, -36, collected);

        assertEquals(List.copyOf(allocated), collected);
    }

    @Test
    void stationTileCoordsRemainClampedToBuildableStationBounds() {
        Set<StationTileCoord> tiles = StationMapViewport.visibleTiles(320, 240, 20, 20, 12, 0, -2000);

        assertTrue(
            tiles.stream()
                .allMatch(tile -> tile.dy() <= StationTileCoord.MAX));
    }
}
