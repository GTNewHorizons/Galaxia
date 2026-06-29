package com.gtnewhorizons.galaxia.registry.orbital;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldNode;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldOrbitModel;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldProfile;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldResolver;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidOreProfile;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.MinorCelestialBodyId;

final class OrbitalMechanicsTest {

    private static final double EPSILON = 1.0e-9;

    @Test
    void asteroidFieldChildUsesBeltPhaseInsteadOfOrbitingAroundBeltPoint() {
        AsteroidFieldProfile profile = AsteroidFieldProfile.builder()
            .sizeCounts(1, 0, 0)
            .radialBand(10.0, 20.0)
            .satelliteScanRadius(1000.0)
            .oreProfile(new AsteroidOreProfile("test", 1.0, List.of("test_vein")))
            .build();
        MinorCelestialBodyId minorId = new MinorCelestialBodyId(CelestialObjectId.FROZEN_BELT, 0);
        AsteroidFieldNode node = AsteroidFieldResolver.resolveNode(CelestialObjectId.FROZEN_BELT, profile, 0);
        CelestialObject belt = CelestialObject.builder()
            .id(CelestialObjectId.FROZEN_BELT)
            .name("Frozen Belt")
            .objectClass(CelestialObject.Class.ASTEROID_BELT)
            .properties(properties -> properties.asteroidFieldProfile(profile))
            .build();
        CelestialObject asteroid = CelestialObject.builder()
            .id(CelestialObjectKey.minorBody(minorId))
            .name(node.displayName())
            .parent(CelestialObjectId.FROZEN_BELT)
            .objectClass(CelestialObject.Class.ASTEROID)
            .circularOrbit(999.0, 1.0, 0.0)
            .build();
        OrbitalMechanics.OrbitalState beltState = new OrbitalMechanics.OrbitalState(0.0, 100.0, -5.0, 0.0);

        OrbitalMechanics.OrbitalState expected = AsteroidFieldOrbitModel.resolveWorldState(profile, node, beltState);
        OrbitalMechanics.OrbitalState actual = OrbitalMechanics.resolveChildWorldState(belt, asteroid, beltState, 0.0);

        assertAll(
            () -> assertEquals(expected.x(), actual.x(), EPSILON),
            () -> assertEquals(expected.y(), actual.y(), EPSILON),
            () -> assertEquals(expected.vx(), actual.vx(), EPSILON),
            () -> assertEquals(expected.vy(), actual.vy(), EPSILON));
        assertTrue(OrbitalMechanics.usesAsteroidFieldPosition(belt, asteroid));
    }

    @Test
    void standaloneMinorAsteroidUsesNormalChildOrbitWhenParentHasNoAsteroidField() {
        MinorCelestialBodyId minorId = new MinorCelestialBodyId(CelestialObjectId.MARS, 0);
        CelestialObject parent = CelestialObject.builder()
            .id(CelestialObjectId.MARS)
            .name("Mars")
            .objectClass(CelestialObject.Class.PLANET)
            .build();
        CelestialObject asteroid = CelestialObject.builder()
            .id(CelestialObjectKey.minorBody(minorId))
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
        assertFalse(OrbitalMechanics.usesAsteroidFieldPosition(parent, asteroid));
    }
}
