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
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldKnowledgeSnapshot;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleDebugDataGenerator;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteDataType;
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
        FacilityPersistenceManager manager = new FacilityPersistenceManager();
        manager.loadFromSaveDirectory(tempDir.toFile());
        CelestialAssetStore.SERVER.setSatelliteCount(TEAM, CelestialObjectId.MARS, SatelliteKind.COMMUNICATION, 3);
        CelestialAssetStore.SERVER.setSatelliteCount(TEAM, CelestialObjectId.MARS, SatelliteKind.PROSPECTING, 2);
        CelestialAssetStore.SERVER.setSatelliteCount(TEAM, CelestialObjectId.MOON, SatelliteKind.COMMUNICATION, 5);
        manager.saveToSaveDirectory(tempDir.toFile());
        CelestialAssetStore.SERVER.clearInternal();

        FacilityPersistenceManager reloaded = new FacilityPersistenceManager();
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
        FacilityPersistenceManager manager = new FacilityPersistenceManager();
        manager.loadFromSaveDirectory(tempDir.toFile());
        CelestialAssetStore.SERVER.setSatelliteCount(TEAM, CelestialObjectId.MARS, SatelliteKind.COMMUNICATION, 3);
        CelestialAssetStore.SERVER.setSatelliteCount(TEAM, CelestialObjectId.MARS, SatelliteKind.PROSPECTING, 2);
        CelestialAssetStore.SERVER.deleteSatellites(TEAM, CelestialObjectId.MARS, SatelliteKind.COMMUNICATION);
        manager.saveToSaveDirectory(tempDir.toFile());
        CelestialAssetStore.SERVER.clearInternal();

        FacilityPersistenceManager reloaded = new FacilityPersistenceManager();
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
        FacilityPersistenceManager manager = new FacilityPersistenceManager();
        manager.loadFromSaveDirectory(tempDir.toFile());
        AutomatedFacility facility = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.FROZEN_BELT,
            CelestialAsset.Kind.AUTOMATED_OUTPOST,
            Buildable.Status.OPERATIONAL);
        CelestialAssetStore.registerAsset(TEAM, facility);
        ModuleDebugDataGenerator producer = addDebugModule(facility);
        ModuleDebugDataGenerator consumer = addDebugModule(facility);
        producer.configure(ModuleDebugDataGenerator.Config.produce(SatelliteDataType.PROSPECTING, 10L, 1));
        consumer.configure(ModuleDebugDataGenerator.Config.consume(SatelliteDataType.PROSPECTING, 10L, 1, null));
        SatelliteNetworkService.refreshFacilityEndpoints(facility);
        SatelliteNetworkService.tickDataJobs();
        List<AsteroidFieldKnowledgeSnapshot> saved = SatelliteNetworkService.asteroidKnowledgeSnapshots(TEAM);
        assertFalse(saved.isEmpty());

        manager.saveToSaveDirectory(tempDir.toFile());
        CelestialAssetStore.SERVER.clearInternal();
        SatelliteNetworkService.clear();

        FacilityPersistenceManager reloaded = new FacilityPersistenceManager();
        reloaded.loadFromSaveDirectory(tempDir.toFile());

        assertEquals(saved, SatelliteNetworkService.asteroidKnowledgeSnapshots(TEAM));
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

    private static ModuleDebugDataGenerator addDebugModule(AutomatedFacility facility) {
        ModuleInstance module = FacilityModuleKind.DEBUG_DATA_GENERATOR.create(
            StationTileCoord.of(
                facility.modules()
                    .size(),
                0),
            ModuleShape.SINGLE,
            ModuleTier.HV);
        module.updateStatus(Buildable.Status.OPERATIONAL);
        facility.addModule(module);
        return (ModuleDebugDataGenerator) module.component();
    }
}
