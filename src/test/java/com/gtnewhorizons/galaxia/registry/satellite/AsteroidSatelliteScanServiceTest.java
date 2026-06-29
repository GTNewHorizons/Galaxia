package com.gtnewhorizons.galaxia.registry.satellite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidDetectionState;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldKnowledge;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldKnowledgeStore;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldNode;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldProfile;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldResolver;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldScanOrder;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidOreKnowledgeState;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidOreProfile;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.MinorCelestialBodyId;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class AsteroidSatelliteScanServiceTest {

    private static final UUID TEAM = UUID.fromString("00000000-0000-0000-0000-000000000241");
    private static final CelestialObjectId BELT = CelestialObjectId.FROZEN_BELT;

    @BeforeAll
    static void initCelestialRegistry() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();
    }

    @Test
    void prospectingSatelliteAnchoredOnAsteroidRunsDetectionSignatureAndProfilePasses() {
        AsteroidFieldProfile profile = profile();
        AsteroidFieldKnowledgeStore knowledgeStore = new AsteroidFieldKnowledgeStore();
        AsteroidSatelliteScanService service = new AsteroidSatelliteScanService(
            knowledgeStore,
            bodyId -> bodyId == BELT ? Optional.of(profile) : Optional.empty());
        AsteroidFieldNode anchor = AsteroidFieldResolver.resolveNode(BELT, profile, 0);
        CelestialAsset satellite = prospectingSatellite(anchor.id());

        assertTrue(
            service.tick(TEAM, List.of(satellite), 1199)
                .isEmpty());
        assertEquals(
            AsteroidDetectionState.HIDDEN,
            knowledgeStore.getOrCreate(TEAM, BELT, profile)
                .entryFor(anchor.id())
                .detectionState());

        assertEquals(
            List.of(
                new AsteroidSatelliteScanService.ScanResult(BELT, anchor.id(), AsteroidSatelliteScanPass.DETECTION)),
            service.tick(TEAM, List.of(satellite), 1));
        AsteroidFieldKnowledge knowledge = knowledgeStore.get(TEAM, BELT)
            .orElseThrow();
        assertEquals(
            AsteroidDetectionState.DETECTED,
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
        assertEquals(
            AsteroidOreKnowledgeState.PROFILE,
            knowledge.entryFor(anchor.id())
                .oreKnowledgeState());
    }

    @Test
    void scanUsesInnerToOuterOrderAndFinishesSignaturePassBeforeProfilePass() {
        AsteroidFieldProfile profile = profile(3);
        AsteroidFieldKnowledgeStore knowledgeStore = new AsteroidFieldKnowledgeStore();
        AsteroidSatelliteScanService service = new AsteroidSatelliteScanService(
            knowledgeStore,
            bodyId -> bodyId == BELT ? Optional.of(profile) : Optional.empty());
        List<AsteroidFieldNode> innerToOuter = AsteroidFieldResolver.resolveAll(BELT, profile)
            .stream()
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

        AsteroidFieldKnowledge knowledge = knowledgeStore.get(TEAM, BELT)
            .orElseThrow();
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
        AsteroidFieldProfile profile = profile(3, 0.0);
        AsteroidFieldKnowledgeStore knowledgeStore = new AsteroidFieldKnowledgeStore();
        AsteroidSatelliteScanService service = new AsteroidSatelliteScanService(
            knowledgeStore,
            bodyId -> bodyId == BELT ? Optional.of(profile) : Optional.empty());
        AsteroidFieldNode anchor = AsteroidFieldResolver.resolveNode(BELT, profile, 0);
        CelestialAsset satellite = prospectingSatellite(anchor.id());

        service.tick(
            TEAM,
            List.of(satellite),
            AsteroidSatelliteScanPass.DETECTION.durationTicks() + AsteroidSatelliteScanPass.SIGNATURE.durationTicks()
                + AsteroidSatelliteScanPass.PROFILE.durationTicks());

        AsteroidFieldKnowledge knowledge = knowledgeStore.get(TEAM, BELT)
            .orElseThrow();
        assertEquals(
            AsteroidOreKnowledgeState.PROFILE,
            knowledge.entryFor(anchor.id())
                .oreKnowledgeState());
        for (AsteroidFieldNode node : knowledge.nodes()) {
            if (node.id()
                .equals(anchor.id())) continue;
            assertEquals(
                AsteroidDetectionState.HIDDEN,
                knowledge.entryFor(node.id())
                    .detectionState());
        }
    }

    @Test
    void restoredProgressContinuesPartiallyCompletedScan() {
        AsteroidFieldProfile profile = profile();
        AsteroidFieldKnowledgeStore firstKnowledgeStore = new AsteroidFieldKnowledgeStore();
        AsteroidSatelliteScanService firstService = new AsteroidSatelliteScanService(
            firstKnowledgeStore,
            bodyId -> bodyId == BELT ? Optional.of(profile) : Optional.empty());
        AsteroidFieldNode anchor = AsteroidFieldResolver.resolveNode(BELT, profile, 0);
        CelestialAsset satellite = prospectingSatellite(anchor.id());

        assertTrue(
            firstService.tick(TEAM, List.of(satellite), 600)
                .isEmpty());

        AsteroidFieldKnowledgeStore restoredKnowledgeStore = new AsteroidFieldKnowledgeStore();
        AsteroidSatelliteScanService restoredService = new AsteroidSatelliteScanService(
            restoredKnowledgeStore,
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
        AsteroidFieldKnowledgeStore firstKnowledgeStore = new AsteroidFieldKnowledgeStore();
        AsteroidSatelliteScanService firstService = new AsteroidSatelliteScanService(
            firstKnowledgeStore,
            bodyId -> bodyId == BELT ? Optional.of(profile) : Optional.empty());
        AsteroidFieldNode anchor = AsteroidFieldResolver.resolveNode(BELT, profile, 0);
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
            new AsteroidFieldKnowledgeStore(),
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
            .oreProfile(new AsteroidOreProfile("metallic", 1.0, List.of("ore.mix.iron")))
            .build();
    }
}
