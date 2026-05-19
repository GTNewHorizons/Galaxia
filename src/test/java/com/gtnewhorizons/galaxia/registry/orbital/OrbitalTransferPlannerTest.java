package com.gtnewhorizons.galaxia.registry.orbital;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;

final class OrbitalTransferPlannerTest {

    @Test
    void fixedLambertRouteAcceptsNearOppositeGeometry() {
        OrbitalTransferPlanner.TransferRoute route = OrbitalTransferPlanner.solveFixedRoute(
            CelestialObjectId.VAEL,
            1.0,
            0.01,
            0.0,
            0.0,
            1.0,
            0.0,
            0.0,
            1.0,
            -1.0,
            1.0e-7,
            0.0,
            -1.0,
            Math.PI);

        assertNotNull(route);
        assertTrue(route.hasTrajectoryGeometry());
        assertTrue(route.prograde());
    }
}
