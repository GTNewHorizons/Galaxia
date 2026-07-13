package com.gtnewhorizons.galaxia.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.MinorCelestialBodyId;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldClientKnowledgeState;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldKnowledgeSnapshot;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldNode;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldNodeCatalog;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldOrbitResolver;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldProfile;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidSizeClass;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidSlotRanges;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidStarmapProjection;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryClientState;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryCapability;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryScanSnapshot;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryStep;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialResourceKnowledgeState;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.DiscoveryState;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteKind;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteDiscoveryWorkerSource;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class CelestialClientAsteroidProjectionTest {

    @BeforeAll
    static void init() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();
    }

    @Test
    void childAsteroidProjectionsUseClientKnowledgeAndDebugHiddenToggle() {
        CelestialClient.clear();
        int visibleIndex = AsteroidSlotRanges.GENERATED_SLOT_MIN;
        int hiddenIndex = AsteroidSlotRanges.GENERATED_SLOT_MIN + 1;
        CelestialObject frozenBelt = CelestialRegistry.get(CelestialObjectId.FROZEN_BELT)
            .orElseThrow();
        AsteroidFieldNodeCatalog catalog = AsteroidFieldNodeCatalog.fromGenerated(
            CelestialObjectId.FROZEN_BELT,
            frozenBelt.properties()
                .asteroidFieldProfile());
        AsteroidFieldClientKnowledgeState.updateFields(
            List.of(
                new AsteroidFieldKnowledgeSnapshot(
                    CelestialObjectId.FROZEN_BELT,
                    catalog.nodes()
                        .stream()
                        .map(
                            node -> new AsteroidFieldKnowledgeSnapshot.Entry(
                                node.index(),
                                node.index() == visibleIndex ? DiscoveryState.DISCOVERED : DiscoveryState.HIDDEN,
                                node.index() == visibleIndex ? CelestialResourceKnowledgeState.SIGNATURE
                                    : CelestialResourceKnowledgeState.UNKNOWN))
                        .toList(),
                    catalog.snapshots())));

        List<AsteroidStarmapProjection> visibleOnly = CelestialClient.getChildAsteroidProjections(frozenBelt);
        List<AsteroidStarmapProjection> cachedVisibleOnly = CelestialClient.getChildAsteroidProjections(frozenBelt);

        assertSame(visibleOnly, cachedVisibleOnly);
        assertEquals(
            List.of(new MinorCelestialBodyId(CelestialObjectId.FROZEN_BELT, visibleIndex)),
            visibleOnly.stream()
                .map(AsteroidStarmapProjection::id)
                .toList());
        assertFalse(
            visibleOnly.get(0)
                .debugHidden());

        CelestialClient.setShowHiddenAsteroidObjects(true);

        List<AsteroidStarmapProjection> withHidden = CelestialClient.getChildAsteroidProjections(frozenBelt);

        assertNotSame(visibleOnly, withHidden);
        assertTrue(
            withHidden.stream()
                .anyMatch(
                    projection -> projection.id()
                        .equals(new MinorCelestialBodyId(CelestialObjectId.FROZEN_BELT, hiddenIndex))
                        && projection.debugHidden()));
        CelestialClient.clear();
    }

    @Test
    void activeProspectingSatelliteRevealsHiddenAsteroidsInsideExtendedSensorRadius() {
        CelestialClient.clear();
        CelestialObject frozenBelt = CelestialRegistry.get(CelestialObjectId.FROZEN_BELT)
            .orElseThrow();
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
            .limit(2)
            .toList();
        AsteroidFieldNode activeTarget = nearestTargets.get(0);
        AsteroidFieldNode sensorTarget = nearestTargets.get(1);
        double activeDistance = distance(profile, anchor, activeTarget);
        double sensorDistance = distance(profile, anchor, sensorTarget);
        double scanRadius = (Math.max(activeDistance, sensorDistance / 2.0) + sensorDistance) / 2.0;

        AsteroidFieldClientKnowledgeState.updateFields(
            List.of(
                new AsteroidFieldKnowledgeSnapshot(
                    CelestialObjectId.FROZEN_BELT,
                    catalog.nodes()
                        .stream()
                        .map(
                            node -> new AsteroidFieldKnowledgeSnapshot.Entry(
                                node.index(),
                                node.index() == anchor.index() ? DiscoveryState.DISCOVERED : DiscoveryState.HIDDEN,
                                CelestialResourceKnowledgeState.UNKNOWN))
                        .toList(),
                    catalog.snapshots())));
        CelestialObjectKey anchorKey = CelestialObjectKey.minorBody(anchor.id());
        CelestialObjectKey targetKey = CelestialObjectKey.minorBody(activeTarget.id());
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

        List<AsteroidStarmapProjection> projections = CelestialClient.getChildAsteroidProjections(frozenBelt);
        AsteroidStarmapProjection activeProjection = projection(projections, activeTarget.id());
        AsteroidStarmapProjection sensorProjection = projection(projections, sensorTarget.id());

        assertTrue(activeProjection.scanInProgress());
        assertFalse(sensorProjection.scanInProgress());
        assertTrue(sensorProjection.sensorRevealed());
        assertFalse(sensorProjection.debugHidden());
        CelestialClient.clear();
    }

    private static AsteroidStarmapProjection projection(List<AsteroidStarmapProjection> projections,
        MinorCelestialBodyId id) {

        return projections.stream()
            .filter(
                candidate -> candidate.id()
                    .equals(id))
            .findFirst()
            .orElseThrow();
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
