package com.gtnewhorizons.galaxia.registry.orbital;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Comparator;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidSlotRanges;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.MinorCelestialBodyId;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class OrbitalTransferPlannerTest {

    /** Product contract: vehicle constraints can choose either valid branch at a fixed flight time. */
    @Test
    void fixedCandidatesRetainBothBranchesAndTheirEndpointVelocities() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();
        var bodies = geometry();
        double departure = 0.2;
        double duration = bodies[1].getHohmannTof(bodies[2], bodies[3], bodies[0], departure);
        var candidates = OrbitalTransferPlanner
            .computeFixedCandidates(bodies[0], bodies[1], bodies[2], bodies[3], departure, duration);
        assertEquals(2, candidates.size());
        assertEquals(
            2,
            candidates.stream()
                .map(
                    candidate -> candidate.route()
                        .prograde())
                .distinct()
                .count());
        var origin = OrbitalMechanics.resolveWorldState(bodies[0], bodies[2], departure);
        var destination = OrbitalMechanics.resolveWorldState(bodies[0], bodies[3], departure + duration);
        var attractorAtDeparture = OrbitalMechanics.resolveWorldState(bodies[0], bodies[1], departure);
        var attractorAtArrival = OrbitalMechanics.resolveWorldState(bodies[0], bodies[1], departure + duration);
        for (var candidate : candidates) {
            assertEquals(origin.vx() - attractorAtDeparture.vx(), candidate.sourceVelocityX(), 1e-12);
            assertEquals(origin.vy() - attractorAtDeparture.vy(), candidate.sourceVelocityY(), 1e-12);
            assertEquals(destination.vx() - attractorAtArrival.vx(), candidate.destinationVelocityX(), 1e-12);
            assertEquals(destination.vy() - attractorAtArrival.vy(), candidate.destinationVelocityY(), 1e-12);
            assertEquals(destination.x() - attractorAtArrival.x(), candidate.r2x(), 1e-12);
            assertEquals(destination.y() - attractorAtArrival.y(), candidate.r2y(), 1e-12);
            assertEquals(
                candidate.route()
                    .captureDv(),
                Math.hypot(
                    candidate.arrivalVelocityX() - candidate.destinationVelocityX(),
                    candidate.arrivalVelocityY() - candidate.destinationVelocityY()),
                1e-12);
        }
    }

    /** Product contract: existing callers still select fastest or cheapest from the shared geometry. */
    @Test
    void scannedCandidatesAreRetainedBeforeTheCurrentPrioritySelectsOneRoute() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();
        var bodies = geometry();
        var candidates = OrbitalTransferPlanner.computeCandidates(bodies[0], bodies[1], bodies[2], bodies[3], 0.2);
        assertTrue(candidates.size() > 64);
        var fastest = candidates.stream()
            .map(OrbitalTransferPlanner.TransferCandidate::route)
            .min(
                Comparator.comparingDouble(OrbitalTransferPlanner.TransferRoute::tofOsu)
                    .thenComparingDouble(OrbitalTransferPlanner.TransferRoute::totalDv))
            .orElseThrow();
        var cheapest = candidates.stream()
            .map(OrbitalTransferPlanner.TransferCandidate::route)
            .min(
                Comparator.comparingDouble(OrbitalTransferPlanner.TransferRoute::totalDv)
                    .thenComparingDouble(OrbitalTransferPlanner.TransferRoute::tofOsu))
            .orElseThrow();
        assertEquals(
            fastest,
            OrbitalTransferPlanner.computeRoute(
                bodies[0],
                bodies[1],
                bodies[2],
                bodies[3],
                0.2,
                OrbitalTransferPlanner.RoutePriority.PRIORITIZE_TOF));
        assertEquals(
            cheapest,
            OrbitalTransferPlanner.computeRoute(
                bodies[0],
                bodies[1],
                bodies[2],
                bodies[3],
                0.2,
                OrbitalTransferPlanner.RoutePriority.PRIORITIZE_DV));
    }

    private static CelestialObject[] geometry() {
        return new CelestialObject[] { GalaxiaCelestialAPI.getPrimaryRoot(),
            CelestialRegistry.get(CelestialObjectId.SOL)
                .orElseThrow(),
            CelestialRegistry.get(CelestialObjectId.OVERWORLD)
                .orElseThrow(),
            CelestialRegistry.get(CelestialObjectId.MARS)
                .orElseThrow() };
    }

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

    /** Product contract: unknown physical clearance is unavailable rather than inferred from presentation sprites. */
    @Test
    void unknownPhysicalRadiusDoesNotClaimValidatedOrbitalTransfers() {
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
            org.junit.jupiter.api.Assertions.assertNull(
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

    /** Integration contract: headless circular families use the same Lambert candidates and effective speeds. */
    @Test
    void circularFamilyMatchesGameGeometryAndMeasuresBoundedBakeCost() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();
        var root = GalaxiaCelestialAPI.getPrimaryRoot();
        var star = CelestialRegistry.get(CelestialObjectId.SOL)
            .orElseThrow();
        var earth = CelestialRegistry.get(CelestialObjectId.OVERWORLD)
            .orElseThrow();
        var mars = CelestialRegistry.get(CelestialObjectId.MARS)
            .orElseThrow();
        double omegaA = OrbitalMechanics.getOrbitDirectionSign(earth) * 2
            * Math.PI
            / OrbitalMechanics.getOrbitalPeriod(star, earth.orbitalParams());
        double omegaB = OrbitalMechanics.getOrbitDirectionSign(mars) * 2
            * Math.PI
            / OrbitalMechanics.getOrbitalPeriod(star, mars.orbitalParams());
        var source = OrbitalMechanics.resolveWorldState(root, earth, 0);
        var target = OrbitalMechanics.resolveWorldState(root, mars, 0);
        double phase = Math.atan2(target.y(), target.x()) - Math.atan2(source.y(), source.x());
        var actual = OrbitalTransferPlanner.computeCandidates(root, star, earth, mars, 0);
        var headless = OrbitalTransferPlanner.computeCircularCandidates(
            star,
            earth.orbitalParams()
                .semiMajorAxis(),
            omegaA,
            mars.orbitalParams()
                .semiMajorAxis(),
            omegaB,
            phase);
        assertEquals(actual.size(), headless.size());
        for (int i = 0; i < actual.size(); i++) {
            assertEquals(
                actual.get(i)
                    .route()
                    .prograde(),
                headless.get(i)
                    .route()
                    .prograde());
            assertEquals(
                actual.get(i)
                    .route()
                    .tofOsu(),
                headless.get(i)
                    .route()
                    .tofOsu(),
                1e-8);
            assertEquals(
                actual.get(i)
                    .route()
                    .departureDv(),
                headless.get(i)
                    .route()
                    .departureDv(),
                1e-7);
            assertEquals(
                actual.get(i)
                    .route()
                    .captureDv(),
                headless.get(i)
                    .route()
                    .captureDv(),
                1e-7);
        }
        long start = System.nanoTime();
        long retained = 0;
        for (int i = 0; i < 32; i++) retained += OrbitalTransferPlanner
            .computeCircularCandidates(
                star,
                earth.orbitalParams()
                    .semiMajorAxis(),
                omegaA,
                mars.orbitalParams()
                    .semiMajorAxis(),
                omegaB,
                2 * Math.PI * i / 32)
            .size();
        System.out.println(
            "FORECAST_FAMILY_MEASURE phases=32 candidates=" + retained
                + " millis="
                + (System.nanoTime() - start) / 1e6
                + " rawCostBytes="
                + (retained * 41));
    }

    @Test
    void nearOppositeTransferGeometryRemainsSolvable() {
        assertFalse(OrbitalTransferPlanner.isDegenerateTransferGeometry(1, 0, -1, 1e-7));
        var solution = LambertTransfer.between(1, 0, -1, 1e-7)
            .mu(1)
            .minPeriapsis(0.01)
            .timeOfFlight(Math.PI)
            .prograde(true)
            .evaluateAgainst(0, 1, 0, -1);
        assertTrue(solution.valid());
    }
}
