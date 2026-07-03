package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.DiscoveryState;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalMechanics;

final class AsteroidFieldScanScopeTest {

    @Test
    void withinRadiusMatchesAsteroidsByDerivedWorldPosition() {
        AsteroidFieldProfile profile = profile();
        OrbitalMechanics.OrbitalState beltState = new OrbitalMechanics.OrbitalState(100.0, 0.0, 0.0, 2.0);
        OrbitalMechanics.OrbitalState scanCenter = new OrbitalMechanics.OrbitalState(15.0, 0.0, 0.0, 0.0);
        Predicate<AsteroidFieldNode> scope = AsteroidFieldScanScope.withinRadius(profile, beltState, scanCenter, 1.0);

        assertTrue(scope.test(node(0, 0.0, 0.5)));
        assertFalse(scope.test(node(1, 180.0, 0.5)));
    }

    @Test
    void withinRadiusRejectsNegativeOrNonFiniteRadius() {
        AsteroidFieldProfile profile = profile();
        OrbitalMechanics.OrbitalState state = new OrbitalMechanics.OrbitalState(1.0, 0.0, 0.0, 0.0);

        assertThrows(
            IllegalArgumentException.class,
            () -> AsteroidFieldScanScope.withinRadius(profile, state, state, -1.0));
        assertThrows(
            IllegalArgumentException.class,
            () -> AsteroidFieldScanScope.withinRadius(profile, state, state, Double.POSITIVE_INFINITY));
    }

    private static AsteroidFieldProfile profile() {
        return AsteroidFieldProfile.builder()
            .sizeCounts(1, 0, 0)
            .radialBand(10.0, 20.0)
            .satelliteScanRadius(1000.0)
            .oreProfile(new AsteroidOreProfile("test", 1.0, List.of("test_vein")))
            .build();
    }

    private static AsteroidFieldNode node(int index, double angleOffsetDeg, double orbitalDepth01) {
        return new AsteroidFieldNode(
            new MinorCelestialBodyId(CelestialObjectId.FROZEN_BELT, index),
            CelestialObjectId.FROZEN_BELT,
            index,
            "Test Asteroid " + index,
            AsteroidNodeKind.GENERATED,
            AsteroidSizeClass.LARGE,
            DiscoveryState.DISCOVERED,
            angleOffsetDeg,
            orbitalDepth01,
            new AsteroidOreProfile("test", 1.0, List.of("test_vein")),
            new AsteroidAppearanceProfile("test_icon", index));
    }
}
