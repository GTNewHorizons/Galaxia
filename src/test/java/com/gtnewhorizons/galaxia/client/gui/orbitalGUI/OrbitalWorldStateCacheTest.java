package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.MinorCelestialBodyId;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalMechanics;

final class OrbitalWorldStateCacheTest {

    private static final double EPSILON = 1.0e-9;

    @Test
    void dynamicAsteroidLookupUsesStableBodyKeyAcrossMaterializations() {
        CelestialObject belt = body(CelestialObjectId.FROZEN_BELT, "Frozen Belt", CelestialObject.Class.ASTEROID_BELT);
        CelestialObject renderedAsteroid = asteroid("Kalrnyx");
        CelestialObject equivalentAsteroid = asteroid("Kalrnyx");
        OrbitalView.OrbitalWorldStateCache cache = new OrbitalView.OrbitalWorldStateCache();
        OrbitalMechanics.OrbitalState state = new OrbitalMechanics.OrbitalState(12.0, 34.0, 1.0, 2.0);

        cache.recordState(renderedAsteroid, belt, state);

        double[] renderedPosition = cache.getWorldPosition(renderedAsteroid);
        double[] equivalentPosition = cache.getWorldPosition(equivalentAsteroid);
        CelestialObject equivalentParent = cache.getParent(equivalentAsteroid);
        assertAll(
            () -> assertNotSame(renderedAsteroid, equivalentAsteroid),
            () -> assertTrue(OrbitalView.OrbitalMapWidget.sameBody(renderedAsteroid, equivalentAsteroid)),
            () -> assertTrue(
                OrbitalView.OrbitalMapWidget.containsBodyByKey(List.of(renderedAsteroid), equivalentAsteroid)),
            () -> assertNotNull(renderedPosition),
            () -> assertNotNull(equivalentPosition),
            () -> assertArrayEquals(renderedPosition, equivalentPosition, EPSILON),
            () -> assertNotNull(equivalentParent),
            () -> assertEquals(belt.id(), equivalentParent.id()));
    }

    @Test
    void rootLayerTraversalIncludesAsteroidBeltContainers() {
        CelestialObject star = body(CelestialObjectId.VAEL, "Vael", CelestialObject.Class.STAR);
        CelestialObject belt = body(CelestialObjectId.FROZEN_BELT, "Frozen Belt", CelestialObject.Class.ASTEROID_BELT);
        CelestialObject planet = body(CelestialObjectId.MARS, "Mars", CelestialObject.Class.PLANET);

        assertAll(
            () -> assertTrue(OrbitalView.OrbitalMapWidget.shouldTraverseChildrenInLayer(star, star, star)),
            () -> assertTrue(OrbitalView.OrbitalMapWidget.shouldTraverseChildrenInLayer(star, star, belt)),
            () -> assertFalse(OrbitalView.OrbitalMapWidget.shouldTraverseChildrenInLayer(star, star, planet)));
    }

    private static CelestialObject body(CelestialObjectId id, String name, CelestialObject.Class objectClass) {
        return CelestialObject.builder()
            .id(id)
            .name(name)
            .objectClass(objectClass)
            .build();
    }

    private static CelestialObject asteroid(String name) {
        return CelestialObject.builder()
            .id(CelestialObjectKey.minorBody(new MinorCelestialBodyId(CelestialObjectId.FROZEN_BELT, 0)))
            .name(name)
            .parent(CelestialObjectId.FROZEN_BELT)
            .objectClass(CelestialObject.Class.ASTEROID)
            .build();
    }
}
