package com.gtnewhorizons.galaxia.registry.celestial;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.asteroid.MinorCelestialBodyId;

final class CelestialObjectKeyModelTest {

    @Test
    void builderWrapsRegisteredIdsAsKeys() {
        CelestialObject mars = CelestialObject.builder()
            .key(CelestialObjectId.MARS)
            .parent(CelestialObjectId.VAEL)
            .build();

        assertEquals(CelestialObjectKey.registered(CelestialObjectId.MARS), mars.key());
        assertEquals(CelestialObjectKey.registered(CelestialObjectId.VAEL), mars.parentKey());
        assertEquals(CelestialObjectId.MARS, mars.requireRegisteredId());
    }

    @Test
    void celestialObjectCanRepresentDynamicMinorBody() {
        CelestialObjectKey asteroidKey = CelestialObjectKey
            .minorBody(new MinorCelestialBodyId(CelestialObjectId.FROZEN_BELT, 3));

        CelestialObject asteroid = CelestialObject.builder()
            .key(asteroidKey)
            .name("Frozen Belt 4")
            .parent(CelestialObjectKey.registered(CelestialObjectId.FROZEN_BELT))
            .objectClass(CelestialObject.Class.ASTEROID)
            .properties(
                b -> b.visitable(true)
                    .canCreateOutpost(true)
                    .canCreateStation(false))
            .build();

        assertEquals(asteroidKey, asteroid.key());
        assertEquals(CelestialObjectKey.registered(CelestialObjectId.FROZEN_BELT), asteroid.parentKey());
        assertEquals(CelestialObject.Class.ASTEROID, asteroid.objectClass());
    }
}
