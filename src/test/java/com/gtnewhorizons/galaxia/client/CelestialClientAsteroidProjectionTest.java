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
import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;
import com.gtnewhorizons.galaxia.registry.celestial.MinorCelestialBodyId;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldClientKnowledgeState;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldKnowledgeSnapshot;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldNode;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldNodeCatalog;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldOrbitResolver;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldProfile;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldScanPass;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidOreKnowledgeState;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidSizeClass;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidSlotRanges;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidStarmapProjection;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.DiscoveryState;
import com.gtnewhorizons.galaxia.registry.satellite.AsteroidSatelliteScanSnapshot;
import com.gtnewhorizons.galaxia.registry.satellite.AsteroidScanClientState;
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
                                node.index() == visibleIndex ? AsteroidOreKnowledgeState.SIGNATURE
                                    : AsteroidOreKnowledgeState.UNKNOWN))
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
        AsteroidFieldNode activeTarget = catalog.nodes()
            .stream()
            .filter(node -> node.index() != anchor.index())
            .filter(node -> distance(profile, anchor, node) <= profile.satelliteScanRadius())
            .findFirst()
            .orElseThrow();
        AsteroidFieldNode sensorTarget = catalog.nodes()
            .stream()
            .filter(node -> node.index() != anchor.index() && node.index() != activeTarget.index())
            .filter(node -> distance(profile, anchor, node) > profile.satelliteScanRadius())
            .filter(node -> distance(profile, anchor, node) <= profile.satelliteScanRadius() * 2.0)
            .findFirst()
            .orElseThrow();

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
                                AsteroidOreKnowledgeState.UNKNOWN))
                        .toList(),
                    catalog.snapshots())));
        AsteroidScanClientState.updateScans(
            List.of(
                new AsteroidSatelliteScanSnapshot(
                    CelestialObjectId.FROZEN_BELT,
                    anchor.id(),
                    activeTarget.id(),
                    AsteroidFieldScanPass.DETECTION,
                    200,
                    1)),
            List.of());

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
