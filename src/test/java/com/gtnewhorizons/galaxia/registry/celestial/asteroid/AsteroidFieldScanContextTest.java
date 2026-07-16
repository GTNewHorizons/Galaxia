package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.DiscoveryState;

final class AsteroidFieldScanContextTest {

    @Test
    void buildsAsteroidPredicateFromGenericAnchorScope() {
        AsteroidFieldProfile profile = profile();
        AsteroidFieldNode anchor = AsteroidFieldResolver.resolveNode(CelestialObjectId.FROZEN_BELT, profile, 0);
        AsteroidFieldNode nearby = AsteroidFieldResolver.resolveNode(CelestialObjectId.FROZEN_BELT, profile, 1);
        AsteroidFieldNode far = AsteroidFieldResolver.resolveNode(CelestialObjectId.FROZEN_BELT, profile, 2);

        AsteroidFieldScanContext context = AsteroidFieldScanContext
            .from(CelestialObjectId.FROZEN_BELT, profile, CelestialObjectKey.minorBody(anchor.id()), 1.0);

        assertTrue(
            context.scope()
                .test(anchor));
        assertTrue(
            context.scope()
                .test(nearby));
        assertFalse(
            context.scope()
                .test(far));
    }

    @Test
    void rejectsNonAsteroidAnchorForAsteroidContext() {
        assertThrows(
            IllegalArgumentException.class,
            () -> AsteroidFieldScanContext.from(
                CelestialObjectId.FROZEN_BELT,
                profile(),
                CelestialObjectKey.registered(CelestialObjectId.MARS),
                1.0));
    }

    @Test
    void rejectsNegativeOrNonFiniteRadius() {
        assertThrows(
            IllegalArgumentException.class,
            () -> AsteroidFieldScanContext.from(
                CelestialObjectId.FROZEN_BELT,
                profile(),
                CelestialObjectKey.minorBody(new MinorCelestialBodyId(CelestialObjectId.FROZEN_BELT, 0)),
                -1.0));
        assertThrows(
            IllegalArgumentException.class,
            () -> AsteroidFieldScanContext.from(
                CelestialObjectId.FROZEN_BELT,
                profile(),
                CelestialObjectKey.minorBody(new MinorCelestialBodyId(CelestialObjectId.FROZEN_BELT, 0)),
                Double.POSITIVE_INFINITY));
    }

    private static AsteroidFieldProfile profile() {
        return AsteroidFieldProfile.builder()
            .sizeCounts(1, 0, 0)
            .radialBand(10.0, 20.0)
            .placementConnectionRadius(1000.0)
            .oreProfile(new AsteroidOreProfile("test", List.of("test_vein")))
            .authoredAsteroid(node(0, 0.0, 0.5))
            .authoredAsteroid(node(1, 1.0, 0.5))
            .authoredAsteroid(node(2, 180.0, 0.5))
            .build();
    }

    private static AuthoredAsteroidDefinition node(int index, double angleOffsetDeg, double orbitalDepth01) {
        return new AuthoredAsteroidDefinition(
            index,
            AsteroidNodeKind.LORE,
            null,
            "Test Asteroid " + index,
            AsteroidSizeClass.LARGE,
            DiscoveryState.DISCOVERED,
            null,
            angleOffsetDeg,
            orbitalDepth01,
            "test",
            new AsteroidAppearanceProfile("test_icon", index));
    }
}
