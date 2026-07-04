package com.gtnewhorizons.galaxia.registry.satellite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldKnowledge;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldNode;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldProfile;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldResolver;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldScanOrder;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidNodeKind;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidOreKnowledgeState;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidOreProfile;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidSizeClass;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AuthoredAsteroidDefinition;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.MinorCelestialBodyId;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeService;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.DiscoveryState;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalMechanics;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class AsteroidSatelliteScanServiceTest {

    private static final UUID TEAM = UUID.fromString("00000000-0000-0000-0000-000000000241");
    private static final CelestialObjectId BELT = CelestialObjectId.FROZEN_BELT;

    @BeforeAll
    static void initCelestialRegistry() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();
    }

    @AfterEach
    void clearKnowledge() {
        CelestialKnowledgeService.clear();
    }

    @Test
    void prospectingSatelliteAnchoredOnAsteroidRunsDetectionSignatureAndProfilePasses() {
        AsteroidFieldProfile profile = profile();
        AsteroidSatelliteScanService service = new AsteroidSatelliteScanService(
            bodyId -> bodyId == BELT ? Optional.of(profile) : Optional.empty());
        AsteroidFieldNode anchor = hiddenAnchor(profile);
        CelestialAsset satellite = prospectingSatellite(anchor.id());

        assertTrue(
            service.tick(TEAM, List.of(satellite), 1199)
                .isEmpty());
        assertEquals(
            DiscoveryState.HIDDEN,
            CelestialKnowledgeService.asteroidFieldKnowledge(TEAM, BELT, profile)
                .entryFor(anchor.id())
                .detectionState());

        assertEquals(
            List.of(
                new AsteroidSatelliteScanService.ScanResult(BELT, anchor.id(), AsteroidSatelliteScanPass.DETECTION)),
            service.tick(TEAM, List.of(satellite), 1));
        AsteroidFieldKnowledge knowledge = CelestialKnowledgeService.asteroidFieldKnowledge(TEAM, BELT, profile);
        assertEquals(
            DiscoveryState.DISCOVERED,
            knowledge.entryFor(anchor.id())
                .detectionState());
        assertEquals(
            AsteroidOreKnowledgeState.UNKNOWN,
            knowledge.entryFor(anchor.id())
                .oreKnowledgeState());

        assertTrue(
            service.tick(TEAM, List.of(satellite), 2399)
                .isEmpty());
        assertEquals(
            List.of(
                new AsteroidSatelliteScanService.ScanResult(BELT, anchor.id(), AsteroidSatelliteScanPass.SIGNATURE)),
            service.tick(TEAM, List.of(satellite), 1));
        assertEquals(
            AsteroidOreKnowledgeState.SIGNATURE,
            knowledge.entryFor(anchor.id())
                .oreKnowledgeState());

        assertTrue(
            service.tick(TEAM, List.of(satellite), 4799)
                .isEmpty());
        assertEquals(
            List.of(new AsteroidSatelliteScanService.ScanResult(BELT, anchor.id(), AsteroidSatelliteScanPass.PROFILE)),
            service.tick(TEAM, List.of(satellite), 1));
        assertTrue(
            knowledge.entryFor(anchor.id())
                .oreKnowledgeState() != AsteroidOreKnowledgeState.UNKNOWN);
    }

    @Test
    void scanUsesInnerToOuterOrderAndFinishesSignaturePassBeforeProfilePass() {
        AsteroidFieldProfile profile = profile(3);
        AsteroidSatelliteScanService service = new AsteroidSatelliteScanService(
            bodyId -> bodyId == BELT ? Optional.of(profile) : Optional.empty());
        List<AsteroidFieldNode> innerToOuter = AsteroidFieldResolver.resolveAll(BELT, profile)
            .stream()
            .filter(node -> AsteroidFieldResolver.initialDetectionState(node) == DiscoveryState.HIDDEN)
            .sorted(AsteroidFieldScanOrder.innerToOuter())
            .toList();
        CelestialAsset satellite = prospectingSatellite(
            innerToOuter.get(2)
                .id());

        for (AsteroidFieldNode node : innerToOuter) {
            assertEquals(
                List.of(
                    new AsteroidSatelliteScanService.ScanResult(BELT, node.id(), AsteroidSatelliteScanPass.DETECTION)),
                service.tick(TEAM, List.of(satellite), AsteroidSatelliteScanPass.DETECTION.durationTicks()));
        }

        AsteroidFieldKnowledge knowledge = CelestialKnowledgeService.asteroidFieldKnowledge(TEAM, BELT, profile);
        assertEquals(
            List.of(
                new AsteroidSatelliteScanService.ScanResult(
                    BELT,
                    innerToOuter.get(0)
                        .id(),
                    AsteroidSatelliteScanPass.SIGNATURE)),
            service.tick(TEAM, List.of(satellite), AsteroidSatelliteScanPass.SIGNATURE.durationTicks()));
        assertEquals(
            List.of(
                new AsteroidSatelliteScanService.ScanResult(
                    BELT,
                    innerToOuter.get(1)
                        .id(),
                    AsteroidSatelliteScanPass.SIGNATURE)),
            service.tick(TEAM, List.of(satellite), AsteroidSatelliteScanPass.SIGNATURE.durationTicks()));
        assertEquals(
            AsteroidOreKnowledgeState.SIGNATURE,
            knowledge.entryFor(
                innerToOuter.get(0)
                    .id())
                .oreKnowledgeState());

        assertEquals(
            List.of(
                new AsteroidSatelliteScanService.ScanResult(
                    BELT,
                    innerToOuter.get(2)
                        .id(),
                    AsteroidSatelliteScanPass.SIGNATURE)),
            service.tick(TEAM, List.of(satellite), AsteroidSatelliteScanPass.SIGNATURE.durationTicks()));
        assertEquals(
            List.of(
                new AsteroidSatelliteScanService.ScanResult(
                    BELT,
                    innerToOuter.get(0)
                        .id(),
                    AsteroidSatelliteScanPass.PROFILE)),
            service.tick(TEAM, List.of(satellite), AsteroidSatelliteScanPass.PROFILE.durationTicks()));
    }

    @Test
    void scanScopeDoesNotAdvanceAsteroidsOutsideAnchorRadius() {
        AsteroidFieldProfile profile = com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI.get(BELT)
            .orElseThrow()
            .properties()
            .asteroidFieldProfile();
        List<AsteroidFieldNode> hiddenNodes = AsteroidFieldResolver.resolveAll(BELT, profile)
            .stream()
            .filter(node -> AsteroidFieldResolver.initialDetectionState(node) == DiscoveryState.HIDDEN)
            .toList();
        AsteroidFieldNode anchor = hiddenNodes.get(0);
        MinorCelestialBodyId outsideId = hiddenNodes.stream()
            .filter(node -> distance(profile, anchor, node) > profile.satelliteScanRadius())
            .findFirst()
            .orElseThrow()
            .id();
        AsteroidSatelliteScanService service = new AsteroidSatelliteScanService(
            bodyId -> bodyId == BELT ? Optional.of(profile) : Optional.empty());
        CelestialAsset satellite = prospectingSatellite(anchor.id());

        List<AsteroidSatelliteScanService.ScanResult> results = service.tick(
            TEAM,
            List.of(satellite),
            AsteroidSatelliteScanPass.DETECTION.durationTicks() + AsteroidSatelliteScanPass.SIGNATURE.durationTicks()
                + AsteroidSatelliteScanPass.PROFILE.durationTicks());

        AsteroidFieldKnowledge knowledge = CelestialKnowledgeService.asteroidFieldKnowledge(TEAM, BELT, profile);
        assertTrue(!results.isEmpty());
        assertTrue(
            results.stream()
                .noneMatch(
                    result -> result.asteroidId()
                        .equals(outsideId)));
        assertEquals(
            DiscoveryState.HIDDEN,
            knowledge.entryFor(outsideId)
                .detectionState());
    }

    @Test
    void restoredProgressContinuesPartiallyCompletedScan() {
        AsteroidFieldProfile profile = profile();
        AsteroidSatelliteScanService firstService = new AsteroidSatelliteScanService(
            bodyId -> bodyId == BELT ? Optional.of(profile) : Optional.empty());
        AsteroidFieldNode anchor = hiddenAnchor(profile);
        CelestialAsset satellite = prospectingSatellite(anchor.id());

        assertTrue(
            firstService.tick(TEAM, List.of(satellite), 600)
                .isEmpty());

        AsteroidSatelliteScanService restoredService = new AsteroidSatelliteScanService(
            bodyId -> bodyId == BELT ? Optional.of(profile) : Optional.empty());
        restoredService.restore(TEAM, firstService.snapshots(TEAM));

        assertEquals(
            List.of(
                new AsteroidSatelliteScanService.ScanResult(BELT, anchor.id(), AsteroidSatelliteScanPass.DETECTION)),
            restoredService.tick(TEAM, List.of(satellite), 600));
    }

    @Test
    void completedAnchorStopsFutureScanWorkAfterRestore() {
        AsteroidFieldProfile profile = profile();
        AsteroidSatelliteScanService firstService = new AsteroidSatelliteScanService(
            bodyId -> bodyId == BELT ? Optional.of(profile) : Optional.empty());
        AsteroidFieldNode anchor = hiddenAnchor(profile);
        CelestialAsset satellite = prospectingSatellite(anchor.id());

        firstService.tick(
            TEAM,
            List.of(satellite),
            AsteroidSatelliteScanPass.DETECTION.durationTicks() + AsteroidSatelliteScanPass.SIGNATURE.durationTicks()
                + AsteroidSatelliteScanPass.PROFILE.durationTicks());
        assertEquals(
            List.of(new AsteroidSatelliteScanCompletionSnapshot(BELT, anchor.id(), profile.generationVersion())),
            firstService.completionSnapshots(TEAM));

        AsteroidSatelliteScanService restoredService = new AsteroidSatelliteScanService(
            bodyId -> bodyId == BELT ? Optional.of(profile) : Optional.empty());
        restoredService.restoreCompletions(TEAM, firstService.completionSnapshots(TEAM));

        assertTrue(
            restoredService.tick(TEAM, List.of(satellite), AsteroidSatelliteScanPass.DETECTION.durationTicks())
                .isEmpty());
        assertTrue(
            restoredService.snapshots(TEAM)
                .isEmpty());
    }

    private static CelestialAsset prospectingSatellite(MinorCelestialBodyId asteroidId) {
        return CelestialAsset.create(
            CelestialObjectKey.minorBody(asteroidId),
            CelestialAsset.Kind.SATELLITE,
            Buildable.Status.OPERATIONAL,
            SatelliteKind.PROSPECTING);
    }

    private static AsteroidFieldProfile profile() {
        return profile(1);
    }

    private static AsteroidFieldProfile profile(int smallCount) {
        return profile(smallCount, 1000.0);
    }

    private static AsteroidFieldProfile profile(int smallCount, double satelliteScanRadius) {
        return AsteroidFieldProfile.builder()
            .seedSalt(0xA57E201DL)
            .generationVersion(1)
            .sizeCounts(0, 0, smallCount)
            .radialBand(8.0, 10.0)
            .satelliteScanRadius(satelliteScanRadius)
            .oreProfile(new AsteroidOreProfile("metallic", List.of("ore.mix.iron")))
            .authoredAsteroid(
                new AuthoredAsteroidDefinition(
                    1,
                    AsteroidNodeKind.LORE,
                    "scan_anchor",
                    "Scan Anchor",
                    AsteroidSizeClass.LARGE,
                    DiscoveryState.DISCOVERED,
                    AsteroidOreKnowledgeState.PROFILE,
                    0.0,
                    0.5,
                    null,
                    null))
            .build();
    }

    private static AsteroidFieldNode hiddenAnchor(AsteroidFieldProfile profile) {
        return AsteroidFieldResolver.resolveAll(BELT, profile)
            .stream()
            .filter(node -> AsteroidFieldResolver.initialDetectionState(node) == DiscoveryState.HIDDEN)
            .findFirst()
            .orElseThrow();
    }

    private static double distance(AsteroidFieldProfile profile, AsteroidFieldNode first, AsteroidFieldNode second) {
        double firstRadius = OrbitalMechanics.resolveAsteroidFieldRadius(profile, first);
        double firstAngle = Math.toRadians(first.angleOffsetDeg());
        double secondRadius = OrbitalMechanics.resolveAsteroidFieldRadius(profile, second);
        double secondAngle = Math.toRadians(second.angleOffsetDeg());
        double dx = Math.cos(firstAngle) * firstRadius - Math.cos(secondAngle) * secondRadius;
        double dy = Math.sin(firstAngle) * firstRadius - Math.sin(secondAngle) * secondRadius;
        return Math.sqrt(dx * dx + dy * dy);
    }
}
