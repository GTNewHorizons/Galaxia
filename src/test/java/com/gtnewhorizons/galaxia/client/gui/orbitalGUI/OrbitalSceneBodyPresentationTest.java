package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.client.CelestialClient;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldProfile;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidNodeKind;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidOreProfile;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidSizeClass;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidStarmapProjection;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.MinorCelestialBodyId;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryClientState;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class OrbitalSceneBodyPresentationTest {

    @BeforeAll
    static void init() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();
    }

    @BeforeEach
    void resetClientState() {
        clearClientState();
    }

    @AfterEach
    void clearClientState() {
        CelestialDiscoveryClientState.clear();
        CelestialClient.setShowHiddenAsteroidObjects(false);
    }

    @Test
    void asteroidBeltContainerHasNoSpriteOrInteractionTarget() {
        CelestialObject belt = asteroidBelt();

        assertTrue(AsteroidStarmapScenePresentation.isBeltContainer(belt));
    }

    @Test
    void asteroidBeltDrawsBandInsteadOfOrbitLine() {
        CelestialObject belt = asteroidBelt();
        CelestialObject planet = CelestialObject.builder()
            .key(CelestialObjectId.MARS)
            .name("Mars")
            .objectClass(CelestialObject.Class.PLANET)
            .build();

        assertTrue(AsteroidStarmapScenePresentation.isBeltContainer(belt));
        assertFalse(AsteroidStarmapScenePresentation.isBeltContainer(planet));
    }

    @Test
    void asteroidFieldMembersStillRenderAndAcceptInteraction() {
        CelestialObject asteroid = CelestialObject.builder()
            .key(CelestialObjectKey.minorBody(new MinorCelestialBodyId(CelestialObjectId.FROZEN_BELT, 0)))
            .name("Frozen Belt 1")
            .objectClass(CelestialObject.Class.ASTEROID)
            .build();

        assertFalse(AsteroidStarmapScenePresentation.isBeltContainer(asteroid));
    }

    @Test
    void nonAsteroidBodiesAlwaysDrawTheirLabel() {
        CelestialObject planet = CelestialRegistry.get(CelestialObjectId.MARS)
            .orElseThrow();

        assertTrue(AsteroidStarmapScenePresentation.drawsDefaultBodyLabel(planet));
        assertTrue(AsteroidStarmapScenePresentation.drawsDefaultBodyLabel(asteroidBelt()));
    }

    @Test
    void generatedAsteroidsDoNotDrawDefaultLabelThroughSharedPresentation() {
        assertFalse(
            AsteroidStarmapScenePresentation
                .drawsDefaultBodyLabel(asteroid(AsteroidNodeKind.GENERATED, AsteroidSizeClass.LARGE)));
    }

    @Test
    void defaultAsteroidLabelsAreLimitedToAuthoredAsteroids() {
        assertFalse(
            CelestialClient.asteroidProjection(asteroid(AsteroidNodeKind.GENERATED, AsteroidSizeClass.LARGE))
                .map(AsteroidStarmapProjection::drawDefaultLabel)
                .orElseThrow());
        assertFalse(
            CelestialClient.asteroidProjection(asteroid(AsteroidNodeKind.GENERATED, AsteroidSizeClass.MEDIUM))
                .map(AsteroidStarmapProjection::drawDefaultLabel)
                .orElseThrow());
        assertFalse(
            CelestialClient.asteroidProjection(asteroid(AsteroidNodeKind.GENERATED, AsteroidSizeClass.SMALL))
                .map(AsteroidStarmapProjection::drawDefaultLabel)
                .orElseThrow());
    }

    @Test
    void asteroidOverviewCullingKeepsUsableMapMarkersVisible() {
        assertFalse(
            OrbitalView.OrbitalMapWidget.shouldCullAsteroidAtNaturalRadius(
                asteroid(AsteroidNodeKind.GENERATED, AsteroidSizeClass.LARGE),
                2.0f));
        assertFalse(
            OrbitalView.OrbitalMapWidget.shouldCullAsteroidAtNaturalRadius(
                asteroid(AsteroidNodeKind.GENERATED, AsteroidSizeClass.MEDIUM),
                2.0f));
        assertFalse(
            OrbitalView.OrbitalMapWidget.shouldCullAsteroidAtNaturalRadius(
                asteroid(AsteroidNodeKind.GENERATED, AsteroidSizeClass.MEDIUM),
                2.0f));
        assertFalse(
            OrbitalView.OrbitalMapWidget.shouldCullAsteroidAtNaturalRadius(
                asteroid(AsteroidNodeKind.GENERATED, AsteroidSizeClass.SMALL),
                2.0f));
    }

    @Test
    void asteroidOverviewCullingStillRemovesMarkersBelowTwoPixelDiameter() {
        assertTrue(
            OrbitalView.OrbitalMapWidget.shouldCullAsteroidAtNaturalRadius(
                asteroid(AsteroidNodeKind.GENERATED, AsteroidSizeClass.LARGE),
                0.99f));
        assertTrue(
            OrbitalView.OrbitalMapWidget.shouldCullAsteroidAtNaturalRadius(
                asteroid(AsteroidNodeKind.GENERATED, AsteroidSizeClass.MEDIUM),
                0.99f));
        assertTrue(
            OrbitalView.OrbitalMapWidget.shouldCullAsteroidAtNaturalRadius(
                asteroid(AsteroidNodeKind.GENERATED, AsteroidSizeClass.SMALL),
                0.99f));
    }

    @Test
    void asteroidMapSpriteRadiusScalesWithZoomWithoutMinimumClamp() {
        CelestialObject smallAsteroid = asteroid(AsteroidNodeKind.GENERATED, AsteroidSizeClass.SMALL, 0.028);

        float farZoomRadius = OrbitalView.OrbitalMapWidget.mapAsteroidSpriteRadiusForRelativeZoom(smallAsteroid, 0.0);
        float overviewRadius = OrbitalView.OrbitalMapWidget.mapAsteroidSpriteRadiusForRelativeZoom(smallAsteroid, 1.0);
        float closerZoomRadius = OrbitalView.OrbitalMapWidget
            .mapAsteroidSpriteRadiusForRelativeZoom(smallAsteroid, 2.0);

        assertEquals(0.0f, farZoomRadius, 0.001f);
        assertTrue(overviewRadius > 0.0f);
        assertEquals(overviewRadius * 2.0f, closerZoomRadius, 0.001f);
    }

    @Test
    void satelliteNetworkSummariesUseAddressableBodyKeys() {
        CelestialObject planet = CelestialRegistry.get(CelestialObjectId.MARS)
            .orElseThrow();
        CelestialObject asteroid = asteroid(AsteroidNodeKind.GENERATED, AsteroidSizeClass.LARGE);

        assertEquals(
            CelestialObjectKey.registered(CelestialObjectId.MARS),
            OrbitalView.OrbitalMapWidget.satelliteNetworkBodyKey(planet));
        assertEquals(asteroid.key(), OrbitalView.OrbitalMapWidget.satelliteNetworkBodyKey(asteroid));
    }

    @Test
    void lowerPriorityAsteroidsAreDeclutteredNearAcceptedScreenBodies() {
        CelestialObject accepted = asteroid(AsteroidSizeClass.LARGE);
        CelestialObject crowded = asteroid(AsteroidSizeClass.MEDIUM);
        OrbitalScene.OrbitalSceneFrame frame = new OrbitalScene.OrbitalSceneFrame();
        frame.addScreenBody(accepted, 100f, 100f, 7f, 12f);

        assertTrue(OrbitalScene.shouldDeclutterBody(crowded, 106f, 100f, 12f, frame.screenBodies));
        assertFalse(OrbitalScene.shouldDeclutterBody(crowded, 130f, 100f, 12f, frame.screenBodies));
    }

    private static CelestialObject asteroid(AsteroidNodeKind kind, AsteroidSizeClass sizeClass) {
        CelestialClient.setShowHiddenAsteroidObjects(true);
        CelestialObject belt = CelestialRegistry.get(CelestialObjectId.FROZEN_BELT)
            .orElseThrow();
        return CelestialClient.getChildren(belt)
            .stream()
            .filter(
                body -> CelestialClient.asteroidProjection(body)
                    .filter(projection -> projection.nodeKind() == kind)
                    .filter(projection -> projection.sizeClass() == sizeClass)
                    .isPresent())
            .findFirst()
            .orElseThrow();
    }

    private static CelestialObject asteroid(AsteroidNodeKind kind, AsteroidSizeClass sizeClass, double spriteSize) {
        return CelestialObject.builder()
            .key(CelestialObjectKey.minorBody(new MinorCelestialBodyId(CelestialObjectId.FROZEN_BELT, 42)))
            .name(kind.name())
            .objectClass(CelestialObject.Class.ASTEROID)
            .spriteSize(spriteSize)
            .properties(properties -> properties.asteroidMetadata(kind, sizeClass))
            .build();
    }

    private static CelestialObject asteroid(AsteroidSizeClass sizeClass) {
        return asteroid(AsteroidNodeKind.GENERATED, sizeClass);
    }

    private static CelestialObject asteroidBelt() {
        return CelestialObject.builder()
            .key(CelestialObjectId.FROZEN_BELT)
            .name("Frozen Belt")
            .objectClass(CelestialObject.Class.ASTEROID_BELT)
            .properties(
                properties -> properties.asteroidFieldProfile(
                    AsteroidFieldProfile.builder()
                        .sizeCounts(1, 1, 1)
                        .radialBand(100.0, 120.0)
                        .placementConnectionRadius(25.0)
                        .oreProfile(new AsteroidOreProfile("test", List.of("test_vein")))
                        .build()))
            .build();
    }
}
