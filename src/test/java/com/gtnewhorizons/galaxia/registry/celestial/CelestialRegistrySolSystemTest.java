package com.gtnewhorizons.galaxia.registry.celestial;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class CelestialRegistrySolSystemTest {

    @BeforeAll
    static void init() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();
    }

    /** Bug regression: a galaxy overview must not stack two stars at the same authored position. */
    @Test
    void galaxyStarsHaveDistinctPositions() {
        var positions = new HashSet<>();
        for (var star : CelestialRegistry.getChildren(CelestialObjectKey.registered(CelestialObjectId.NOVUM_CAELUM))) {
            assertNotNull(star.absolutePosition());
            assertTrue(positions.add(star.absolutePosition()), () -> "Overlapping star: " + star.key());
        }
    }

    /** Bug regression: moons stay near their planet rather than crossing neighboring planetary orbits. */
    @Test
    void solMoonOrbitsStayInsideTheGapToNeighboringPlanets() {
        var planets = CelestialRegistry.getChildren(CelestialObjectKey.registered(CelestialObjectId.SOL));
        for (var planet : planets) {
            double nearestGap = Double.POSITIVE_INFINITY;
            for (var neighbor : planets) {
                if (neighbor == planet || neighbor.objectClass() == CelestialObject.Class.ASTEROID_BELT) continue;
                nearestGap = Math.min(
                    nearestGap,
                    Math.abs(
                        planet.orbitalParams()
                            .semiMajorAxis()
                            - neighbor.orbitalParams()
                                .semiMajorAxis()));
            }
            for (var moon : CelestialRegistry.getChildren(planet.key())) {
                if (moon.objectClass() != CelestialObject.Class.MOON) continue;
                double farthest = moon.orbitalParams()
                    .apogee();
                assertTrue(farthest < nearestGap, () -> "Moon reaches another planet's orbit: " + moon.key());
            }
        }
    }
}
