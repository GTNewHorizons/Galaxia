package com.gtnewhorizons.galaxia.registry.celestial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.asteroid.MinorCelestialBodyId;

final class CelestialObjectKeyTest {

    @Test
    void registeredKeyKeepsStaticBodyId() {
        CelestialObjectKey key = CelestialObjectKey.registered(CelestialObjectId.MARS);

        assertTrue(key.isRegistered());
        assertFalse(key.isMinorBody());
        assertEquals(CelestialObjectId.MARS, key.registeredBodyId());
        assertEquals(CelestialObjectId.MARS, key.requireRegisteredBodyId());
    }

    @Test
    void minorKeyKeepsStructuredMinorBodyId() {
        MinorCelestialBodyId asteroidId = new MinorCelestialBodyId(CelestialObjectId.FROZEN_BELT, 7);
        CelestialObjectKey key = CelestialObjectKey.minorBody(asteroidId);

        assertTrue(key.isMinorBody());
        assertFalse(key.isRegistered());
        assertEquals(asteroidId, key.minorBodyId());
        assertThrows(IllegalStateException.class, key::requireRegisteredBodyId);
    }

    @Test
    void keyRequiresExactlyOneTarget() {
        assertThrows(NullPointerException.class, () -> CelestialObjectKey.registered(null));
        assertThrows(NullPointerException.class, () -> CelestialObjectKey.minorBody(null));
        assertThrows(IllegalStateException.class, () -> new CelestialObjectKey(null, null));
        assertThrows(
            IllegalStateException.class,
            () -> new CelestialObjectKey(
                CelestialObjectId.MARS,
                new MinorCelestialBodyId(CelestialObjectId.FROZEN_BELT, 0)));
    }
}
