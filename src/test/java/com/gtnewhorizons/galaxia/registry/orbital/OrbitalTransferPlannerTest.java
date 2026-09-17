package com.gtnewhorizons.galaxia.registry.orbital;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidSlotRanges;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.MinorCelestialBodyId;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class OrbitalTransferPlannerTest {

    /** Bug regression: long transfers across Sol must end at the destination with the normal rendering budget. */
    @Test
    void sampledLongSolarTransfersReachTheirDestination() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();
        var root = GalaxiaCelestialAPI.getPrimaryRoot();
        var star = CelestialRegistry.get(CelestialObjectId.SOL)
            .orElseThrow();
        var pairs = new CelestialObjectId[][] { { CelestialObjectId.MERCURY, CelestialObjectId.NEPTUNE },
            { CelestialObjectId.OVERWORLD, CelestialObjectId.PLUTO },
            { CelestialObjectId.NEPTUNE, CelestialObjectId.MERCURY },
            { CelestialObjectId.PLUTO, CelestialObjectId.OVERWORLD } };
        for (int i = 0; i < pairs.length; i++) {
            var source = CelestialRegistry.get(pairs[i][0])
                .orElseThrow();
            var destination = CelestialRegistry.get(pairs[i][1])
                .orElseThrow();
            var route = OrbitalTransferPlanner.computeFixedRoute(
                root,
                star,
                source,
                destination,
                100,
                star.getHohmannTof(source, destination, root, 100));
            assertNotNull(route);
            double[] xs = new double[96];
            double[] ys = new double[96];
            assertEquals(
                xs.length,
                OrbitalTransferPlanner.sampleTransferArcInto(
                    route.anchorX(),
                    route.anchorY(),
                    route.r1x(),
                    route.r1y(),
                    route.departureVelocityX(),
                    route.departureVelocityY(),
                    route.tofOsu(),
                    star.mu(),
                    xs,
                    ys,
                    xs.length));
            var expected = OrbitalMechanics.resolveWorldState(root, destination, 100 + route.tofOsu());
            assertNotNull(expected);
            double error = Math.hypot(xs[xs.length - 1] - expected.x(), ys[ys.length - 1] - expected.y());
            assertTrue(
                error < destination.orbitalParams()
                    .semiMajorAxis() * 1e-4,
                pairs[i][0] + " -> " + pairs[i][1] + " missed the destination by " + error);
        }
    }

    /** Bug regression: imported stars provide gravity for transfers between their orbiting destinations. */
    @Test
    void importedStarSystemsSupportOrbitalTransfers() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();
        var root = GalaxiaCelestialAPI.getPrimaryRoot();
        var routes = new CelestialObjectId[][] {
            { CelestialObjectId.TCETI_A, CelestialObjectId.TCETI_B, CelestialObjectId.TCETI_E },
            { CelestialObjectId.VEGA, CelestialObjectId.VEGA_B, CelestialObjectId.VEGA_C },
            { CelestialObjectId.ALPHA_CENTAURI_A, CelestialObjectId.CENTAURI_BB, CelestialObjectId.ALPHA_CENTAURI_B } };
        for (var route : routes) {
            var star = CelestialRegistry.get(route[0])
                .orElseThrow();
            var source = CelestialRegistry.get(route[1])
                .orElseThrow();
            var destination = CelestialRegistry.get(route[2])
                .orElseThrow();
            assertNotNull(
                OrbitalTransferPlanner.computeRoute(
                    root,
                    star,
                    source,
                    destination,
                    0.0,
                    OrbitalTransferPlanner.RoutePriority.PRIORITIZE_TOF),
                route[0].name());
        }
    }

    @Test
    void routesConnectGeneratedAsteroidsAndPlanetsInBothDirections() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();
        var root = GalaxiaCelestialAPI.getPrimaryRoot();
        var star = CelestialRegistry.get(CelestialObjectId.SOL)
            .orElseThrow();
        var planet = CelestialRegistry.get(CelestialObjectId.OVERWORLD)
            .orElseThrow();
        var asteroid = CelestialRegistry
            .get(
                CelestialObjectKey.minorBody(
                    new MinorCelestialBodyId(CelestialObjectId.FROZEN_BELT, AsteroidSlotRanges.generatedSlot(9))))
            .orElseThrow();
        for (double time : new double[] { 0.0, 100.0, 1000.0 }) {
            assertNotNull(
                OrbitalTransferPlanner.computeRoute(
                    root,
                    star,
                    asteroid,
                    planet,
                    time,
                    OrbitalTransferPlanner.RoutePriority.PRIORITIZE_TOF),
                "asteroid to planet at " + time);
            assertNotNull(
                OrbitalTransferPlanner.computeRoute(
                    root,
                    star,
                    planet,
                    asteroid,
                    time,
                    OrbitalTransferPlanner.RoutePriority.PRIORITIZE_TOF),
                "planet to asteroid at " + time);
        }
    }

    @Test
    void fixedLambertRouteAcceptsNearOppositeGeometry() {
        OrbitalTransferPlanner.TransferRoute route = OrbitalTransferPlanner.solveFixedRoute(
            CelestialObjectKey.registered(CelestialObjectId.VAEL),
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
