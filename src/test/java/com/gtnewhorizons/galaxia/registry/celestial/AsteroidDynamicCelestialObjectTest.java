package com.gtnewhorizons.galaxia.registry.celestial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.asteroid.MinorCelestialBodyId;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class AsteroidDynamicCelestialObjectTest {

    @BeforeAll
    static void init() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();
    }

    @Test
    void frozenBeltMinorBodyKeyResolvesGeneratedAsteroidObject() {
        CelestialObjectKey key = CelestialObjectKey
            .minorBody(new MinorCelestialBodyId(CelestialObjectId.FROZEN_BELT, 0));

        CelestialObject asteroid = CelestialRegistry.get(key)
            .orElseThrow();

        assertEquals(key, asteroid.id());
        assertEquals(CelestialObjectKey.registered(CelestialObjectId.FROZEN_BELT), asteroid.parentId());
        assertEquals(CelestialObject.Class.ASTEROID, asteroid.objectClass());
        assertEquals("FROZEN_BELT 1", asteroid.name());
        assertTrue(
            asteroid.properties()
                .canCreateOutpost());
        assertFalse(
            asteroid.properties()
                .canCreateStation());
    }

    @Test
    void generatedAsteroidObjectsDoNotPolluteStaticRegistryListing() {
        CelestialObjectKey key = CelestialObjectKey
            .minorBody(new MinorCelestialBodyId(CelestialObjectId.FROZEN_BELT, 1));

        assertTrue(
            CelestialRegistry.get(key)
                .isPresent());
        assertFalse(
            CelestialRegistry.getAllBodies()
                .containsKey(key));
    }
}
