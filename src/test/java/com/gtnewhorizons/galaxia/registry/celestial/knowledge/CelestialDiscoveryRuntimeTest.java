package com.gtnewhorizons.galaxia.registry.celestial.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialServerRuntime;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldKnowledgeStore;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteKind;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteNetworkService;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class CelestialDiscoveryRuntimeTest {

    private static final UUID TEAM = UUID.fromString("00000000-0000-0000-0000-000000000780");
    private static final CelestialDiscoveryScanScope SCOPE = new CelestialDiscoveryScanScope(
        CelestialObjectKey.registered(CelestialObjectId.MARS),
        0.25,
        1L);
    private CelestialServerRuntime runtime;

    @BeforeAll
    static void initCelestialRegistry() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();
    }

    @BeforeEach
    void createRuntime() {
        runtime = CelestialServerRuntime.create();
    }

    @AfterEach
    void clearState() {
        CelestialAssetStore.SERVER.clearInternal();
        SatelliteNetworkService.clear();
        AsteroidFieldKnowledgeStore.global()
            .clear();
    }

    @Test
    void satelliteTopologyClearDoesNotClearDiscoveryProgress() {
        addRuntimeProgress();
        AsteroidFieldKnowledgeStore.global()
            .getOrCreate(
                TEAM,
                CelestialObjectId.FROZEN_BELT,
                CelestialRegistry.get(CelestialObjectId.FROZEN_BELT)
                    .orElseThrow()
                    .properties()
                    .asteroidFieldProfile());

        SatelliteNetworkService.clear();

        assertEquals(50L, elapsedProgress());
        assertEquals(
            1,
            AsteroidFieldKnowledgeStore.global()
                .snapshots(TEAM)
                .size());
    }

    @Test
    void asteroidKnowledgeClearDoesNotClearDiscoveryProgress() {
        addRuntimeProgress();

        AsteroidFieldKnowledgeStore.global()
            .clear();

        assertEquals(50L, elapsedProgress());
    }

    @Test
    void explicitRuntimeClearClearsDiscoveryProgress() {
        addRuntimeProgress();

        runtime.discovery()
            .clear();

        assertTrue(
            runtime.scans()
                .snapshots(TEAM)
                .isEmpty());
    }

    @Test
    void assetStoreClearDoesNotClearSiblingRuntimeState() {
        addRuntimeProgress();
        AsteroidFieldKnowledgeStore.global()
            .getOrCreate(
                TEAM,
                CelestialObjectId.FROZEN_BELT,
                CelestialRegistry.get(CelestialObjectId.FROZEN_BELT)
                    .orElseThrow()
                    .properties()
                    .asteroidFieldProfile());
        CelestialAssetStore.registerAsset(
            TEAM,
            com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset.create(
                SCOPE.anchorKey(),
                com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset.Kind.SATELLITE,
                Buildable.Status.OPERATIONAL,
                SatelliteKind.COMMUNICATION));
        SatelliteNetworkService.rebuild(TEAM, 0.0D);

        CelestialAssetStore.SERVER.clearInternal();

        assertTrue(
            CelestialAssetStore.allAssets()
                .isEmpty());
        assertEquals(50L, elapsedProgress());
        assertEquals(
            1,
            AsteroidFieldKnowledgeStore.global()
                .snapshots(TEAM)
                .size());
        assertEquals(
            1,
            SatelliteNetworkService.current(TEAM)
                .revision());
    }

    @Test
    void topLevelRuntimeResetClearsAllServerState() {
        addRuntimeProgress();
        AsteroidFieldKnowledgeStore.global()
            .getOrCreate(
                TEAM,
                CelestialObjectId.FROZEN_BELT,
                CelestialRegistry.get(CelestialObjectId.FROZEN_BELT)
                    .orElseThrow()
                    .properties()
                    .asteroidFieldProfile());
        SatelliteNetworkService.rebuild(TEAM, 0.0D);

        runtime.reset();

        assertTrue(
            CelestialAssetStore.allAssets()
                .isEmpty());
        assertTrue(
            runtime.scans()
                .snapshots(TEAM)
                .isEmpty());
        assertTrue(
            AsteroidFieldKnowledgeStore.global()
                .snapshots(TEAM)
                .isEmpty());
        assertEquals(
            0,
            SatelliteNetworkService.current(TEAM)
                .revision());
    }

    @Test
    void separatelyConstructedRuntimesOwnIndependentScanState() {
        CelestialServerRuntime first = CelestialServerRuntime.create();
        CelestialServerRuntime second = CelestialServerRuntime.create();
        first.scans()
            .restore(TEAM, List.of(activeSnapshot(TEAM, 50L)));
        UUID otherTeam = UUID.fromString("00000000-0000-0000-0000-000000000781");
        second.scans()
            .restore(otherTeam, List.of(activeSnapshot(otherTeam, 75L)));

        first.discovery()
            .clear();

        assertTrue(
            first.scans()
                .snapshots(TEAM)
                .isEmpty());
        assertEquals(
            75L,
            second.scans()
                .snapshots(otherTeam)
                .get(0)
                .elapsedTicks());
    }

    private void addRuntimeProgress() {
        runtime.scans()
            .restore(TEAM, List.of(activeSnapshot(TEAM, 50L)));
    }

    private static CelestialDiscoveryScanSnapshot activeSnapshot(UUID teamId, long elapsedTicks) {
        return new CelestialDiscoveryScanSnapshot(
            teamId,
            SCOPE.anchorKey(),
            SCOPE.radius(),
            SCOPE.revision(),
            CelestialDiscoveryCapability.PROSPECTING,
            CelestialDiscoveryScanSnapshot.Status.ACTIVE,
            SCOPE.anchorKey(),
            CelestialDiscoveryStep.DETECTION,
            elapsedTicks);
    }

    private long elapsedProgress() {
        return runtime.scans()
            .snapshots(TEAM)
            .get(0)
            .elapsedTicks();
    }
}
