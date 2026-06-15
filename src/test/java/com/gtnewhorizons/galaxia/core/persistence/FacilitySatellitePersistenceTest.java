package com.gtnewhorizons.galaxia.core.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteKind;

final class FacilitySatellitePersistenceTest {

    private static final UUID TEAM = UUID.fromString("00000000-0000-0000-0000-000000000123");

    @AfterEach
    void clearStores() {
        CelestialAssetStore.SERVER.clearInternal();
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

}
