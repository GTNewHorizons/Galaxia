package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalMechanics;

final class AsteroidFieldOrbitModelTest {

    private static final double EPSILON = 1.0e-9;

    @Test
    void asteroidPositionRotatesWithBeltPhaseAndUsesDepthWithinRadialBand() {
        AsteroidFieldProfile profile = profile(10.0, 20.0);
        AsteroidFieldNode node = node(90.0, 0.5);
        OrbitalMechanics.OrbitalState beltState = new OrbitalMechanics.OrbitalState(0.0, 100.0, -5.0, 0.0);

        OrbitalMechanics.OrbitalState asteroidState = AsteroidFieldOrbitModel
            .resolveWorldState(profile, node, beltState);

        assertAll(
            () -> assertEquals(-15.0, asteroidState.x(), EPSILON),
            () -> assertEquals(0.0, asteroidState.y(), EPSILON),
            () -> assertEquals(0.0, asteroidState.vx(), EPSILON),
            () -> assertEquals(-0.75, asteroidState.vy(), EPSILON));
    }

    @Test
    void asteroidDepthZeroAndOneMapToConfiguredRadialBandEdges() {
        AsteroidFieldProfile profile = profile(4.0, 9.0);
        OrbitalMechanics.OrbitalState beltState = new OrbitalMechanics.OrbitalState(100.0, 0.0, 0.0, 2.0);

        OrbitalMechanics.OrbitalState innerState = AsteroidFieldOrbitModel
            .resolveWorldState(profile, node(0.0, 0.0), beltState);
        OrbitalMechanics.OrbitalState outerState = AsteroidFieldOrbitModel
            .resolveWorldState(profile, node(0.0, 1.0), beltState);

        assertAll(
            () -> assertEquals(4.0, innerState.x(), EPSILON),
            () -> assertEquals(0.0, innerState.y(), EPSILON),
            () -> assertEquals(9.0, outerState.x(), EPSILON),
            () -> assertEquals(0.0, outerState.y(), EPSILON));
    }

    private static AsteroidFieldProfile profile(double innerRadius, double outerRadius) {
        return AsteroidFieldProfile.builder()
            .sizeCounts(1, 0, 0)
            .radialBand(innerRadius, outerRadius)
            .oreProfile(new AsteroidOreProfile("test", 1.0, List.of("test_vein")))
            .build();
    }

    private static AsteroidFieldNode node(double angleOffsetDeg, double orbitalDepth01) {
        return new AsteroidFieldNode(
            new MinorCelestialBodyId(CelestialObjectId.FROZEN_BELT, 0),
            CelestialObjectId.FROZEN_BELT,
            0,
            "Test Asteroid",
            AsteroidNodeKind.GENERATED,
            AsteroidSizeClass.LARGE,
            angleOffsetDeg,
            orbitalDepth01,
            new AsteroidOreProfile("test", 1.0, List.of("test_vein")),
            new AsteroidAppearanceProfile("test_icon", 0L));
    }
}
