package com.gtnewhorizons.galaxia.registry.outpost.logistics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class HammerTrajectoryLoadTrackerTest {

    private static final long ONE_MS_NANOS = 1_000_000L;

    @BeforeEach
    @AfterEach
    void resetTracker() {
        HammerTrajectoryLoadTracker.reset();
    }

    @Test
    void separatesOwnTeamLoadFromAllServerLoad() {
        UUID ownTeam = UUID.randomUUID();
        UUID otherTeam = UUID.randomUUID();

        HammerTrajectoryLoadTracker.beginTick();
        HammerTrajectoryLoadTracker.recordRouteComputation(ownTeam, ONE_MS_NANOS);
        HammerTrajectoryLoadTracker.recordRouteComputation(otherTeam, 2L * ONE_MS_NANOS);
        HammerTrajectoryLoadTracker.endTick();

        HammerTrajectoryLoadTracker.Snapshot snapshot = HammerTrajectoryLoadTracker.snapshot(ownTeam);

        assertEquals(1.0, snapshot.ownMsPerTick(), 1e-9);
        assertEquals(3.0, snapshot.allMsPerTick(), 1e-9);
    }

    @Test
    void averagesOverLastTenSecondsOfServerTicks() {
        UUID ownTeam = UUID.randomUUID();

        for (int tick = 0; tick < 200; tick++) {
            HammerTrajectoryLoadTracker.beginTick();
            if (tick < 20) {
                HammerTrajectoryLoadTracker.recordRouteComputation(ownTeam, ONE_MS_NANOS);
            }
            HammerTrajectoryLoadTracker.endTick();
        }

        HammerTrajectoryLoadTracker.Snapshot snapshot = HammerTrajectoryLoadTracker.snapshot(ownTeam);

        assertEquals(0.1, snapshot.ownMsPerTick(), 1e-9);
        assertEquals(0.1, snapshot.allMsPerTick(), 1e-9);
    }

    @Test
    void disabledTicksIgnoreRouteComputations() {
        UUID ownTeam = UUID.randomUUID();

        HammerTrajectoryLoadTracker.beginTick(false);
        HammerTrajectoryLoadTracker.recordRouteComputation(ownTeam, ONE_MS_NANOS);
        HammerTrajectoryLoadTracker.endTick();

        HammerTrajectoryLoadTracker.Snapshot snapshot = HammerTrajectoryLoadTracker.snapshot(ownTeam);

        assertEquals(0.0, snapshot.ownMsPerTick(), 1e-9);
        assertEquals(0.0, snapshot.allMsPerTick(), 1e-9);
    }
}
