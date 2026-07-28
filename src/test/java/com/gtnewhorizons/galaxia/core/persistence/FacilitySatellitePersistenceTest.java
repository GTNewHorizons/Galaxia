package com.gtnewhorizons.galaxia.core.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialServerRuntime;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldKnowledgeSnapshot;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldKnowledgeStore;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteKind;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteNetworkService;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class FacilitySatellitePersistenceTest {

    private static final UUID TEAM = UUID.fromString("00000000-0000-0000-0000-000000000123");

    @BeforeAll
    static void bootstrapRegistry() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();
        GalaxiaTestBootstrap.ensureFacilityModules();
    }

    @AfterEach
    void clearStores() {
        CelestialAssetStore.SERVER.clearInternal();
        SatelliteNetworkService.clear();
    }

    @Test
    void satelliteCountsAreBackedByBodyIndexedAssets() {
        CelestialAssetStore.SERVER.setSatelliteCount(TEAM, CelestialObjectId.MARS, SatelliteKind.COMMUNICATION, 3);
        CelestialAssetStore.SERVER.setSatelliteCount(TEAM, CelestialObjectId.MARS, SatelliteKind.PROSPECTING, 2);

        long satelliteAssets = CelestialAssetStore.getAssetsOnBody(CelestialObjectId.MARS)
            .stream()
            .map(CelestialAssetStore::findAsset)
            .filter(asset -> asset != null && asset.kind == CelestialAsset.Kind.SATELLITE)
            .count();

        assertEquals(5, satelliteAssets);
    }

    @Test
    void satelliteCountsRoundTripThroughStarmapPersistence(@TempDir Path tempDir) {
        FacilityPersistenceManager manager = new FacilityPersistenceManager(CelestialServerRuntime.create());
        manager.loadFromSaveDirectory(tempDir.toFile());
        CelestialAssetStore.SERVER.setSatelliteCount(TEAM, CelestialObjectId.MARS, SatelliteKind.COMMUNICATION, 3);
        CelestialAssetStore.SERVER.setSatelliteCount(TEAM, CelestialObjectId.MARS, SatelliteKind.PROSPECTING, 2);
        CelestialAssetStore.SERVER.setSatelliteCount(TEAM, CelestialObjectId.MOON, SatelliteKind.COMMUNICATION, 5);
        manager.saveToSaveDirectory(tempDir.toFile());
        CelestialAssetStore.SERVER.clearInternal();

        FacilityPersistenceManager reloaded = new FacilityPersistenceManager(CelestialServerRuntime.create());
        reloaded.loadFromSaveDirectory(tempDir.toFile());

        assertEquals(
            3,
            CelestialAssetStore.SERVER.satelliteCount(TEAM, CelestialObjectId.MARS, SatelliteKind.COMMUNICATION));
        assertEquals(
            2,
            CelestialAssetStore.SERVER.satelliteCount(TEAM, CelestialObjectId.MARS, SatelliteKind.PROSPECTING));
        assertEquals(
            5,
            CelestialAssetStore.SERVER.satelliteCount(TEAM, CelestialObjectId.MOON, SatelliteKind.COMMUNICATION));
    }

    @Test
    void deletedSatelliteKindDoesNotRoundTripAsPhantomCount(@TempDir Path tempDir) {
        FacilityPersistenceManager manager = new FacilityPersistenceManager(CelestialServerRuntime.create());
        manager.loadFromSaveDirectory(tempDir.toFile());
        CelestialAssetStore.SERVER.setSatelliteCount(TEAM, CelestialObjectId.MARS, SatelliteKind.COMMUNICATION, 3);
        CelestialAssetStore.SERVER.setSatelliteCount(TEAM, CelestialObjectId.MARS, SatelliteKind.PROSPECTING, 2);
        CelestialAssetStore.SERVER.deleteSatellites(TEAM, CelestialObjectId.MARS, SatelliteKind.COMMUNICATION);
        manager.saveToSaveDirectory(tempDir.toFile());
        CelestialAssetStore.SERVER.clearInternal();

        FacilityPersistenceManager reloaded = new FacilityPersistenceManager(CelestialServerRuntime.create());
        reloaded.loadFromSaveDirectory(tempDir.toFile());

        assertEquals(
            0,
            CelestialAssetStore.SERVER.satelliteCount(TEAM, CelestialObjectId.MARS, SatelliteKind.COMMUNICATION));
        assertEquals(
            2,
            CelestialAssetStore.SERVER.satelliteCount(TEAM, CelestialObjectId.MARS, SatelliteKind.PROSPECTING));
    }

    @Test
    void asteroidKnowledgeRoundTripsThroughStarmapPersistence(@TempDir Path tempDir) {
        FacilityPersistenceManager manager = new FacilityPersistenceManager(CelestialServerRuntime.create());
        manager.loadFromSaveDirectory(tempDir.toFile());
        AsteroidFieldKnowledgeStore.global()
            .getOrCreate(
                TEAM,
                CelestialObjectId.FROZEN_BELT,
                com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI.get(CelestialObjectId.FROZEN_BELT)
                    .orElseThrow()
                    .properties()
                    .asteroidFieldProfile());
        List<AsteroidFieldKnowledgeSnapshot> saved = AsteroidFieldKnowledgeStore.global()
            .snapshots(TEAM);
        assertFalse(saved.isEmpty());

        manager.saveToSaveDirectory(tempDir.toFile());
        CelestialAssetStore.SERVER.clearInternal();
        SatelliteNetworkService.clear();

        FacilityPersistenceManager reloaded = new FacilityPersistenceManager(CelestialServerRuntime.create());
        reloaded.loadFromSaveDirectory(tempDir.toFile());

        assertEquals(
            saved,
            AsteroidFieldKnowledgeStore.global()
                .snapshots(TEAM));
    }

    @Test
    void deletingSatelliteAmountOnlyRemovesExistingSatellites() {
        CelestialAssetStore.SERVER.setSatelliteCount(TEAM, CelestialObjectId.MARS, SatelliteKind.COMMUNICATION, 3);

        CelestialAssetStore.SERVER.deleteSatelliteAmount(TEAM, CelestialObjectId.MARS, SatelliteKind.COMMUNICATION, 2);
        CelestialAssetStore.SERVER.deleteSatelliteAmount(TEAM, CelestialObjectId.MARS, SatelliteKind.COMMUNICATION, 10);

        assertEquals(
            0,
            CelestialAssetStore.SERVER.satelliteCount(TEAM, CelestialObjectId.MARS, SatelliteKind.COMMUNICATION));
    }

}
