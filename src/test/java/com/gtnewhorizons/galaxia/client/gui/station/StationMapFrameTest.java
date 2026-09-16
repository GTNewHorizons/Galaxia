package com.gtnewhorizons.galaxia.client.gui.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

final class StationMapFrameTest {

    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final int CONTENT_LEFT = 228;
    private static final int CONTENT_RIGHT_PADDING = 12;
    private static final int CONTENT_VERTICAL_PADDING = 12;
    private static final StationMapFrame FRAME = frame(0, 0);

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
        assertNull(FRAME.coordAt(CONTENT_LEFT - 1, HEIGHT / 2));
        assertNull(FRAME.coordAt(WIDTH - CONTENT_RIGHT_PADDING, HEIGHT / 2));
    }

    @Test
    void pointsInConnectorGapDoNotResolveToTiles() {
        int gapX = FRAME.tileLocalX(StationTileCoord.CORE) + StationMapFrame.TILE_SIZE
            + StationMapFrame.CONNECTOR_GAP / 2;
        int centerY = FRAME.tileLocalY(StationTileCoord.CORE) + StationMapFrame.TILE_SIZE / 2;

        assertNull(FRAME.coordAt(gapX, centerY));
    }

    @Test
    void connectorGeometryOverlapsBothNeighbouringTileEdges() {
        StationTileCoord left = StationTileCoord.CORE;
        StationTileCoord right = StationTileCoord.of(1, 0);
        int leftTileX = FRAME.tileLocalX(left);
        int rightTileX = FRAME.tileLocalX(right);
        int connectorX = FRAME.connectorLocalX(left);

        assertEquals(leftTileX + StationMapFrame.TILE_SIZE - StationMapFrame.CONNECTOR_OVERLAP, connectorX);
        assertEquals(rightTileX + StationMapFrame.CONNECTOR_OVERLAP, connectorX + StationMapFrame.CONNECTOR_SIZE);

        StationTileCoord upper = StationTileCoord.CORE;
        StationTileCoord lower = StationTileCoord.of(0, 1);
        int upperTileY = FRAME.tileLocalY(upper);
        int lowerTileY = FRAME.tileLocalY(lower);
        int connectorY = FRAME.connectorLocalY(upper);

        assertEquals(upperTileY + StationMapFrame.TILE_SIZE - StationMapFrame.CONNECTOR_OVERLAP, connectorY);
        assertEquals(lowerTileY + StationMapFrame.CONNECTOR_OVERLAP, connectorY + StationMapFrame.CONNECTOR_SIZE);
    }

    @Test
    void pannedHitboxesAcceptTilePixelsAndRejectConnectorGapPixels() {
        StationTileCoord coord = StationTileCoord.of(2, -1);
        StationMapFrame frame = frame(-73, 41);
        int tileX = frame.tileLocalX(coord);
        int tileY = frame.tileLocalY(coord);

        assertEquals(
            coord,
            frame.coordAt(tileX + StationMapFrame.TILE_SIZE - 1, tileY + StationMapFrame.TILE_SIZE - 1));
        assertNull(frame.coordAt(tileX + StationMapFrame.TILE_SIZE, tileY + StationMapFrame.TILE_SIZE / 2));
    }

    private static void assertTileCenterRoundTrips(StationTileCoord coord, int panX, int panY) {
        StationMapFrame frame = frame(panX, panY);
        int centerX = frame.tileLocalX(coord) + StationMapFrame.TILE_SIZE / 2;
        int centerY = frame.tileLocalY(coord) + StationMapFrame.TILE_SIZE / 2;

        assertEquals(coord, frame.coordAt(centerX, centerY));
    }

    private static StationMapFrame frame(int panX, int panY) {
        return new StationMapFrame(
            WIDTH,
            HEIGHT,
            CONTENT_LEFT,
            CONTENT_RIGHT_PADDING,
            CONTENT_VERTICAL_PADDING,
            panX,
            panY);
    }
}
