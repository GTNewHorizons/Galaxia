package com.gtnewhorizons.galaxia.registry.orbital;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;

final class OrbitalForecastTest {

    /** Product contract: local docking guidance applies both paid obligations before reporting availability. */
    @Test
    void dockingAvailabilityUsesTheSameAllowanceAndActualFuelAsDeparture() {
        var definitions = CelestialRegistry.numericalDefinitions(200000);
        var root = definitions.get(CelestialObjectKey.registered(CelestialObjectId.NOVUM_CAELUM));
        var earth = definitions.get(CelestialObjectKey.registered(CelestialObjectId.OVERWORLD));
        var model = new OrbitalMechanics.CostModel(1000, 100, .35, .05, 20, 20, 10);
        var data = new OrbitalTransferPlanner.ForecastData("a".repeat(64), "b".repeat(64), java.util.List.of());
        var vehicle = new OrbitalMechanics.LocalVehicle(1400, 30800, 5200, 800000);
        for (boolean supplied : new boolean[] { true, false }) for (double allowance : new double[] { 39, 40 }) {
            var result = OrbitalTransferPlanner.estimateAvailability(
                data,
                root,
                earth,
                earth,
                0,
                OrbitalTransferPlanner.EndpointKind.DOCK,
                OrbitalTransferPlanner.EndpointKind.DOCK,
                supplied ? vehicle : new OrbitalMechanics.LocalVehicle(1400, 0, 5200, 800000),
                model,
                allowance);
            assertEquals(OrbitalTransferPlanner.ForecastStatus.AVAILABLE, result.status());
            assertEquals(
                supplied && allowance >= 40 ? 1 : 0,
                result.availability()
                    .availableFraction());
        }
    }

    /**
     * Integration contract: directional prebaked output keeps individual branch obligations and strict data identity.
     */
    @Test
    void directionalProfilesRetainAlternativesAndRoundTripWithoutRuntimeGeneration() throws Exception {
        var definitions = CelestialRegistry.numericalDefinitions(200000);
        Map<CelestialObjectKey, CelestialObject> selected = new TreeMap<>();
        for (var id : new CelestialObjectId[] { CelestialObjectId.NOVUM_CAELUM, CelestialObjectId.SOL,
            CelestialObjectId.OVERWORLD, CelestialObjectId.MARS }) {
            var key = CelestialObjectKey.registered(id);
            selected.put(key, definitions.get(key));
        }
        var data = OrbitalTransferPlanner.generateForecasts(selected, 16, "a".repeat(64));
        assertEquals(
            2,
            data.profiles()
                .size());
        assertNotEquals(
            data.profiles()
                .getFirst()
                .source(),
            data.profiles()
                .getLast()
                .source());
        assertTrue(
            data.profiles()
                .stream()
                .allMatch(
                    p -> p.phases()
                        .size() == 16));
        assertTrue(
            data.profiles()
                .stream()
                .flatMap(
                    p -> p.phases()
                        .stream())
                .anyMatch(rows -> rows.size() > 64));
        var output = new java.io.ByteArrayOutputStream();
        OrbitalTransferPlanner.writeForecastData(data, output);
        assertEquals(
            data,
            OrbitalTransferPlanner.readForecastData(new java.io.ByteArrayInputStream(output.toByteArray())));
        var bytes = output.toByteArray();
        bytes[0] = 0;
        assertThrows(
            java.io.IOException.class,
            () -> OrbitalTransferPlanner.readForecastData(new java.io.ByteArrayInputStream(bytes)));
        System.out.println(
            "FORECAST_PAIR_BAKE profiles=" + data.profiles()
                .size() + " bytes=" + output.size());
    }

