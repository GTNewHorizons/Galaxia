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
        StationCoreDirectionIndicator.Arrow arrow = StationCoreDirectionIndicator.towardCore(frame(0, -2000));

        assertTrue(arrow.unitY() < -0.99);
        assertTrue(arrow.tipY() < HEIGHT / 4);
        assertTrue(arrow.tipY() >= CONTENT_VERTICAL_PADDING);
    }

    @Test
    void arrowDirectionIsRecomputedFromTipToCore() {
        int panX = -700;
        int panY = -500;
        StationMapFrame frame = frame(panX, panY);
        StationCoreDirectionIndicator.Arrow arrow = StationCoreDirectionIndicator.towardCore(frame);
        double coreX = frame.tileLocalX(0) + StationMapFrame.TILE_SIZE * 0.5;
        double coreY = frame.tileLocalY(0) + StationMapFrame.TILE_SIZE * 0.5;
        double dx = coreX - arrow.tipX();
        double dy = coreY - arrow.tipY();
        double length = Math.hypot(dx, dy);

        assertEquals(dx / length, arrow.unitX(), 1.0E-9);
        assertEquals(dy / length, arrow.unitY(), 1.0E-9);
    }

    @Test
    void occupiedTilesIntersectScreenOnlyWhenTheirRectIsOnIt() {
        StationMapFrame frame = frame(0, 0);
        int visibleX = frame.tileLocalX(0);
        int visibleY = frame.tileLocalY(0);

        assertTrue(frame.tileIntersectsScreen(visibleX, visibleY));
        assertFalse(frame.tileIntersectsScreen(visibleX, -StationMapFrame.TILE_SIZE));
    }

    @Test
    void occupiedTilesBehindTransparentPanelsStillCountAsVisibleOnScreen() {
        StationMapFrame frame = frame(0, 0);
        int tileX = CONTENT_LEFT - StationMapFrame.TILE_SIZE - 1;
        int tileY = HEIGHT / 2;

        assertTrue(frame.tileIntersectsScreen(tileX, tileY));
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
