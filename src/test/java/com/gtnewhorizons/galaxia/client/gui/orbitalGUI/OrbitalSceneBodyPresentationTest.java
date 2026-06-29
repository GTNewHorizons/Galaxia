package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.MinorCelestialBodyId;

final class OrbitalSceneBodyPresentationTest {

    @Test
    void asteroidBeltContainerHasNoSpriteOrInteractionTarget() {
        CelestialObject belt = CelestialObject.builder()
            .id(CelestialObjectId.FROZEN_BELT)
            .name("Frozen Belt")
            .objectClass(CelestialObject.Class.ASTEROID_BELT)
            .build();

        assertFalse(OrbitalScene.drawsBodySprite(belt));
        assertFalse(OrbitalScene.registersBodyInteraction(belt));
    }

    @Test
    void asteroidFieldMembersStillRenderAndAcceptInteraction() {
        CelestialObject asteroid = CelestialObject.builder()
            .id(CelestialObjectKey.minorBody(new MinorCelestialBodyId(CelestialObjectId.FROZEN_BELT, 0)))
            .name("Frozen Belt 1")
            .objectClass(CelestialObject.Class.ASTEROID)
            .build();

        assertTrue(OrbitalScene.drawsBodySprite(asteroid));
        assertTrue(OrbitalScene.registersBodyInteraction(asteroid));
    }
}