    /** Product contract: availability counts qualifying time and circular gaps cross the cycle boundary. */
    @Test
    void circularAvailabilityUsesTimeSignedSpeedsAndActualFixedPhase() {
        var wrap = OrbitalTransferPlanner
            .summarizeCircularAvailability(new boolean[] { false, false, true, true, false, false }, 1, 0);
        assertEquals(1.0 / 3, wrap.availableFraction(), 1e-12);
        assertEquals(
            4 * (2 * Math.PI / 6) / 20,
            wrap.maximumWaitSeconds()
                .orElseThrow(),
            1e-12);
        var clockwise = OrbitalTransferPlanner
            .summarizeCircularAvailability(new boolean[] { false, true, false, false }, -1, Math.PI / 4);
        var counterclockwise = OrbitalTransferPlanner
            .summarizeCircularAvailability(new boolean[] { false, true, false, false }, 1, Math.PI / 4);
        assertEquals(
            5 * counterclockwise.nextWindowSeconds()
                .orElseThrow(),
            clockwise.nextWindowSeconds()
                .orElseThrow(),
            1e-12);
        assertEquals(
            0,
            OrbitalTransferPlanner.summarizeCircularAvailability(new boolean[] { false, true }, 0, 0)
                .availableFraction());
        assertEquals(
            1,
            OrbitalTransferPlanner.summarizeCircularAvailability(new boolean[] { false, true }, 0, Math.PI)
                .availableFraction());
        var none = OrbitalTransferPlanner.summarizeCircularAvailability(new boolean[] { false, false }, 1, 0);
        assertTrue(
            none.nextWindowSeconds()
                .isEmpty());
        assertTrue(
            none.maximumWaitSeconds()
                .isEmpty());
    }

    /**
     * Product contract: missing branch neighbors remain unavailable and radius interpolation precedes affordability.
     */
    @Test
    void interpolationKeepsBranchIdentityAndDoesNotTurnInvalidNeighborsIntoFreeRows() {
        var a = CelestialObjectKey.registered(CelestialObjectId.OVERWORLD);
        var b = CelestialObjectKey.registered(CelestialObjectId.MARS);
        var star = CelestialObjectKey.registered(CelestialObjectId.SOL);
        var row = new OrbitalTransferPlanner.ForecastCandidate(0, true, 10, 20, 30);
        var empty = java.util.List.<OrbitalTransferPlanner.ForecastCandidate>of();
        var profile = new OrbitalTransferPlanner.CircularProfile(
            a,
            b,
            star,
            1,
            2,
            1,
            2,
            false,
            java.util.List.of(empty, java.util.List.of(row)));
        assertTrue(
            OrbitalTransferPlanner.interpolateForecastCandidates(java.util.List.of(profile), 1, 2, Math.PI / 2)
                .isEmpty());
        assertEquals(
            java.util.List.of(row),
            OrbitalTransferPlanner.interpolateForecastCandidates(java.util.List.of(profile), 1, 2, Math.PI));
        assertNull(OrbitalTransferPlanner.interpolateForecastCandidates(java.util.List.of(profile), 1, 3, Math.PI));
    }

    /** Bug regression: same-sign Lambert samples on opposite sides of zero arrival angle are unrelated paths. */
    @Test
    void arrivalWindingBoundaryCannotCreateAnInterpolatedCandidate() {
        var a = CelestialObjectKey.registered(CelestialObjectId.OVERWORLD);
        var b = CelestialObjectKey.registered(CelestialObjectId.MARS);
        var star = CelestialObjectKey.registered(CelestialObjectId.SOL);
        var row = new OrbitalTransferPlanner.ForecastCandidate(0, true, 1, 20, 30);
        var profile = new OrbitalTransferPlanner.CircularProfile(
            a,
            b,
            star,
            1,
            2,
            0,
            -Math.PI / 2,
            false,
            java.util.List.of(java.util.List.of(row), java.util.List.of(row)));
        assertTrue(
            OrbitalTransferPlanner.interpolateForecastCandidates(java.util.List.of(profile), 1, 2, Math.PI / 2)
                .isEmpty());
        assertEquals(
            1,
            OrbitalTransferPlanner.interpolateForecastCandidates(java.util.List.of(profile), 1, 2, 0)
                .size());
    }

