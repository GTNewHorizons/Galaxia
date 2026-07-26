package com.gtnewhorizons.galaxia.registry.celestial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldNode;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldProfile;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldResolver;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidSlotRanges;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.MinorCelestialBodyId;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeClientState.CelestialDiscoveryView;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeFacts.DiscoveryState;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class CelestialRegistryChildrenTest {

    @BeforeAll
    static void init() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();
    }

    @Test
    void marsChildrenIncludeRegisteredMoon() {
        List<CelestialObject> children = CelestialRegistry
            .children(CelestialObjectKey.registered(CelestialObjectId.MARS), CelestialDiscoveryView.empty(), false);

        assertTrue(
            children.stream()
                .map(CelestialObject::id)
                .anyMatch(CelestialObjectKey.registered(CelestialObjectId.MOON)::equals));
        assertTrue(
            children.stream()
                .map(CelestialObject::id)
                .noneMatch(CelestialObjectKey::isMinorBody));
    }

    @Test
    void frozenBeltEmptyViewReturnsInitialDiscoveredAsteroids() {
        Set<CelestialObjectKey> initiallyDetectedKeys = initiallyDetectedAsteroidKeys();

        Set<CelestialObjectKey> childKeys = childKeys(
            CelestialRegistry.children(
                CelestialObjectKey.registered(CelestialObjectId.FROZEN_BELT),
                CelestialDiscoveryView.empty(),
                false));

        assertFalse(initiallyDetectedKeys.isEmpty());
        assertTrue(childKeys.containsAll(initiallyDetectedKeys));
        assertEquals(initiallyDetectedKeys, minorKeys(childKeys));
    }

    @Test
    void suppliedViewRevealingHiddenKeyAddsThatAsteroid() {
        AsteroidFieldNode hiddenNode = firstHiddenAsteroid();
        CelestialObjectKey hiddenKey = CelestialObjectKey.minorBody(hiddenNode.id());

        Set<CelestialObjectKey> withoutReveal = minorKeys(
            childKeys(
                CelestialRegistry.children(
                    CelestialObjectKey.registered(CelestialObjectId.FROZEN_BELT),
                    CelestialDiscoveryView.empty(),
                    false)));
        Set<CelestialObjectKey> withReveal = minorKeys(
            childKeys(
                CelestialRegistry.children(
                    CelestialObjectKey.registered(CelestialObjectId.FROZEN_BELT),
                    discoveryView(hiddenNode),
                    false)));

        assertFalse(withoutReveal.contains(hiddenKey));
        assertTrue(withReveal.contains(hiddenKey));
        assertTrue(withReveal.containsAll(initiallyDetectedAsteroidKeys()));
    }

    @Test
    void partialViewKeepsInitialDiscoveredNodes() {
        Set<CelestialObjectKey> initiallyDetectedKeys = initiallyDetectedAsteroidKeys();
        AsteroidFieldNode hiddenNode = firstHiddenAsteroid();

        Set<CelestialObjectKey> childKeys = minorKeys(
            childKeys(
                CelestialRegistry.children(
                    CelestialObjectKey.registered(CelestialObjectId.FROZEN_BELT),
                    discoveryView(hiddenNode),
                    false)));

        assertTrue(childKeys.containsAll(initiallyDetectedKeys));
        assertTrue(childKeys.contains(CelestialObjectKey.minorBody(hiddenNode.id())));
    }

    @Test
    void includeHiddenReturnsAllAsteroidNodeKeys() {
        Set<CelestialObjectKey> allAsteroidKeys = allAsteroidKeys();

        Set<CelestialObjectKey> childKeys = minorKeys(
            childKeys(
                CelestialRegistry.children(
                    CelestialObjectKey.registered(CelestialObjectId.FROZEN_BELT),
                    CelestialDiscoveryView.empty(),
                    true)));

        assertEquals(allAsteroidKeys, childKeys);
        assertTrue(childKeys.size() > initiallyDetectedAsteroidKeys().size());
    }

    @Test
    void childrenResultIsImmutableAndHasNoDuplicateKeys() {
        List<CelestialObject> children = CelestialRegistry.children(
            CelestialObjectKey.registered(CelestialObjectId.FROZEN_BELT),
            CelestialDiscoveryView.empty(),
            true);

        List<CelestialObjectKey> keys = children.stream()
            .map(CelestialObject::id)
            .toList();
        assertEquals(keys.size(), new HashSet<>(keys).size());
        assertThrows(UnsupportedOperationException.class, () -> children.add(children.get(0)));
    }

    @Test
    void findByIdStillResolvesMinorBodyRegardlessOfVisibility() {
        CelestialObjectKey key = CelestialObjectKey
            .minorBody(new MinorCelestialBodyId(CelestialObjectId.FROZEN_BELT, AsteroidSlotRanges.GENERATED_SLOT_MIN));

        CelestialObject asteroid = CelestialRegistry.findById(key)
            .orElseThrow();

        assertEquals(key, asteroid.id());
        assertEquals(CelestialObjectKey.registered(CelestialObjectId.FROZEN_BELT), asteroid.parentId());
        assertEquals(CelestialObject.Class.ASTEROID, asteroid.objectClass());
    }

    private static Set<CelestialObjectKey> initiallyDetectedAsteroidKeys() {
        return resolveAllNodes().stream()
            .filter(node -> node.initialDetectionState() == DiscoveryState.DISCOVERED)
            .map(AsteroidFieldNode::id)
            .map(CelestialObjectKey::minorBody)
            .collect(Collectors.toSet());
    }

    private static Set<CelestialObjectKey> allAsteroidKeys() {
        return resolveAllNodes().stream()
            .map(AsteroidFieldNode::id)
            .map(CelestialObjectKey::minorBody)
            .collect(Collectors.toSet());
    }

    private static AsteroidFieldNode firstHiddenAsteroid() {
        return resolveAllNodes().stream()
            .filter(node -> node.initialDetectionState() == DiscoveryState.HIDDEN)
            .findFirst()
            .orElseThrow();
    }

    private static List<AsteroidFieldNode> resolveAllNodes() {
        CelestialObject frozenBelt = CelestialRegistry.get(CelestialObjectId.FROZEN_BELT)
            .orElseThrow();
        AsteroidFieldProfile profile = frozenBelt.properties()
            .asteroidFieldProfile();
        return AsteroidFieldResolver.resolveAll(CelestialObjectId.FROZEN_BELT, profile);
    }

    private static CelestialDiscoveryView discoveryView(AsteroidFieldNode discoveredNode) {
        CelestialObjectKey discoveredKey = CelestialObjectKey.minorBody(discoveredNode.id());
        return key -> discoveredKey.equals(key) ? Optional.of(DiscoveryState.DISCOVERED) : Optional.empty();
    }

    private static Set<CelestialObjectKey> childKeys(List<CelestialObject> children) {
        return children.stream()
            .map(CelestialObject::id)
            .collect(Collectors.toCollection(HashSet::new));
    }

    private static Set<CelestialObjectKey> minorKeys(Set<CelestialObjectKey> keys) {
        return keys.stream()
            .filter(CelestialObjectKey::isMinorBody)
            .collect(Collectors.toSet());
    }
}
