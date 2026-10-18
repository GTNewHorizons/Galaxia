package com.gtnewhorizons.galaxia.registry.orbital;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class OrbitalPoweredTransferTest {

    private static final OrbitalMechanics.CostModel MODEL = new OrbitalMechanics.CostModel(
        1000,
        100,
        .35,
        .05,
        20,
        20,
        10);

    /** Product contract: prepared orbital endpoints pay each docking operation with actual fuel and finite time. */
    @Test
    void preparedOrbitalStopsPayUndockingAndDockingThroughTheSharedProgram() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();
        var root = GalaxiaCelestialAPI.getPrimaryRoot();
        var earth = CelestialRegistry.get(CelestialObjectId.OVERWORLD)
            .orElseThrow();
        var vehicle = new OrbitalMechanics.LocalVehicle(1400, 30800, 5200, 800000);
        var plan = OrbitalTransferPlanner.computePoweredCandidates(
            root,
            earth,
            earth,
            0,
            OrbitalTransferPlanner.EndpointKind.DOCK,
            .7,
            OrbitalTransferPlanner.EndpointKind.DOCK,
            1,
            vehicle,
            MODEL);
        var selected = OrbitalTransferPlanner.selectPoweredCandidate(
            plan,
            2 * MODEL.dockingDeltaVMps(),
            OrbitalTransferPlanner.RoutePriority.PRIORITIZE_DV);
        assertNotNull(selected);
        assertEquals(
            java.util.List.of(OrbitalMechanics.ManeuverKind.UNDOCKING, OrbitalMechanics.ManeuverKind.DOCKING),
            selected.obligations()
                .stream()
                .map(OrbitalMechanics.ManeuverObligation::kind)
                .toList());
        assertEquals(2 * MODEL.dockingDeltaVMps(), selected.totalDvMps());
        assertEquals(
            vehicle.wetMassKg() * Math.exp(-selected.totalDvMps() / vehicle.exhaustVelocityMps()),
            selected.finalMassKg(),
            1e-8);
        assertTrue(
            selected.propellantKg() > 0 && selected.physicalSeconds() > 0 && selected.traveledDistanceMeters() > 0);
        assertEquals(
            selected.obligations()
                .getFirst()
                .endEpochOsu(),
            selected.obligations()
                .getLast()
                .startEpochOsu());
        assertEquals(selected, OrbitalTransferPlanner.PoweredCandidate.deserializeNBT(selected.serializeNBT()));
        assertNull(
            OrbitalTransferPlanner.selectPoweredCandidate(
                plan,
                selected.totalDvMps() - 1,
                OrbitalTransferPlanner.RoutePriority.PRIORITIZE_DV));
        var bound = OrbitalTransferPlanner.maximumTravelExposure(
            root,
            earth,
            earth,
            OrbitalTransferPlanner.EndpointKind.DOCK,
            OrbitalTransferPlanner.EndpointKind.DOCK,
            selected.totalDvMps(),
            MODEL);
        assertNotNull(bound);
        assertTrue(bound.distanceMeters() >= selected.traveledDistanceMeters());
        for (var source : OrbitalTransferPlanner.EndpointKind.values()) {
            if (source == OrbitalTransferPlanner.EndpointKind.SURFACE) continue;
            var one = OrbitalTransferPlanner.computePoweredCandidates(
                root,
                earth,
                earth,
                0,
                source,
                0,
                source == OrbitalTransferPlanner.EndpointKind.DOCK ? OrbitalTransferPlanner.EndpointKind.PARKING
                    : OrbitalTransferPlanner.EndpointKind.DOCK,
                1,
                vehicle,
                MODEL);
            assertEquals(
                MODEL.dockingDeltaVMps(),
                one.candidates()
                    .getFirst()
                    .totalDvMps());
        }
        var free = OrbitalTransferPlanner.computePoweredCandidates(
            root,
            earth,
            earth,
            0,
            OrbitalTransferPlanner.EndpointKind.PARKING,
            0,
            OrbitalTransferPlanner.EndpointKind.PARKING,
            1,
            vehicle,
            MODEL);
        assertEquals(
            0,
            free.candidates()
                .getFirst()
                .totalDvMps());
        assertEquals(0, new OrbitalMechanics.LocalVehicle(1400, 0, 5200, 800000).usableDeltaVMps());
        assertEquals(
            vehicle.usableDeltaVMps(),
            new OrbitalMechanics.LocalVehicle(2800, 61600, 5200, 1600000).usableDeltaVMps(),
            1e-10);
    }

    /** Product contract: local flight charges actual mixture and finite engine time, with no exact steering claim. */
    @Test
    void approximateAscentAndLandingUseActualMassAndPressureCredit() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();
        var body = CelestialRegistry.get(CelestialObjectId.OVERWORLD)
            .orElseThrow();
        var vehicle = new OrbitalMechanics.LocalVehicle(1400, 30800, 3200, 800000);
        var plan = OrbitalTransferPlanner.computePoweredCandidates(
            GalaxiaCelestialAPI.getPrimaryRoot(),
            body,
            body,
            0,
            OrbitalTransferPlanner.EndpointKind.SURFACE,
            .7,
            OrbitalTransferPlanner.EndpointKind.PARKING,
            1,
            vehicle,
            MODEL);
        var ascent = plan.candidates()
            .getFirst();
        assertEquals(OrbitalMechanics.LocalStatus.FEASIBLE, ascent.status());
        assertEquals(OrbitalTransferPlanner.POWERED_MODEL_ID, ascent.modelId());
        assertEquals(.7, ascent.finalParkingAngleRadians());
        assertEquals(vehicle.wetMassKg() * Math.exp(-ascent.totalDvMps() / 3200), ascent.finalMassKg(), 1e-6);
        assertEquals(
            ascent.propellantKg() * 3200 / 800000,
            ascent.obligations()
                .getFirst()
                .burnPhysicalSeconds(),
            1e-6);
        assertEquals(
            Math.max(ascent.physicalSeconds() / MODEL.localTimeScale(), MODEL.minimumSurfaceGameSeconds()),
            ascent.gameSeconds(),
            1e-8);
        double landing = OrbitalMechanics.surfaceManeuverDeltaV(body.properties(), MODEL, true);
        double ascentDv = OrbitalMechanics.surfaceManeuverDeltaV(body.properties(), MODEL, false);
        assertTrue(landing > 0 && landing < ascentDv);
        assertEquals(200000, ascent.traveledDistanceMeters(), 1e-6);
        assertTrue(
            ascent.beltDistanceMeters()
                .isEmpty());
        assertEquals(ascent, OrbitalTransferPlanner.PoweredCandidate.deserializeNBT(ascent.serializeNBT()));
    }

    /** Integration contract: Earth-to-Moon starts in a real parent parking orbit and charges the Moon capture well. */
    @Test
    void parentToChildWitnessChargesEveryWellAndRetainsAllBranchesBeforeRanking() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();
        var root = GalaxiaCelestialAPI.getPrimaryRoot();
        var earth = CelestialRegistry.get(CelestialObjectId.OVERWORLD)
            .orElseThrow();
        var moon = CelestialRegistry.get(CelestialObjectId.MOON)
            .orElseThrow();
        var vehicle = new OrbitalMechanics.LocalVehicle(1400, 30800, 3200, 4000000);
        long start = System.nanoTime();
        var plan = OrbitalTransferPlanner.computePoweredCandidates(
            root,
            earth,
            moon,
            0,
            OrbitalTransferPlanner.EndpointKind.PARKING,
            .4,
            OrbitalTransferPlanner.EndpointKind.PARKING,
            1,
            vehicle,
            MODEL);
        var selected = OrbitalTransferPlanner
            .selectPoweredCandidate(plan, Double.POSITIVE_INFINITY, OrbitalTransferPlanner.RoutePriority.PRIORITIZE_DV);
        System.out.println(
            "APPROX_OW_MOON candidates=" + plan.candidates()
                .size() + " millis=" + (System.nanoTime() - start) / 1e6 + " chosen=" + selected);
        assertNotNull(selected);
        printDistance("MOON", plan, selected);
        assertTrue(
            plan.candidates()
                .stream()
                .anyMatch(
                    c -> c.geometrySeed()
                        .route()
                        .prograde()));
        assertTrue(
            plan.candidates()
                .stream()
                .anyMatch(
                    c -> !c.geometrySeed()
                        .route()
                        .prograde()));
        var departure = selected.obligations()
            .stream()
            .filter(o -> o.kind() == OrbitalMechanics.ManeuverKind.DEPARTURE)
            .findFirst()
            .orElseThrow();
        var capture = selected.obligations()
            .stream()
            .filter(o -> o.kind() == OrbitalMechanics.ManeuverKind.CAPTURE)
            .findFirst()
            .orElseThrow();
        assertEquals(
            OrbitalMechanics.velocityToMetresPerSecond(
                selected.geometrySeed()
                    .route()
                    .departureDv()),
            departure.deltaVMps(),
            1e-7);
        assertEquals(
            OrbitalMechanics.parkingWellDeltaV(
                moon.properties(),
                OrbitalMechanics.velocityToMetresPerSecond(
                    selected.geometrySeed()
                        .route()
                        .captureDv())),
            capture.deltaVMps(),
            1e-7);
        assertEquals(departure.deltaVMps() + capture.deltaVMps(), selected.totalDvMps(), 1e-7);
        var anchor = OrbitalMechanics.resolveWorldState(root, earth, departure.endEpochOsu());
        assertEquals(
            anchor.x(),
            selected.geometrySeed()
                .route()
                .anchorX(),
            1e-6);
        assertEquals(
            anchor.y(),
            selected.geometrySeed()
                .route()
                .anchorY(),
            1e-6);
        assertTrue(
            selected.obligations()
                .getFirst()
                .kind() == OrbitalMechanics.ManeuverKind.PARKING_COAST);
        assertEquals(selected, OrbitalTransferPlanner.PoweredCandidate.deserializeNBT(selected.serializeNBT()));
        assertNull(
            OrbitalTransferPlanner.selectPoweredCandidate(
                plan,
                selected.totalDvMps() - 1,
                OrbitalTransferPlanner.RoutePriority.PRIORITIZE_DV));
    }

    /** Integration contract: direct interplanetary routes charge both endpoint wells in the same pipeline. */
    @Test
    void interplanetaryWitnessIncludesBothParkingWellCharges() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();
        var earth = CelestialRegistry.get(CelestialObjectId.OVERWORLD)
            .orElseThrow();
        var mars = CelestialRegistry.get(CelestialObjectId.MARS)
            .orElseThrow();
        var plan = OrbitalTransferPlanner.computePoweredCandidates(
            GalaxiaCelestialAPI.getPrimaryRoot(),
            earth,
            mars,
            0,
            OrbitalTransferPlanner.EndpointKind.PARKING,
            0,
            OrbitalTransferPlanner.EndpointKind.PARKING,
            1,
            new OrbitalMechanics.LocalVehicle(1400, 30800, 3200, 4000000),
            MODEL);
        var chosen = OrbitalTransferPlanner
            .selectPoweredCandidate(plan, Double.POSITIVE_INFINITY, OrbitalTransferPlanner.RoutePriority.PRIORITIZE_DV);
        assertNotNull(chosen);
        printDistance("MARS", plan, chosen);
        assertEquals(
            2,
            chosen.obligations()
                .stream()
                .filter(
                    o -> o.kind() == OrbitalMechanics.ManeuverKind.DEPARTURE
                        || o.kind() == OrbitalMechanics.ManeuverKind.CAPTURE)
                .count());
        assertTrue(
            chosen.obligations()
                .stream()
                .anyMatch(
                    o -> o.bodyKey()
                        .equals(mars.key()) && o.deltaVMps() > 0));
        System.out.println(
            "APPROX_OW_MARS candidates=" + plan.candidates()
                .size() + " chosen=" + chosen);
    }

    /** Product contract: the approved basic physical vehicle has a finite-burn, whole-route lunar landing witness. */
    @Test
    void approvedBasicVehicleHasLunarSurfaceWitnessWithinVehicleDuration() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();
        var earth = CelestialRegistry.get(CelestialObjectId.OVERWORLD)
            .orElseThrow();
        var moon = CelestialRegistry.get(CelestialObjectId.MOON)
            .orElseThrow();
        var plan = OrbitalTransferPlanner.computePoweredCandidates(
            GalaxiaCelestialAPI.getPrimaryRoot(),
            earth,
            moon,
            0,
            OrbitalTransferPlanner.EndpointKind.SURFACE,
            0,
            OrbitalTransferPlanner.EndpointKind.SURFACE,
            1,
            new OrbitalMechanics.LocalVehicle(1400, 30800, 5200, 800000),
            MODEL);
        var eligible = new OrbitalTransferPlanner.PoweredPlan(
            plan.status(),
            plan.candidates()
                .stream()
                .filter(c -> c.gameSeconds() <= 180)
                .toList());
        var selected = OrbitalTransferPlanner.selectPoweredCandidate(
            eligible,
            Double.POSITIVE_INFINITY,
            OrbitalTransferPlanner.RoutePriority.PRIORITIZE_DV);
        assertNotNull(selected, "Approved basic vehicle must have a bounded lunar landing witness");
        assertTrue(
            selected.obligations()
                .stream()
                .allMatch(o -> o.status() == OrbitalMechanics.LocalStatus.FEASIBLE));
        assertTrue(selected.finalMassKg() >= 1400);
        assertTrue(selected.gameSeconds() <= 180);
        System.out.println(
            "BASIC_LUNAR_WITNESS epoch=0 dv=" + selected.totalDvMps()
                + " fuelKg="
                + selected.propellantKg()
                + " finalKg="
                + selected.finalMassKg()
                + " gameSeconds="
                + selected.gameSeconds()
                + " pathMeters="
                + selected.traveledDistanceMeters());
        selected.obligations()
            .forEach(o -> System.out.println("BASIC_LUNAR_PHASE " + o));
    }

    /** Product contract: long-burn approximation and real liftoff thrust are eligibility constraints. */
    @Test
    void insufficientThrustAndLongBurnsAreNotFreeSuccessfulTrips() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();
        var earth = CelestialRegistry.get(CelestialObjectId.OVERWORLD)
            .orElseThrow();
        var weak = new OrbitalMechanics.LocalVehicle(1400, 30800, 3200, 100000);
        var plan = OrbitalTransferPlanner.computePoweredCandidates(
            GalaxiaCelestialAPI.getPrimaryRoot(),
            earth,
            earth,
            0,
            OrbitalTransferPlanner.EndpointKind.SURFACE,
            0,
            OrbitalTransferPlanner.EndpointKind.PARKING,
            1,
            weak,
            MODEL);
        assertEquals(OrbitalMechanics.LocalStatus.INSUFFICIENT_THRUST, plan.status());
        var burn = OrbitalMechanics.approximateBurn(
            OrbitalMechanics.ManeuverKind.DEPARTURE,
            earth,
            new OrbitalMechanics.LocalVehicle(1400, 30800, 3200, 100000),
            32200,
            3000,
            0,
            MODEL);
        assertEquals(OrbitalMechanics.LocalStatus.APPROXIMATION_LIMIT, burn.status());
    }

    /** Product contract: landing thrust uses the mass that actually reaches the landing burn. */
    @Test
    void landingThrustUsesCurrentMassAfterEarlierBurns() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();
        var earth = CelestialRegistry.get(CelestialObjectId.OVERWORLD)
            .orElseThrow();
        var vehicle = new OrbitalMechanics.LocalVehicle(1000, 3000, 10000, 30000);
        var landing = OrbitalMechanics.approximateBurn(
            OrbitalMechanics.ManeuverKind.LANDING,
            earth,
            vehicle,
            2500,
            OrbitalMechanics.surfaceManeuverDeltaV(earth.properties(), MODEL, true),
            0,
            MODEL);
        assertEquals(OrbitalMechanics.LocalStatus.FEASIBLE, landing.status());
        assertTrue(landing.thrustToWeight() > 1);
        var atLaunchMass = OrbitalMechanics.approximateBurn(
            OrbitalMechanics.ManeuverKind.LANDING,
            earth,
            vehicle,
            vehicle.wetMassKg(),
            OrbitalMechanics.surfaceManeuverDeltaV(earth.properties(), MODEL, true),
            0,
            MODEL);
        assertEquals(OrbitalMechanics.LocalStatus.INSUFFICIENT_THRUST, atLaunchMass.status());
    }

    /** Integration contract: nested departure and arrival wells are paid without parent visits. */
    @Test
    void nestedRoutesRetainActualEndpointAndAccountForParentWells() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();
        var root = GalaxiaCelestialAPI.getPrimaryRoot();
        var vehicle = new OrbitalMechanics.LocalVehicle(1400, 30800, 10000, 4000000);
        var moon = CelestialRegistry.get(CelestialObjectId.MOON)
            .orElseThrow();
        var earth = CelestialRegistry.get(CelestialObjectId.OVERWORLD)
            .orElseThrow();
        var mars = CelestialRegistry.get(CelestialObjectId.MARS)
            .orElseThrow();
        var callisto = CelestialRegistry.get(CelestialObjectId.CALLISTO)
            .orElseThrow();
        var jupiter = CelestialRegistry.get(CelestialObjectId.JUPITER)
            .orElseThrow();
        var moonPlan = OrbitalTransferPlanner.computePoweredCandidates(
            root,
            moon,
            mars,
            0,
            OrbitalTransferPlanner.EndpointKind.PARKING,
            0,
            OrbitalTransferPlanner.EndpointKind.PARKING,
            1,
            vehicle,
            MODEL);
        var moonRoute = OrbitalTransferPlanner.selectPoweredCandidate(
            moonPlan,
            Double.POSITIVE_INFINITY,
            OrbitalTransferPlanner.RoutePriority.PRIORITIZE_DV);
        assertNotNull(moonRoute, "Moon-to-Mars has a bounded feasible cost-model witness");
        assertTrue(
            moonRoute.obligations()
                .stream()
                .anyMatch(
                    o -> o.kind() == OrbitalMechanics.ManeuverKind.WELL_PASSAGE && o.bodyKey()
                        .equals(earth.key())));
        assertTrue(
            moonRoute.obligations()
                .stream()
                .anyMatch(
                    o -> o.kind() == OrbitalMechanics.ManeuverKind.CAPTURE && o.bodyKey()
                        .equals(mars.key())));
        var callistoPlan = OrbitalTransferPlanner.computePoweredCandidates(
            root,
            earth,
            callisto,
            0,
            OrbitalTransferPlanner.EndpointKind.PARKING,
            0,
            OrbitalTransferPlanner.EndpointKind.PARKING,
            1,
            vehicle,
            MODEL);
        var callistoRoute = OrbitalTransferPlanner.selectPoweredCandidate(
            callistoPlan,
            Double.POSITIVE_INFINITY,
            OrbitalTransferPlanner.RoutePriority.PRIORITIZE_DV);
        assertNotNull(callistoRoute, "Earth-to-Callisto has a bounded feasible cost-model witness");
        assertTrue(
            callistoRoute.obligations()
                .stream()
                .anyMatch(
                    o -> o.kind() == OrbitalMechanics.ManeuverKind.WELL_PASSAGE && o.bodyKey()
                        .equals(jupiter.key())));
        assertTrue(
            callistoRoute.obligations()
                .stream()
                .anyMatch(
                    o -> o.kind() == OrbitalMechanics.ManeuverKind.CAPTURE && o.bodyKey()
                        .equals(callisto.key())));
        for (var route : java.util.List.of(moonRoute, callistoRoute)) {
            assertEquals(
                2,
                route.obligations()
                    .stream()
                    .filter(
                        o -> o.kind() == OrbitalMechanics.ManeuverKind.DEPARTURE
                            || o.kind() == OrbitalMechanics.ManeuverKind.CAPTURE)
                    .count());
            assertTrue(
                route.obligations()
                    .stream()
                    .filter(o -> o.kind() == OrbitalMechanics.ManeuverKind.WELL_PASSAGE)
                    .allMatch(o -> o.deltaVMps() == 0 && o.propellantKg() == 0 && o.physicalSeconds() == 0));
            assertEquals(route, OrbitalTransferPlanner.PoweredCandidate.deserializeNBT(route.serializeNBT()));
            System.out.println(
                "HIERARCHY_WITNESS endpoint=" + route.obligations()
                    .getLast()
                    .bodyKey()
                    + " dv="
                    + route.totalDvMps()
                    + " fuel="
                    + route.propellantKg()
                    + " distanceMeters="
                    + route.traveledDistanceMeters()
                    + " belts="
                    + route.beltDistanceMeters());
        }
        assertTrue(
            callistoRoute.beltDistanceMeters()
                .getOrDefault(
                    com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey
                        .registered(CelestialObjectId.FROZEN_BELT),
                    0.0)
                > 0);
    }

    /** Product contract: parent hyperbolic passages preserve energy and the selected infinity direction. */
    @Test
    void parentWellPassagesConserveEnergyForBothDirectionsAndBranches() {
        double mu = 126712764.1e9, x = 1.8e9, y = .4e9, vx = 5100, vy = -2300;
        for (boolean outgoing : new boolean[] { true, false }) for (int sign : new int[] { -1, 1 }) {
            var passage = OrbitalMechanics.hyperbolicVelocityAtPosition(mu, x, y, vx, vy, outgoing, sign);
            double r = Math.hypot(x, y), h = x * passage.vyMps() - y * passage.vxMps();
            double ex = passage.vyMps() * h / mu - x / r, ey = -passage.vxMps() * h / mu - y / r;
            assertEquals(
                vx * vx + vy * vy + 2 * mu / r,
                passage.vxMps() * passage.vxMps() + passage.vyMps() * passage.vyMps(),
                1e-5);
            assertEquals(outgoing ? -1 : 1, (ex * vx + ey * vy) / Math.hypot(vx, vy), 1e-10);
            assertTrue(passage.periapsisRadiusM() >= 0);
        }
    }

    /** Product contract: wear distance follows curved transfer travel and clips only the authored belt segment. */
    @Test
    void trajectoryDistanceFollowsArcAndClipsAuthoredBeltBoundaries() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();
        var root = GalaxiaCelestialAPI.getPrimaryRoot();
        var earth = CelestialRegistry.get(CelestialObjectId.OVERWORLD)
            .orElseThrow();
        double r = earth.properties()
            .parkingOrbitRadius(), v = Math.sqrt(earth.mu() / r), tof = Math.PI / 2 * Math.sqrt(r * r * r / earth.mu());
        var circle = new OrbitalTransferPlanner.TransferCandidate(
            new OrbitalTransferPlanner.TransferRoute(tof, 0, 0, 0, earth.key(), 0, 0, r, 0, 0, v, true),
            0,
            r,
            -v,
            0,
            0,
            v,
            -v,
            0);
        var distance = OrbitalTransferPlanner.measureTransferDistance(root, circle, 0);
        assertEquals(
            Math.PI / 2 * OrbitalMechanics.distanceToMetres(r),
            distance.totalMeters(),
            distance.totalMeters() * 1e-4);
        assertTrue(distance.totalMeters() > Math.sqrt(2) * OrbitalMechanics.distanceToMetres(r));
        var sol = CelestialRegistry.get(CelestialObjectId.SOL)
            .orElseThrow();
        double au = OrbitalMechanics.metresToDistance(149597870700.0);
        var outward = new OrbitalTransferPlanner.TransferCandidate(
            new OrbitalTransferPlanner.TransferRoute(500, 0, 0, 0, sol.key(), 0, 0, 2 * au, 0, 100, 0, true),
            4 * au,
            0,
            100,
            0,
            0,
            0,
            0,
            0);
        var crossing = OrbitalTransferPlanner.measureTransferDistance(root, outward, 0);
        var belt = com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey
            .registered(CelestialObjectId.FROZEN_BELT);
        var profile = CelestialRegistry.get(belt)
            .orElseThrow()
            .properties()
            .asteroidFieldProfile();
        assertEquals(
            OrbitalMechanics.distanceToMetres(profile.outerOrbitalRadius() - profile.innerOrbitalRadius()),
            crossing.beltDistanceMeters()
                .get(belt),
            1);
        assertTrue(
            crossing.beltDistanceMeters()
                .values()
                .stream()
                .mapToDouble(Double::doubleValue)
                .sum() < crossing.totalMeters());
    }

    /** Product contract: cumulative safety uses an analytic family bound and refuses unsupported hierarchies. */
    @Test
    void futureExposureBoundCoversCircularWitnessesAndKeepsLocalBoundsTight() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();
        var root = GalaxiaCelestialAPI.getPrimaryRoot();
        var earth = CelestialRegistry.get(CelestialObjectId.OVERWORLD)
            .orElseThrow();
        var moon = CelestialRegistry.get(CelestialObjectId.MOON)
            .orElseThrow();
        var mars = CelestialRegistry.get(CelestialObjectId.MARS)
            .orElseThrow();
        var local = OrbitalTransferPlanner.maximumTravelExposure(
            root,
            earth,
            earth,
            OrbitalTransferPlanner.EndpointKind.SURFACE,
            OrbitalTransferPlanner.EndpointKind.PARKING,
            10000,
            MODEL);
        assertNotNull(local);
        assertEquals(200000, local.distanceMeters(), 1e-6);
        assertEquals(0, local.beltDistanceMeters());
        assertNull(
            OrbitalTransferPlanner.maximumTravelExposure(
                root,
                moon,
                mars,
                OrbitalTransferPlanner.EndpointKind.PARKING,
                OrbitalTransferPlanner.EndpointKind.PARKING,
                10000,
                MODEL));
        var vehicle = new OrbitalMechanics.LocalVehicle(1400, 30800, 10000, 4000000);
        for (var target : java.util.List.of(moon, mars)) {
            var bound = OrbitalTransferPlanner.maximumTravelExposure(
                root,
                earth,
                target,
                OrbitalTransferPlanner.EndpointKind.PARKING,
                OrbitalTransferPlanner.EndpointKind.PARKING,
                10000,
                MODEL);
            assertNotNull(bound);
            for (double epoch : new double[] { 0, 100, 500 }) {
                var plan = OrbitalTransferPlanner.computePoweredCandidates(
                    root,
                    earth,
                    target,
                    epoch,
                    OrbitalTransferPlanner.EndpointKind.PARKING,
                    .4,
                    OrbitalTransferPlanner.EndpointKind.PARKING,
                    1,
                    vehicle,
                    MODEL);
                for (var candidate : plan.candidates()) if (candidate.status() == OrbitalMechanics.LocalStatus.FEASIBLE
                    && candidate.totalDvMps() <= 10000) {
                        assertTrue(candidate.traveledDistanceMeters() <= bound.distanceMeters());
                        assertTrue(
                            candidate.beltDistanceMeters()
                                .values()
                                .stream()
                                .mapToDouble(Double::doubleValue)
                                .sum() <= bound.beltDistanceMeters());
                    }
            }
            System.out.println("FUTURE_EXPOSURE " + target.key() + " " + bound);
        }
    }

    private static void printDistance(String label, OrbitalTransferPlanner.PoweredPlan plan,
        OrbitalTransferPlanner.PoweredCandidate selected) {
        double max = plan.candidates()
            .stream()
            .filter(c -> c.status() == OrbitalMechanics.LocalStatus.FEASIBLE)
            .mapToDouble(OrbitalTransferPlanner.PoweredCandidate::traveledDistanceMeters)
            .max()
            .orElseThrow();
        assertTrue(selected.traveledDistanceMeters() > 0);
        System.out.println(
            "PATH_DISTANCE " + label
                + " chosenMeters="
                + selected.traveledDistanceMeters()
                + " currentScanMaxFeasibleMeters="
                + max
                + " belts="
                + selected.beltDistanceMeters());
    }

    /** Persistence contract: accepted obligations use strict typed fields and conserve resource totals. */
    @Test
    void codecRejectsMissingOrAlteredAcceptedObligations() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();
        var earth = CelestialRegistry.get(CelestialObjectId.OVERWORLD)
            .orElseThrow();
        var candidate = OrbitalTransferPlanner
            .computePoweredCandidates(
                GalaxiaCelestialAPI.getPrimaryRoot(),
                earth,
                earth,
                0,
                OrbitalTransferPlanner.EndpointKind.SURFACE,
                0,
                OrbitalTransferPlanner.EndpointKind.PARKING,
                1,
                new OrbitalMechanics.LocalVehicle(1400, 30800, 3200, 800000),
                MODEL)
            .candidates()
            .getFirst();
        var encoded = candidate.serializeNBT();
        encoded.setDouble("propellant", 0);
        assertThrows(
            IllegalArgumentException.class,
            () -> OrbitalTransferPlanner.PoweredCandidate.deserializeNBT(encoded));
        var missingDistance = candidate.serializeNBT();
        missingDistance.removeTag("beltDistances");
        assertThrows(
            IllegalStateException.class,
            () -> OrbitalTransferPlanner.PoweredCandidate.deserializeNBT(missingDistance));
        var malformed = candidate.serializeNBT();
        malformed.removeTag("model");
        assertThrows(
            IllegalStateException.class,
            () -> OrbitalTransferPlanner.PoweredCandidate.deserializeNBT(malformed));
    }
}