    /**
     * Integration contract: guidance refreshes for actual fuel and allowance, with dense-reference interpolation
     * measurements.
     */
    @Test
    void guidanceUsesActualVehicleAndMeasuresAgainstDenserGeometry() {
        var definitions = CelestialRegistry.numericalDefinitions(200000);
        Map<CelestialObjectKey, CelestialObject> selected = new TreeMap<>();
        for (var id : new CelestialObjectId[] { CelestialObjectId.NOVUM_CAELUM, CelestialObjectId.SOL,
            CelestialObjectId.OVERWORLD, CelestialObjectId.MARS }) {
            var key = CelestialObjectKey.registered(id);
            selected.put(key, definitions.get(key));
        }
        var data = OrbitalTransferPlanner.generateForecasts(selected, 32, "b".repeat(64));
        com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap.ensureCelestialRegistry();
        var root = com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI.getPrimaryRoot();
        var earth = CelestialRegistry.get(CelestialObjectId.OVERWORLD)
            .orElseThrow();
        var mars = CelestialRegistry.get(CelestialObjectId.MARS)
            .orElseThrow();
        var star = CelestialRegistry.get(CelestialObjectId.SOL)
            .orElseThrow();
        var model = new OrbitalMechanics.CostModel(1000, 100, .35, .05, 20, 20, 10);
        var vehicle = new OrbitalMechanics.LocalVehicle(1400, 30800, 3200, 4000000);
        var low = OrbitalTransferPlanner.estimateAvailability(
            data,
            root,
            earth,
            mars,
            0,
            OrbitalTransferPlanner.EndpointKind.PARKING,
            OrbitalTransferPlanner.EndpointKind.PARKING,
            vehicle,
            model,
            6000);
        var high = OrbitalTransferPlanner.estimateAvailability(
            data,
            root,
            earth,
            mars,
            0,
            OrbitalTransferPlanner.EndpointKind.PARKING,
            OrbitalTransferPlanner.EndpointKind.PARKING,
            vehicle,
            model,
            10000);
        assertEquals(OrbitalTransferPlanner.ForecastStatus.AVAILABLE, high.status());
        assertTrue(
            low.availability()
                .availableFraction()
                <= high.availability()
                    .availableFraction());
        var empty = OrbitalTransferPlanner.estimateAvailability(
            data,
            root,
            earth,
            mars,
            0,
            OrbitalTransferPlanner.EndpointKind.PARKING,
            OrbitalTransferPlanner.EndpointKind.PARKING,
            new OrbitalMechanics.LocalVehicle(1400, 0, 3200, 4000000),
            model,
            10000);
        assertEquals(
            0,
            empty.availability()
                .availableFraction());
        for (int density : new int[] { 32, 64, 128 }) {
            var measured = OrbitalTransferPlanner.generateForecasts(selected, density, "b".repeat(64));
            var profiles = measured.profiles()
                .stream()
                .filter(
                    p -> p.source()
                        .equals(earth.key()))
                .toList();
            var profile = profiles.getFirst();
            double a = (profile.sourceRadius() + profile.destinationRadius()) / 2;
            double hohmann = Math.PI * Math.sqrt(a * a * a / star.mu());
            java.util.List<Double> errors = new java.util.ArrayList<>();
            int unavailable = 0;
            for (int phase = 0; phase < 512; phase++) {
                double angle = 2 * Math.PI * phase / 512;
                var interpolated = OrbitalTransferPlanner.interpolateForecastCandidates(
                    profiles,
                    profile.sourceRadius(),
                    profile.destinationRadius(),
                    angle);
                var exact = OrbitalTransferPlanner.computeCircularCandidates(
                    star,
                    profile.sourceRadius(),
                    profile.sourceOmega(),
                    profile.destinationRadius(),
                    profile.destinationOmega(),
                    angle);
                for (var candidate : exact) {
                    int index = (int) Math.round(
                        (candidate.route()
                            .tofOsu() / hohmann - .1) * 63 / 2.9);
                    var found = interpolated.stream()
                        .filter(
                            row -> row.timeIndex() == index && row.prograde() == candidate.route()
                                .prograde())
                        .findFirst();
                    if (found.isEmpty()) {
                        unavailable++;
                        continue;
                    }
                    var row = found.orElseThrow();
                    double dep = candidate.route()
                        .departureDv(),
                        cap = candidate.route()
                            .captureDv();
                    errors.add(
                        OrbitalMechanics.velocityToMetresPerSecond(
                            Math.abs(row.departureRelativeSpeed() - dep) + Math.abs(row.arrivalRelativeSpeed() - cap)));
                }
            }
            errors.sort(Double::compare);
            assertFalse(errors.isEmpty());
            System.out.println(
                "FORECAST_DENSE_REFERENCE coarsePhases=" + density
                    + " referencePhases=512 compared="
                    + errors.size()
                    + " unavailable="
                    + unavailable
                    + " p95RawDvErrorMps="
                    + errors.get((int) (errors.size() * .95))
                    + " maxRawDvErrorMps="
                    + errors.getLast()
                    + " availability6000="
                    + low.availability()
                        .availableFraction()
                    + " availability10000="
                    + high.availability()
                        .availableFraction());
        }
    }
}
