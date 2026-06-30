package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidNodeKind;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidSizeClass;
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

    @Test
    void defaultAsteroidLabelsAreLimitedToLargeOrLoreAsteroids() {
        assertTrue(OrbitalScene.drawsDefaultBodyLabel(asteroid(AsteroidNodeKind.GENERATED, AsteroidSizeClass.LARGE)));
        assertTrue(OrbitalScene.drawsDefaultBodyLabel(asteroid(AsteroidNodeKind.LORE, AsteroidSizeClass.MEDIUM)));
        assertFalse(OrbitalScene.drawsDefaultBodyLabel(asteroid(AsteroidNodeKind.GENERATED, AsteroidSizeClass.MEDIUM)));
        assertFalse(OrbitalScene.drawsDefaultBodyLabel(asteroid(AsteroidNodeKind.UNIQUE, AsteroidSizeClass.SMALL)));
    }

    @Test
    void lowerPriorityAsteroidsAreDeclutteredNearAcceptedScreenBodies() {
        CelestialObject accepted = asteroid(AsteroidNodeKind.LORE, AsteroidSizeClass.LARGE);
        CelestialObject crowded = asteroid(AsteroidNodeKind.GENERATED, AsteroidSizeClass.MEDIUM);
        OrbitalScene.OrbitalSceneFrame frame = new OrbitalScene.OrbitalSceneFrame();
        frame.addScreenBody(accepted, 100f, 100f, 7f, 12f);

        assertTrue(OrbitalScene.shouldDeclutterBody(crowded, 106f, 100f, 12f, frame.screenBodies));
        assertFalse(OrbitalScene.shouldDeclutterBody(crowded, 130f, 100f, 12f, frame.screenBodies));
    }

    private static CelestialObject asteroid(AsteroidNodeKind kind, AsteroidSizeClass sizeClass) {
        return CelestialObject.builder()
            .id(CelestialObjectKey.minorBody(new MinorCelestialBodyId(CelestialObjectId.FROZEN_BELT, 0)))
            .name("Frozen Belt 1")
            .objectClass(CelestialObject.Class.ASTEROID)
            .properties(properties -> properties.asteroidMetadata(kind, sizeClass))
            .build();
    }
}
