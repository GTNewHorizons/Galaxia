package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;

final class OrbitalViewTransitionZoomTest {

    @Test
    void nearestOtherStarDistanceUsesGalaxyStarsAndIgnoresPlanets() {
        CelestialObject anchorStar = body(CelestialObjectId.ILIA, "Ilia", CelestialObject.Class.STAR);
        CelestialObject otherStar = body(CelestialObjectId.VAEL, "Vael", CelestialObject.Class.STAR);
        CelestialObject nearbyPlanet = body(CelestialObjectId.ROMULUS, "Romulus", CelestialObject.Class.PLANET);
        Map<CelestialObject, double[]> positions = new HashMap<>();
        positions.put(anchorStar, new double[] { 0.0, 0.0 });
        positions.put(otherStar, new double[] { 1000.0, 0.0 });
        positions.put(nearbyPlanet, new double[] { 10.0, 0.0 });

        double distance = OrbitalView.OrbitalMapWidget
            .nearestOtherStarDistance(anchorStar, List.of(anchorStar, otherStar, nearbyPlanet), positions::get);

        assertEquals(1000.0, distance);
    }

    private static CelestialObject body(CelestialObjectId id, String name, CelestialObject.Class objectClass) {
        return CelestialObject.builder()
            .id(id)
            .name(name)
            .objectClass(objectClass)
            .build();
    }
}
