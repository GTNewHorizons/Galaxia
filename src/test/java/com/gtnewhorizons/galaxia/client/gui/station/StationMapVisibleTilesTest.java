package com.gtnewhorizons.galaxia.client.gui.station;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

final class StationMapVisibleTilesTest {

    @Test
    void visibleTilesIncludeEmptyTileCoordinatesWithinViewport() {
        Set<StationMapViewport.TilePosition> positions = visible(0, 0);

        assertTrue(contains(positions, StationTileCoord.CORE.dx(), StationTileCoord.CORE.dy()));
        assertTrue(contains(positions, 1, 0));
        assertTrue(contains(positions, -1, 0));
    }

    @Test
    void visibleTilesRespectPanOffset() {
        assertFalse(visible(0, 0).equals(visible(200, 0)));
    }

    @Test
    void visibleTilePositionsAreNotClampedToBuildableStationBounds() {
        assertTrue(
            visible(0, -2000).stream()
                .anyMatch(position -> position.dy() > StationTileCoord.MAX));
    }

    private static Set<StationMapViewport.TilePosition> visible(int panX, int panY) {
        Set<StationMapViewport.TilePosition> positions = new LinkedHashSet<>();
        StationMapViewport.collectVisibleTilePositions(320, 240, 20, 20, 12, panX, panY, positions);
        return positions;
    }

    private static boolean contains(Set<StationMapViewport.TilePosition> positions, int dx, int dy) {
        return positions.stream()
            .anyMatch(position -> position.dx() == dx && position.dy() == dy);
    }
}
