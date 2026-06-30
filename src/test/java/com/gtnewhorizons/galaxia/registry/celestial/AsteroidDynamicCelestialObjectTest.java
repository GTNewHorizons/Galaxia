package com.gtnewhorizons.galaxia.registry.celestial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.client.EnumTextures;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidDetectionState;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldKnowledgeSnapshot;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldNode;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldProfile;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldResolver;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidNodeKind;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidOreKnowledgeState;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidSizeClass;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidSlotRanges;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.MinorCelestialBodyId;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class AsteroidDynamicCelestialObjectTest {

    @BeforeAll
    static void init() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();
    }

    @Test
    void frozenBeltMinorBodyKeyResolvesGeneratedAsteroidObject() {
        CelestialObjectKey key = CelestialObjectKey
            .minorBody(new MinorCelestialBodyId(CelestialObjectId.FROZEN_BELT, AsteroidSlotRanges.GENERATED_SLOT_MIN));

        CelestialObject asteroid = CelestialRegistry.get(key)
            .orElseThrow();

        assertEquals(key, asteroid.id());
        assertEquals(CelestialObjectKey.registered(CelestialObjectId.FROZEN_BELT), asteroid.parentId());
        assertEquals(CelestialObject.Class.ASTEROID, asteroid.objectClass());
        assertEquals("FROZEN_BELT 1", asteroid.name());
        assertTrue(
            asteroid.properties()
                .canCreateOutpost());
        assertFalse(
            asteroid.properties()
                .canCreateStation());
    }

    @Test
    void generatedAsteroidObjectsDoNotPolluteStaticRegistryListing() {
        CelestialObjectKey key = CelestialObjectKey
            .minorBody(new MinorCelestialBodyId(CelestialObjectId.FROZEN_BELT, AsteroidSlotRanges.GENERATED_SLOT_MIN));

        assertTrue(
            CelestialRegistry.get(key)
                .isPresent());
        assertFalse(
            CelestialRegistry.getAllBodies()
                .containsKey(key));
    }

    @Test
    void dynamicAsteroidCanBeFoundThroughRegistryFindById() {
        CelestialObjectKey key = CelestialObjectKey
            .minorBody(new MinorCelestialBodyId(CelestialObjectId.FROZEN_BELT, AsteroidSlotRanges.GENERATED_SLOT_MIN));

        CelestialObject asteroid = CelestialRegistry.findById(key)
            .orElseThrow();

        assertEquals(key, asteroid.id());
    }

    @Test
    void frozenBeltPresetAsteroidHasNameKindAndVisibilityOverride() {
        CelestialObjectKey key = CelestialObjectKey
            .minorBody(new MinorCelestialBodyId(CelestialObjectId.FROZEN_BELT, 1));

        CelestialObject asteroid = CelestialRegistry.get(key)
            .orElseThrow();

        assertEquals("Karnyx", asteroid.name());
        assertEquals(
            AsteroidNodeKind.LORE,
            asteroid.properties()
                .asteroidNodeKind());
        assertEquals(
            AsteroidSizeClass.LARGE,
            asteroid.properties()
                .asteroidSizeClass());
        assertTrue(
            GalaxiaCelestialAPI.getChildren(CelestialObjectId.FROZEN_BELT)
                .stream()
                .map(CelestialObject::id)
                .anyMatch(key::equals));
    }

    @Test
    void asteroidBeltChildrenIncludeOnlyInitiallyDetectedMinorBodies() {
        CelestialObject frozenBelt = CelestialRegistry.get(CelestialObjectId.FROZEN_BELT)
            .orElseThrow();
        AsteroidFieldProfile profile = frozenBelt.properties()
            .asteroidFieldProfile();
        Set<CelestialObjectKey> initiallyDetectedKeys = AsteroidFieldResolver
            .resolveAll(CelestialObjectId.FROZEN_BELT, profile)
            .stream()
            .filter(node -> AsteroidFieldResolver.initialDetectionState(node) == AsteroidDetectionState.DETECTED)
            .map(AsteroidFieldNode::id)
            .map(CelestialObjectKey::minorBody)
            .collect(Collectors.toSet());

        List<CelestialObject> children = GalaxiaCelestialAPI.getChildren(CelestialObjectId.FROZEN_BELT);
        Set<CelestialObjectKey> asteroidChildKeys = children.stream()
            .map(CelestialObject::id)
            .filter(CelestialObjectKey::isMinorBody)
            .collect(Collectors.toSet());

        assertFalse(initiallyDetectedKeys.isEmpty());
        assertTrue(initiallyDetectedKeys.size() >= 5);
        assertEquals(initiallyDetectedKeys, asteroidChildKeys);
        assertFalse(
            children.stream()
                .anyMatch(
                    child -> child.objectClass() == CelestialObject.Class.ASTEROID && child.id()
                        .isRegistered()));
        assertTrue(
            children.stream()
                .filter(child -> child.objectClass() == CelestialObject.Class.ASTEROID)
                .noneMatch(
                    child -> EnumTextures.ICON_AMBERGRIS.get()
                        .equals(child.texture())));
    }

    @Test
    void asteroidBeltChildrenCanUseTeamKnowledgeSnapshots() {
        CelestialObject frozenBelt = CelestialRegistry.get(CelestialObjectId.FROZEN_BELT)
            .orElseThrow();
        AsteroidFieldProfile profile = frozenBelt.properties()
            .asteroidFieldProfile();
        AsteroidFieldNode hiddenNode = AsteroidFieldResolver.resolveAll(CelestialObjectId.FROZEN_BELT, profile)
            .stream()
            .filter(node -> AsteroidFieldResolver.initialDetectionState(node) == AsteroidDetectionState.HIDDEN)
            .findFirst()
            .orElseThrow();
        AsteroidFieldKnowledgeSnapshot snapshot = new AsteroidFieldKnowledgeSnapshot(
            CelestialObjectId.FROZEN_BELT,
            List.of(
                new AsteroidFieldKnowledgeSnapshot.Entry(
                    hiddenNode.index(),
                    AsteroidDetectionState.DETECTED,
                    AsteroidOreKnowledgeState.UNKNOWN)));

        List<CelestialObject> children = GalaxiaCelestialAPI
            .getChildren(CelestialObjectId.FROZEN_BELT, List.of(snapshot));

        assertTrue(
            children.stream()
                .map(CelestialObject::id)
                .anyMatch(CelestialObjectKey.minorBody(hiddenNode.id())::equals));
    }

    @Test
    void asteroidBeltChildrenCanIncludeHiddenAsteroidsForDebugViews() {
        CelestialObject frozenBelt = CelestialRegistry.get(CelestialObjectId.FROZEN_BELT)
            .orElseThrow();
        AsteroidFieldProfile profile = frozenBelt.properties()
            .asteroidFieldProfile();
        Set<CelestialObjectKey> allAsteroidKeys = AsteroidFieldResolver
            .resolveAll(CelestialObjectId.FROZEN_BELT, profile)
            .stream()
            .map(AsteroidFieldNode::id)
            .map(CelestialObjectKey::minorBody)
            .collect(Collectors.toSet());
        Set<CelestialObjectKey> normalAsteroidKeys = GalaxiaCelestialAPI.getChildren(CelestialObjectId.FROZEN_BELT)
            .stream()
            .map(CelestialObject::id)
            .filter(CelestialObjectKey::isMinorBody)
            .collect(Collectors.toSet());

        Set<CelestialObjectKey> debugAsteroidKeys = GalaxiaCelestialAPI
            .getChildren(CelestialObjectId.FROZEN_BELT, List.of(), true)
            .stream()
            .map(CelestialObject::id)
            .filter(CelestialObjectKey::isMinorBody)
            .collect(Collectors.toSet());

        assertEquals(allAsteroidKeys, debugAsteroidKeys);
        assertTrue(debugAsteroidKeys.size() > normalAsteroidKeys.size());
    }

    @Test
    void detectedSmallAsteroidsAreVisitableOutpostTargets() {
        CelestialObject frozenBelt = CelestialRegistry.get(CelestialObjectId.FROZEN_BELT)
            .orElseThrow();
        AsteroidFieldProfile profile = frozenBelt.properties()
            .asteroidFieldProfile();
        AsteroidFieldNode smallNode = AsteroidFieldResolver.resolveAll(CelestialObjectId.FROZEN_BELT, profile)
            .stream()
            .filter(node -> node.sizeClass() == AsteroidSizeClass.SMALL)
            .findFirst()
            .orElseThrow();
        AsteroidFieldKnowledgeSnapshot snapshot = new AsteroidFieldKnowledgeSnapshot(
            CelestialObjectId.FROZEN_BELT,
            List.of(
                new AsteroidFieldKnowledgeSnapshot.Entry(
                    smallNode.index(),
                    AsteroidDetectionState.DETECTED,
                    AsteroidOreKnowledgeState.UNKNOWN)));

        CelestialObject asteroid = GalaxiaCelestialAPI.getChildren(CelestialObjectId.FROZEN_BELT, List.of(snapshot))
            .stream()
            .filter(
                child -> child.id()
                    .equals(CelestialObjectKey.minorBody(smallNode.id())))
            .findFirst()
            .orElseThrow();

        assertTrue(
            asteroid.properties()
                .visitable());
        assertTrue(
            asteroid.properties()
                .canCreateOutpost());
    }
}
