package com.gtnewhorizons.galaxia.registry.celestial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.asteroid.MinorCelestialBodyId;

final class CelestialBodyRefTest {

    @Test
    void registeredRefKeepsStaticBodyId() {
        CelestialBodyRef ref = CelestialBodyRef.registered(CelestialObjectId.MARS);

        assertTrue(ref.isRegistered());
        assertFalse(ref.isMinorBody());
        assertEquals(CelestialObjectId.MARS, ref.registeredBodyId());
    }

    @Test
    void minorRefKeepsStructuredMinorBodyId() {
        MinorCelestialBodyId asteroidId = new MinorCelestialBodyId(CelestialObjectId.FROZEN_BELT, 7);
        CelestialBodyRef ref = CelestialBodyRef.minorBody(asteroidId);

        assertTrue(ref.isMinorBody());
        assertFalse(ref.isRegistered());
        assertEquals(asteroidId, ref.minorBodyId());
    }

    @Test
    void refRequiresExactlyOneTarget() {
        assertThrows(NullPointerException.class, () -> CelestialBodyRef.registered(null));
        assertThrows(NullPointerException.class, () -> CelestialBodyRef.minorBody(null));
        assertThrows(IllegalStateException.class, () -> new CelestialBodyRef(null, null));
        assertThrows(
            IllegalStateException.class,
            () -> new CelestialBodyRef(
                CelestialObjectId.MARS,
                new MinorCelestialBodyId(CelestialObjectId.FROZEN_BELT, 0)));
    }
}
