package com.gtnewhorizons.galaxia.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidSlotRanges;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.MinorCelestialBodyId;
import com.gtnewhorizons.galaxia.registry.dimension.DimensionEnum;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class GalaxiaCelestialAPIKeyTest {

    @BeforeAll
    static void init() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();
    }

    @Test
    void publicApiFindsDynamicMinorBodyByKey() {
        CelestialObject root = GalaxiaCelestialAPI.getPrimaryRoot();
        CelestialObjectKey key = CelestialObjectKey
            .minorBody(new MinorCelestialBodyId(CelestialObjectId.FROZEN_BELT, AsteroidSlotRanges.GENERATED_SLOT_MIN));

        CelestialObject asteroid = GalaxiaCelestialAPI.get(key)
            .orElseThrow();

        assertEquals(asteroid, GalaxiaCelestialAPI.findBodyByKey(root, key));
        assertEquals(
            CelestialObjectId.VAEL,
            GalaxiaCelestialAPI.findStar(key)
                .requireRegisteredId());
        assertEquals(asteroid, GalaxiaCelestialAPI.findPlanetaryAnchor(key));
    }

    @Test
    void findByDimensionIdReturnsRegisteredBodyForKnownDimension() {
        CelestialObject mars = GalaxiaCelestialAPI.findByDimension(DimensionEnum.MARS.getId())
            .orElseThrow();

        assertEquals(CelestialObjectKey.registered(CelestialObjectId.MARS), mars.key());
    }

    @Test
    void findByDimensionIdReturnsEmptyForUnknownDimension() {
        assertTrue(
            GalaxiaCelestialAPI.findByDimension(Integer.MIN_VALUE)
                .isEmpty());
    }
}
