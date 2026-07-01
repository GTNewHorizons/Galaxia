package com.gtnewhorizons.galaxia.client.gui.station;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

final class ModuleFootprintProjectionTest {

    private static final int WIDGET_SIZE = 200;

    @Test
    void filledSegmentsCoverCenterGapWhenFourTilesMeet() {
        List<ModuleFootprintProjection.Segment> segments = ModuleFootprintProjection.filledSegments(
            ModuleShape.QUAD_2x2,
            StationTileCoord.CORE,
            0,
            WIDGET_SIZE,
            WIDGET_SIZE,
            0,
            0,
            0,
            0,
            0);

        int centerGapX = StationMapViewport.tileLeftX(0, WIDGET_SIZE, 0, 0, 0) + StationMapViewport.TILE_SIZE;
        int centerGapY = StationMapViewport.tileTopY(0, WIDGET_SIZE, 0, 0) + StationMapViewport.TILE_SIZE;

        assertTrue(covers(segments, centerGapX, centerGapY));
    }

    @Test
    void filledSegmentsKeepMissingCornerOutOfLShape() {
        List<ModuleFootprintProjection.Segment> segments = ModuleFootprintProjection.filledSegments(
            ModuleShape.L_2x2,
            StationTileCoord.CORE,
            0,
            WIDGET_SIZE,
            WIDGET_SIZE,
            0,
            0,
            0,
            0,
            0);

        int missingCornerX = StationMapViewport.tileLeftX(1, WIDGET_SIZE, 0, 0, 0) + StationMapViewport.TILE_SIZE / 2;
        int missingCornerY = StationMapViewport.tileTopY(0, WIDGET_SIZE, 0, 0) + StationMapViewport.TILE_SIZE / 2;

        assertFalse(covers(segments, missingCornerX, missingCornerY));
    }

    @Test
    void outlineSegmentsSkipInternalCenterGapWhenFourTilesMeet() {
        List<ModuleFootprintProjection.Segment> outline = ModuleFootprintProjection.outlineSegments(
            ModuleShape.QUAD_2x2,
            StationTileCoord.CORE,
            0,
            WIDGET_SIZE,
            WIDGET_SIZE,
            0,
            0,
            0,
            0,
            0);

        int centerGapX = StationMapViewport.tileLeftX(0, WIDGET_SIZE, 0, 0, 0) + StationMapViewport.TILE_SIZE;
        int centerGapY = StationMapViewport.tileTopY(0, WIDGET_SIZE, 0, 0) + StationMapViewport.TILE_SIZE;

        assertFalse(covers(outline, centerGapX, centerGapY));
    }

    @Test
    void containsMatchesFilledSegments() {
        int centerGapX = StationMapViewport.tileLeftX(0, WIDGET_SIZE, 0, 0, 0) + StationMapViewport.TILE_SIZE;
        int centerGapY = StationMapViewport.tileTopY(0, WIDGET_SIZE, 0, 0) + StationMapViewport.TILE_SIZE;

        assertTrue(
            ModuleFootprintProjection.contains(
                ModuleShape.QUAD_2x2,
                StationTileCoord.CORE,
                0,
                centerGapX,
                centerGapY,
                WIDGET_SIZE,
                WIDGET_SIZE,
                0,
                0,
                0,
                0,
                0));
    }

    private static boolean covers(List<ModuleFootprintProjection.Segment> segments, int x, int y) {
        for (ModuleFootprintProjection.Segment segment : segments) {
            if (segment.contains(x, y)) return true;
        }
        return false;
    }
}
