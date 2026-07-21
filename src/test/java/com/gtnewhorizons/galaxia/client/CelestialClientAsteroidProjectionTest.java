package com.gtnewhorizons.galaxia.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldClientCatalogState;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldNode;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldNodeCatalog;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldOrbitResolver;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldProfile;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidSizeClass;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidSlotRanges;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidStarmapProjection;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.MinorCelestialBodyId;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryCapability;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryClientState;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryScanSnapshot;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryStep;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeClientState;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeFacts;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialResourceKnowledgeState;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.DiscoveryState;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class CelestialClientAsteroidProjectionTest {

    @BeforeAll
    static void init() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();
    }

    @Test
    void syncedKnowledgeChangesCanonicalChildKeysAndProjectionsStaySubset() {
        CelestialClient.clear();
        CelestialKnowledgeClientState.clear();
        AsteroidFieldClientCatalogState.clear();
        int visibleIndex = AsteroidSlotRanges.GENERATED_SLOT_MIN;
        int hiddenIndex = AsteroidSlotRanges.GENERATED_SLOT_MIN + 1;
        CelestialObject frozenBelt = CelestialRegistry.get(CelestialObjectId.FROZEN_BELT)
            .orElseThrow();
        CelestialObjectKey beltKey = frozenBelt.id();
        AsteroidFieldNodeCatalog catalog = AsteroidFieldNodeCatalog.fromGenerated(
            CelestialObjectId.FROZEN_BELT,
            frozenBelt.properties()
                .asteroidFieldProfile());
        Map<CelestialObjectKey, CelestialKnowledgeFacts> facts = new LinkedHashMap<>();
        for (AsteroidFieldNode node : catalog.nodes()) {
            DiscoveryState state = node.index() == visibleIndex ? DiscoveryState.DISCOVERED : DiscoveryState.HIDDEN;
            CelestialResourceKnowledgeState ore = node.index() == visibleIndex
                ? CelestialResourceKnowledgeState.SIGNATURE
                : CelestialResourceKnowledgeState.UNKNOWN;
            facts.put(CelestialObjectKey.minorBody(node.id()), CelestialKnowledgeFacts.of(state, ore));
        }
        CelestialKnowledgeClientState.apply(facts);
        AsteroidFieldClientCatalogState.update(Map.of(CelestialObjectId.FROZEN_BELT, catalog.snapshots()));

        CelestialObjectKey visibleKey = CelestialObjectKey
            .minorBody(new MinorCelestialBodyId(CelestialObjectId.FROZEN_BELT, visibleIndex));
        CelestialObjectKey hiddenKey = CelestialObjectKey
            .minorBody(new MinorCelestialBodyId(CelestialObjectId.FROZEN_BELT, hiddenIndex));

        Set<CelestialObjectKey> childKeys = minorChildKeys(CelestialClient.getChildren(beltKey));
        assertTrue(childKeys.contains(visibleKey));
        assertFalse(childKeys.contains(hiddenKey));
        assertEquals(childKeys, projectionKeysForChildren(CelestialClient.getChildren(beltKey)));

        CelestialClient.setShowHiddenAsteroidObjects(true);

        Set<CelestialObjectKey> debugChildKeys = minorChildKeys(CelestialClient.getChildren(beltKey));
        assertTrue(debugChildKeys.contains(hiddenKey));
        assertEquals(debugChildKeys, projectionKeysForChildren(CelestialClient.getChildren(beltKey)));
        AsteroidStarmapProjection hiddenProjection = CelestialClient.asteroidProjection(
            CelestialRegistry.get(hiddenKey)
                .orElseThrow())
            .orElseThrow();
        assertTrue(hiddenProjection.debugHidden());
        assertEquals(DiscoveryState.HIDDEN, hiddenProjection.detectionState());
        CelestialClient.clear();
        CelestialKnowledgeClientState.clear();
        AsteroidFieldClientCatalogState.clear();
    }

    @Test
    void activeScanAndSensorRevealUseCanonicalChildrenWithoutFalsifyingDiscoveryState() {
        CelestialClient.clear();
        CelestialKnowledgeClientState.clear();
        AsteroidFieldClientCatalogState.clear();
        CelestialObject frozenBelt = CelestialRegistry.get(CelestialObjectId.FROZEN_BELT)
            .orElseThrow();
        CelestialObjectKey beltKey = frozenBelt.id();
        AsteroidFieldProfile profile = frozenBelt.properties()
            .asteroidFieldProfile();
        AsteroidFieldNodeCatalog catalog = AsteroidFieldNodeCatalog
            .fromGenerated(CelestialObjectId.FROZEN_BELT, profile);
        AsteroidFieldNode anchor = catalog.nodes()
            .stream()
            .filter(node -> node.sizeClass() == AsteroidSizeClass.LARGE)
            .findFirst()
            .orElseThrow();
        List<AsteroidFieldNode> nearestTargets = catalog.nodes()
            .stream()
            .filter(node -> node.index() != anchor.index())
            .sorted(java.util.Comparator.comparingDouble(node -> distance(profile, anchor, node)))
            .toList();
        AsteroidFieldNode activeTarget = nearestTargets.get(0);
        AsteroidFieldNode sensorTarget = nearestTargets.get(1);
        AsteroidFieldNode outsideTarget = nearestTargets.get(nearestTargets.size() - 1);
        double activeDistance = distance(profile, anchor, activeTarget);
        double sensorDistance = distance(profile, anchor, sensorTarget);
        double outsideDistance = distance(profile, anchor, outsideTarget);
        double scanRadius = (activeDistance + sensorDistance) / 2.0;
        if (sensorDistance > scanRadius) {
            scanRadius = (sensorDistance + outsideDistance) / 2.0;
        }
        assertTrue(sensorDistance <= scanRadius);
        assertTrue(outsideDistance > scanRadius);

        Map<CelestialObjectKey, CelestialKnowledgeFacts> facts = new LinkedHashMap<>();
        for (AsteroidFieldNode node : catalog.nodes()) {
            DiscoveryState state = node.index() == anchor.index() ? DiscoveryState.DISCOVERED : DiscoveryState.HIDDEN;
            facts.put(
                CelestialObjectKey.minorBody(node.id()),
                CelestialKnowledgeFacts.of(state, CelestialResourceKnowledgeState.UNKNOWN));
        }
        CelestialKnowledgeClientState.apply(facts);
        AsteroidFieldClientCatalogState.update(Map.of(CelestialObjectId.FROZEN_BELT, catalog.snapshots()));

        CelestialObjectKey anchorKey = CelestialObjectKey.minorBody(anchor.id());
        CelestialObjectKey targetKey = CelestialObjectKey.minorBody(activeTarget.id());
        CelestialObjectKey sensorKey = CelestialObjectKey.minorBody(sensorTarget.id());
        CelestialObjectKey outsideKey = CelestialObjectKey.minorBody(outsideTarget.id());
        CelestialDiscoveryClientState.update(
            List.of(
                new CelestialDiscoveryScanSnapshot(
                    new java.util.UUID(1L, 2L),
                    anchorKey,
                    scanRadius,
                    1,
                    CelestialDiscoveryCapability.PROSPECTING,
                    CelestialDiscoveryScanSnapshot.Status.ACTIVE,
                    targetKey,
                    CelestialDiscoveryStep.DETECTION,
                    200)));

        Set<CelestialObjectKey> childKeys = minorChildKeys(CelestialClient.getChildren(beltKey));
        assertTrue(childKeys.contains(targetKey));
        assertTrue(childKeys.contains(sensorKey));
        assertFalse(childKeys.contains(outsideKey));
        assertEquals(childKeys, projectionKeysForChildren(CelestialClient.getChildren(beltKey)));

        AsteroidStarmapProjection activeProjection = CelestialClient.asteroidProjection(
            CelestialRegistry.get(targetKey)
                .orElseThrow())
            .orElseThrow();
        AsteroidStarmapProjection sensorProjection = CelestialClient.asteroidProjection(
            CelestialRegistry.get(sensorKey)
                .orElseThrow())
            .orElseThrow();
        assertTrue(activeProjection.scanInProgress());
        assertTrue(sensorProjection.sensorRevealed());
        assertFalse(sensorProjection.scanInProgress());
        assertEquals(
            Optional.of(DiscoveryState.HIDDEN),
            CelestialKnowledgeClientState.discoveryView()
                .discoveryState(targetKey));
        assertEquals(
            Optional.of(DiscoveryState.HIDDEN),
            CelestialKnowledgeClientState.discoveryView()
                .discoveryState(sensorKey));
        CelestialClient.clear();
        CelestialKnowledgeClientState.clear();
        AsteroidFieldClientCatalogState.clear();
        CelestialDiscoveryClientState.clear();
    }

    private static Set<CelestialObjectKey> minorChildKeys(List<CelestialObject> children) {
        return children.stream()
            .map(CelestialObject::id)
            .filter(CelestialObjectKey::isMinorBody)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static Set<CelestialObjectKey> projectionKeysForChildren(List<CelestialObject> children) {
        Set<CelestialObjectKey> keys = new LinkedHashSet<>();
        for (CelestialObject child : children) {
            CelestialClient.asteroidProjection(child)
                .ifPresent(
                    projection -> keys.add(
                        projection.body()
                            .id()));
        }
        return keys;
    }

    private static double distance(AsteroidFieldProfile profile, AsteroidFieldNode first, AsteroidFieldNode second) {
        double firstRadius = AsteroidFieldOrbitResolver.resolveRadius(profile, first);
        double firstAngle = Math.toRadians(first.angleOffsetDeg());
        double secondRadius = AsteroidFieldOrbitResolver.resolveRadius(profile, second);
        double secondAngle = Math.toRadians(second.angleOffsetDeg());
        double dx = Math.cos(firstAngle) * firstRadius - Math.cos(secondAngle) * secondRadius;
        double dy = Math.sin(firstAngle) * firstRadius - Math.sin(secondAngle) * secondRadius;
        return Math.sqrt(dx * dx + dy * dy);
    }
}
