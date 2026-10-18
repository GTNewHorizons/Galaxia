package com.gtnewhorizons.galaxia.registry.orbital;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialServerRuntime;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldNode;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldOrbitResolver;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldProfile;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldResolver;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidOreProfile;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidSlotRanges;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.MinorCelestialBodyId;

final class OrbitalMechanicsTest {

    private static final double EPSILON = 1.0e-9;

    /** Integration contract: SI physics preserves the existing orbital length and Sun gravity scales. */
    @Test
    void physicalConversionsPreserveOrbitalDynamicsWithoutUsingTheGameTimer() {
        assertEquals(6_371_000, OrbitalMechanics.distanceToMetres(1), 1e-9);
        assertEquals(11_844.639269880003, OrbitalMechanics.osuToPhysicalSeconds(1), 1e-8);
        assertEquals(537.8804583944534, OrbitalMechanics.velocityToMetresPerSecond(1), 1e-9);
        assertEquals(132_712_440_041.279419e9, OrbitalMechanics.muToSI(7.2e7), 1e6);
        assertEquals(1, OrbitalMechanics.metresToDistance(6_371_000), 1e-12);
        assertEquals(1, OrbitalMechanics.physicalSecondsToOsu(11_844.639269880003), 1e-12);
        assertEquals(1, OrbitalMechanics.metresPerSecondToVelocity(537.8804583944534), 1e-12);
        assertEquals(7.2e7, OrbitalMechanics.siToMu(132_712_440_041.279419e9), 1e-7);
    }

    /** Product contract: visible surface maneuvers have a minimum game time without inventing extra fuel burns. */
    @Test
    void surfaceManeuversKeepBurnPhysicsButReserveVisibleGameTime() {
        var body = CelestialRegistry.numericalDefinitions(200000)
            .get(CelestialObjectKey.registered(CelestialObjectId.OVERWORLD));
        var vehicle = new OrbitalMechanics.LocalVehicle(100, 3000, 3000, 1000000);
        var numerical = new OrbitalMechanics.CostModel(1000, 100, .35, .05, 20, 20, 0);
        var visible = new OrbitalMechanics.CostModel(1000, 100, .35, .05, 20, 20, 4);
        for (var kind : List.of(
            OrbitalMechanics.ManeuverKind.ASCENT,
            OrbitalMechanics.ManeuverKind.LANDING,
            OrbitalMechanics.ManeuverKind.DOCKING)) {
            var raw = OrbitalMechanics.approximateBurn(kind, body, vehicle, vehicle.wetMassKg(), 100, 0, numerical);
            var shown = OrbitalMechanics.approximateBurn(kind, body, vehicle, vehicle.wetMassKg(), 100, 0, visible);
            double gameSeconds = (shown.endEpochOsu() - shown.startEpochOsu()) / OrbitalTransferPlanner.OSU_PER_SECOND;
            assertEquals(raw.deltaVMps(), shown.deltaVMps());
            assertEquals(raw.propellantKg(), shown.propellantKg());
            assertEquals(raw.physicalSeconds(), shown.physicalSeconds());
            assertEquals(raw.burnPhysicalSeconds(), shown.burnPhysicalSeconds());
            assertEquals(shown, OrbitalMechanics.ManeuverObligation.deserializeNBT(shown.serializeNBT()));
            if (kind == OrbitalMechanics.ManeuverKind.DOCKING) assertEquals(raw, shown);
            else assertTrue(
                gameSeconds >= visible.minimumSurfaceGameSeconds() - EPSILON,
                kind + " game duration " + gameSeconds + " is shorter than its configured visible minimum");
        }
    }

    @BeforeEach
    void registerOrbitalAdapters() {
        OrbitalMechanics.resetMinorBodyResolversForTesting();
        CelestialServerRuntime.create();
    }

