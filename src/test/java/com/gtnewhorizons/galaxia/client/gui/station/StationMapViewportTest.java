package com.gtnewhorizons.galaxia.client.gui.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

final class StationMapViewportTest {

    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final int TILE = 24;
    private static final int CONTENT_LEFT = 228;
    private static final int CONTENT_RIGHT_PADDING = 12;
    private static final int CONTENT_VERTICAL_PADDING = 12;

    @Test
    void drawnTileCentersResolveToTheSameTile() {
        assertTileCenterRoundTrips(StationTileCoord.CORE, 0, 0);
        assertTileCenterRoundTrips(StationTileCoord.of(1, 0), 0, 0);
        assertTileCenterRoundTrips(StationTileCoord.of(-1, 0), 0, 0);
        assertTileCenterRoundTrips(StationTileCoord.of(0, 1), 0, 0);
        assertTileCenterRoundTrips(StationTileCoord.of(0, -1), 0, 0);
        assertTileCenterRoundTrips(StationTileCoord.of(3, -2), 0, 0);
    }

    @Test
    void drawnTileCentersResolveToTheSameTileAfterPanning() {
        assertTileCenterRoundTrips(StationTileCoord.CORE, 87, -43);
        assertTileCenterRoundTrips(StationTileCoord.of(2, 1), 87, -43);
        assertTileCenterRoundTrips(StationTileCoord.of(-3, -2), 87, -43);
    }

    @Test
    void pointsOutsideMapContentDoNotResolveToTiles() {
        assertNull(
            StationMapViewport.coordAt(
                CONTENT_LEFT - 1,
                HEIGHT / 2,
                WIDTH,
                HEIGHT,
                TILE,
                CONTENT_LEFT,
                CONTENT_RIGHT_PADDING,
                CONTENT_VERTICAL_PADDING));
        assertNull(
            StationMapViewport.coordAt(
                WIDTH - CONTENT_RIGHT_PADDING,
                HEIGHT / 2,
                WIDTH,
                HEIGHT,
                TILE,
                CONTENT_LEFT,
                CONTENT_RIGHT_PADDING,
                CONTENT_VERTICAL_PADDING));
    }

    private static void assertTileCenterRoundTrips(StationTileCoord coord, int panX, int panY) {
        int centerX = StationMapViewport.tileLeftX(coord, WIDTH, TILE, CONTENT_LEFT, CONTENT_RIGHT_PADDING, panX)
            + TILE / 2;
        int centerY = StationMapViewport.tileTopY(coord, HEIGHT, TILE, CONTENT_VERTICAL_PADDING, panY) + TILE / 2;

        assertEquals(
            coord,
            StationMapViewport.coordAt(
                centerX,
                centerY,
                WIDTH,
                HEIGHT,
                TILE,
                CONTENT_LEFT,
                CONTENT_RIGHT_PADDING,
                CONTENT_VERTICAL_PADDING,
                panX,
                panY));
    }
}
