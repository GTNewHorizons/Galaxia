package com.gtnewhorizons.galaxia.registry.celestial;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class CelestialNumericalDefinitionsTest {

    /** Integration contract: offline forecasting and the game consume identical authored numerical definitions. */
    @Test
    void numericalDefinitionsMatchEveryGameBodyWithoutGameplayContent() {
        var definitions = CelestialRegistry.numericalDefinitions(200000);
        assertTrue(definitions.size() > 50);
        assertThrows(UnsupportedOperationException.class, () -> definitions.clear());
        GalaxiaTestBootstrap.ensureCelestialRegistry();
        assertEquals(
            CelestialRegistry.getAllBodies()
                .keySet(),
            definitions.keySet());
        for (var body : definitions.values()) {
            var game = CelestialRegistry.get(body.key())
                .orElseThrow();
            assertEquals(game.parentKey(), body.parentKey());
            assertEquals(game.objectClass(), body.objectClass());
            assertEquals(game.orbitalParams(), body.orbitalParams());
            assertEquals(game.absolutePosition(), body.absolutePosition());
            assertEquals(game.mu(), body.mu());
            assertEquals(
                game.properties()
                    .radiusEarthRelative(),
                body.properties()
                    .radiusEarthRelative());
            assertEquals(
                game.properties()
                    .parkingOrbitRadius(),
                body.properties()
                    .parkingOrbitRadius());
            assertEquals(
                game.properties()
                    .sphereOfInfluenceRadius(),
                body.properties()
                    .sphereOfInfluenceRadius());
            assertEquals(
                game.properties()
                    .asteroidFieldProfile(),
                body.properties()
                    .asteroidFieldProfile());
            assertNull(body.texture());
            assertNull(body.playableDimensionProfile());
            assertTrue(
                body.properties()
                    .atmosphereIngredients()
                    .isEmpty());
            assertEquals(
                body.key()
                    .registeredBodyId()
                    .getId(),
                body.nameKey());
        }
    }
}