    @Test
    void asteroidFieldChildUsesBeltPhaseInsteadOfOrbitingAroundBeltPoint() {
        AsteroidFieldProfile profile = AsteroidFieldProfile.builder()
            .sizeCounts(1, 0, 0)
            .radialBand(10.0, 20.0)
            .placementConnectionRadius(1000.0)
            .oreProfile(new AsteroidOreProfile("test", List.of("test_vein")))
            .build();
        MinorCelestialBodyId minorId = new MinorCelestialBodyId(
            CelestialObjectId.FROZEN_BELT,
            AsteroidSlotRanges.GENERATED_SLOT_MIN);
        AsteroidFieldNode node = AsteroidFieldResolver
            .placedNode(CelestialObjectId.FROZEN_BELT, profile, AsteroidSlotRanges.GENERATED_SLOT_MIN);
        CelestialObject belt = CelestialObject.builder()
            .key(CelestialObjectId.FROZEN_BELT)
            .name("Frozen Belt")
            .objectClass(CelestialObject.Class.ASTEROID_BELT)
            .properties(properties -> properties.asteroidFieldProfile(profile))
            .build();
        CelestialObject asteroid = CelestialObject.builder()
            .key(CelestialObjectKey.minorBody(minorId))
            .name(node.displayName())
            .parent(CelestialObjectId.FROZEN_BELT)
            .objectClass(CelestialObject.Class.ASTEROID)
            .circularOrbit(999.0, 1.0, 0.0)
            .build();
        OrbitalMechanics.OrbitalState beltState = new OrbitalMechanics.OrbitalState(0.0, 100.0, -5.0, 0.0);

        OrbitalMechanics.OrbitalState expected = AsteroidFieldOrbitResolver.resolveWorldState(profile, node, beltState);
        OrbitalMechanics.OrbitalState actual = OrbitalMechanics.resolveChildWorldState(belt, asteroid, beltState, 0.0);

        assertAll(
            () -> assertEquals(expected.x(), actual.x(), EPSILON),
            () -> assertEquals(expected.y(), actual.y(), EPSILON),
            () -> assertEquals(expected.vx(), actual.vx(), EPSILON),
            () -> assertEquals(expected.vy(), actual.vy(), EPSILON));
        assertTrue(OrbitalMechanics.usesMinorBodyResolvedPosition(belt, asteroid));
    }

    @Test
    void standaloneMinorAsteroidUsesNormalChildOrbitWhenParentHasNoAsteroidField() {
        MinorCelestialBodyId minorId = new MinorCelestialBodyId(CelestialObjectId.MARS, 0);
        CelestialObject parent = CelestialObject.builder()
            .key(CelestialObjectId.MARS)
            .name("Mars")
            .objectClass(CelestialObject.Class.PLANET)
            .build();
        CelestialObject asteroid = CelestialObject.builder()
            .key(CelestialObjectKey.minorBody(minorId))
            .name("Mars Trojan")
            .parent(CelestialObjectId.MARS)
            .objectClass(CelestialObject.Class.ASTEROID)
            .circularOrbit(12.0, 0.001, 0.0)
            .build();
        OrbitalMechanics.OrbitalState parentState = new OrbitalMechanics.OrbitalState(100.0, 0.0, 0.0, 2.0);

        OrbitalMechanics.OrbitalState expected = parentState.add(
            OrbitalMechanics.calculateOrbitalState(
                asteroid.orbitalParams(),
                OrbitalMechanics.resolveAttractorMu(parent, asteroid.orbitalParams()),
                0.0));
        OrbitalMechanics.OrbitalState actual = OrbitalMechanics
            .resolveChildWorldState(parent, asteroid, parentState, 0.0);

        assertAll(
            () -> assertEquals(expected.x(), actual.x(), EPSILON),
            () -> assertEquals(expected.y(), actual.y(), EPSILON),
            () -> assertEquals(expected.vx(), actual.vx(), EPSILON),
            () -> assertEquals(expected.vy(), actual.vy(), EPSILON));
        assertFalse(OrbitalMechanics.usesMinorBodyResolvedPosition(parent, asteroid));
    }
}
