package com.gtnewhorizons.galaxia.registry.celestial;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.asteroid.MinorCelestialBodyId;

final class CelestialObjectKeyTest {

    @Test
    void naturalOrderSortsRegisteredBodiesBeforeTheirMinorBodies() {
        CelestialObjectKey mars = CelestialObjectKey.registered(CelestialObjectId.MARS);
        CelestialObjectKey belt = CelestialObjectKey.registered(CelestialObjectId.FROZEN_BELT);
        CelestialObjectKey thirdAsteroid = CelestialObjectKey
            .minorBody(new MinorCelestialBodyId(CelestialObjectId.FROZEN_BELT, 3));
        CelestialObjectKey firstAsteroid = CelestialObjectKey
            .minorBody(new MinorCelestialBodyId(CelestialObjectId.FROZEN_BELT, 1));
        List<CelestialObjectKey> keys = new ArrayList<>(List.of(thirdAsteroid, mars, firstAsteroid, belt));

        keys.sort(null);

        assertEquals(List.of(mars, belt, firstAsteroid, thirdAsteroid), keys);
    }
}
