package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class OrbitalClockTest {

    private static final double SERVER_UNITS_PER_SECOND = 20.0;
    private static final boolean IN_WORLD = true;

    private static OrbitalClock realTimeClock() {
        return new OrbitalClock(SERVER_UNITS_PER_SECOND, SERVER_UNITS_PER_SECOND);
    }

    @Test
    void firstFrameInAWorldAdoptsTheServerTime() {
        OrbitalClock clock = realTimeClock();

        clock.advance(IN_WORLD, 1000.0);

        assertEquals(1000.0, clock.time(), 1.0e-9);
    }

    @Test
    void displayTimeTracksTheServerOnceAnchored() {
        OrbitalClock clock = realTimeClock();
        clock.advance(IN_WORLD, 1000.0);

        clock.advance(IN_WORLD, 1060.0);

        assertEquals(1060.0, clock.time(), 1.0e-9);
    }

    @Test
    void pausingHoldsTheDisplayTimeWhileTheServerRunsOn() {
        OrbitalClock clock = realTimeClock();
        clock.advance(IN_WORLD, 1000.0);

        clock.togglePaused(IN_WORLD, 1000.0);
        clock.advance(IN_WORLD, 2000.0);

        assertTrue(clock.isPaused());
        assertEquals(1000.0, clock.time(), 1.0e-9);
    }

    @Test
    void resumingContinuesFromWhereItPausedInsteadOfJumping() {
        OrbitalClock clock = realTimeClock();
        clock.advance(IN_WORLD, 1000.0);
        clock.togglePaused(IN_WORLD, 1000.0);
        clock.advance(IN_WORLD, 2000.0);

        clock.togglePaused(IN_WORLD, 2000.0);
        clock.advance(IN_WORLD, 2000.0);

        assertFalse(clock.isPaused());
        assertEquals(1000.0, clock.time(), 1.0e-9, "resuming must not jump forward by the paused interval");

        clock.advance(IN_WORLD, 2050.0);
        assertEquals(1050.0, clock.time(), 1.0e-9);
    }

    @Test
    void aFasterTimeScaleStretchesDisplayTimeAgainstServerTime() {
        OrbitalClock clock = new OrbitalClock(2.0 * SERVER_UNITS_PER_SECOND, SERVER_UNITS_PER_SECOND);
        clock.advance(IN_WORLD, 1000.0);

        clock.advance(IN_WORLD, 1010.0);

        assertEquals(1020.0, clock.time(), 1.0e-9);
    }

    @Test
    void revisionBumpsWhenRepinnedAndStaysPutWhileMerelyTracking() {
        OrbitalClock clock = realTimeClock();
        clock.advance(IN_WORLD, 1000.0);
        int afterFirstAnchor = clock.revision();

        clock.advance(IN_WORLD, 1010.0);
        assertEquals(afterFirstAnchor, clock.revision(), "tracking the server must not invalidate time-keyed caches");

        clock.togglePaused(IN_WORLD, 1010.0);
        assertTrue(clock.revision() > afterFirstAnchor);
    }

    @Test
    void capturingWithoutAWorldReportsTheLastDisplayTime() {
        OrbitalClock clock = realTimeClock();
        clock.advance(IN_WORLD, 1000.0);

        assertEquals(clock.time(), clock.captureDisplayTime(false, 9999.0), 1.0e-9);
    }

    @Test
    void reanchoringWithoutAWorldMovesTheClockButKeepsTheServerAnchor() {
        OrbitalClock clock = realTimeClock();
        clock.advance(IN_WORLD, 1000.0);

        clock.reanchor(500.0, false, 9999.0);

        assertEquals(500.0, clock.time(), 1.0e-9);
        assertEquals(
            1000.0,
            clock.toDisplayTime(1000.0),
            1.0e-9,
            "the server anchor must survive a worldless reanchor");
    }
}
