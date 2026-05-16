package com.gtnewhorizons.galaxia.client.gui.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class StationCoreDirectionIndicatorTest {

    private static final int WIDTH = 320;
    private static final int HEIGHT = 240;
    private static final int CONTENT_LEFT = 20;
    private static final int CONTENT_RIGHT_PADDING = 20;
    private static final int CONTENT_VERTICAL_PADDING = 12;

    @Test
    void arrowPointsTowardCoreWhenViewportIsPannedAway() {
        StationCoreDirectionIndicator.Arrow arrow = StationCoreDirectionIndicator
            .towardCore(WIDTH, HEIGHT, CONTENT_LEFT, CONTENT_RIGHT_PADDING, CONTENT_VERTICAL_PADDING, 0, -2000);

        assertTrue(arrow.unitY() < -0.99);
        assertTrue(arrow.tipY() < HEIGHT / 4);
        assertTrue(arrow.tipY() >= CONTENT_VERTICAL_PADDING);
    }

    @Test
    void arrowDirectionIsRecomputedFromTipToCore() {
        int panX = -700;
        int panY = -500;
        StationCoreDirectionIndicator.Arrow arrow = StationCoreDirectionIndicator
            .towardCore(WIDTH, HEIGHT, CONTENT_LEFT, CONTENT_RIGHT_PADDING, CONTENT_VERTICAL_PADDING, panX, panY);
        double coreX = StationMapViewport.tileLeftX(0, WIDTH, CONTENT_LEFT, CONTENT_RIGHT_PADDING, panX)
            + StationMapViewport.TILE_SIZE * 0.5;
        double coreY = StationMapViewport.tileTopY(0, HEIGHT, CONTENT_VERTICAL_PADDING, panY)
            + StationMapViewport.TILE_SIZE * 0.5;
        double dx = coreX - arrow.tipX();
        double dy = coreY - arrow.tipY();
        double length = Math.hypot(dx, dy);

        assertEquals(dx / length, arrow.unitX(), 1.0E-9);
        assertEquals(dy / length, arrow.unitY(), 1.0E-9);
    }

    @Test
    void occupiedTilesIntersectViewportOnlyWhenTheirRectIsVisible() {
        int visibleX = StationMapViewport.tileLeftX(0, WIDTH, CONTENT_LEFT, CONTENT_RIGHT_PADDING, 0);
        int visibleY = StationMapViewport.tileTopY(0, HEIGHT, CONTENT_VERTICAL_PADDING, 0);

        assertTrue(
            StationCoreDirectionIndicator.tileIntersectsViewport(
                visibleX,
                visibleY,
                WIDTH,
                HEIGHT,
                CONTENT_LEFT,
                CONTENT_RIGHT_PADDING,
                CONTENT_VERTICAL_PADDING));
        assertFalse(
            StationCoreDirectionIndicator.tileIntersectsViewport(
                visibleX,
                -StationMapViewport.TILE_SIZE,
                WIDTH,
                HEIGHT,
                CONTENT_LEFT,
                CONTENT_RIGHT_PADDING,
                CONTENT_VERTICAL_PADDING));
    }

    @Test
    void occupiedTilesBehindTransparentPanelsStillCountAsVisibleOnScreen() {
        int tileX = CONTENT_LEFT - StationMapViewport.TILE_SIZE - 1;
        int tileY = HEIGHT / 2;

        assertFalse(
            StationCoreDirectionIndicator.tileIntersectsViewport(
                tileX,
                tileY,
                WIDTH,
                HEIGHT,
                CONTENT_LEFT,
                CONTENT_RIGHT_PADDING,
                CONTENT_VERTICAL_PADDING));
        assertTrue(StationCoreDirectionIndicator.tileIntersectsScreen(tileX, tileY, WIDTH, HEIGHT));
    }
}
