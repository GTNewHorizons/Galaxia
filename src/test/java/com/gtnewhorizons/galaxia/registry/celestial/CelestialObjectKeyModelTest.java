package com.gtnewhorizons.galaxia.registry.celestial;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class CelestialObjectKeyModelTest {

    @Test
    void builderWrapsRegisteredIdsAsKeys() {
        CelestialObject mars = CelestialObject.builder()
            .id(CelestialObjectId.MARS)
            .parent(CelestialObjectId.VAEL)
            .build();

        assertEquals(CelestialObjectKey.registered(CelestialObjectId.MARS), mars.id());
        assertEquals(CelestialObjectKey.registered(CelestialObjectId.VAEL), mars.parentId());
        assertEquals(CelestialObjectId.MARS, mars.requireRegisteredId());
    }

    @Test
    void celestialObjectCanRepresentDynamicMinorBody() {
        CelestialObjectKey asteroidKey = CelestialObjectKey
            .minorBody(new MinorCelestialBodyId(CelestialObjectId.FROZEN_BELT, 3));

        CelestialObject asteroid = CelestialObject.builder()
            .id(asteroidKey)
            .name("Frozen Belt 4")
            .parent(CelestialObjectKey.registered(CelestialObjectId.FROZEN_BELT))
            .objectClass(CelestialObject.Class.ASTEROID)
            .properties(
                b -> b.visitable(true)
                    .canCreateOutpost(true)
                    .canCreateStation(false))
            .build();

        assertEquals(asteroidKey, asteroid.id());
        assertEquals(CelestialObjectKey.registered(CelestialObjectId.FROZEN_BELT), asteroid.parentId());
        assertEquals(CelestialObject.Class.ASTEROID, asteroid.objectClass());
    }
}
