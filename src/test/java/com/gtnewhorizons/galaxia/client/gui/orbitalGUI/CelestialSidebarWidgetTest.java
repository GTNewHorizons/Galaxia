package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.MinorCelestialBodyId;

final class CelestialSidebarWidgetTest {

    @Test
    void searchResultsAreLimitedToRegisteredMajorBodies() {
        CelestialObject majorBody = CelestialObject.builder()
            .key(CelestialObjectId.MARS)
            .name("Mars")
            .objectClass(CelestialObject.Class.PLANET)
            .build();
        CelestialObject minorAsteroid = CelestialObject.builder()
            .key(CelestialObjectKey.minorBody(new MinorCelestialBodyId(CelestialObjectId.FROZEN_BELT, 7)))
            .name("Frozen Belt 7")
            .objectClass(CelestialObject.Class.ASTEROID)
            .build();

        assertTrue(CelestialSidebarWidget.isMajorBodySearchResult(majorBody));
        assertFalse(CelestialSidebarWidget.isMajorBodySearchResult(minorAsteroid));
    }
}
