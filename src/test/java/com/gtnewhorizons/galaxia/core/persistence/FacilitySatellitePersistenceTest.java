package com.gtnewhorizons.galaxia.core.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.satellite.PlanetarySatelliteStore;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteKind;

final class FacilitySatellitePersistenceTest {

    private static final UUID TEAM = UUID.fromString("00000000-0000-0000-0000-000000000123");

    @AfterEach
    void clearStores() {
        PlanetarySatelliteStore.SERVER.clear();
    }

    @Test
    void satelliteCountsRoundTripThroughStarmapPersistence(@TempDir Path tempDir) {
        FacilityPersistenceManager manager = new FacilityPersistenceManager();
        manager.loadFromSaveDirectory(tempDir.toFile());
        PlanetarySatelliteStore.SERVER.set(TEAM, CelestialObjectId.MARS, SatelliteKind.COMMUNICATION, 3);
        PlanetarySatelliteStore.SERVER.set(TEAM, CelestialObjectId.MARS, SatelliteKind.PROSPECTING, 2);
        PlanetarySatelliteStore.SERVER.set(TEAM, CelestialObjectId.MOON, SatelliteKind.COMMUNICATION, 5);
        manager.saveToSaveDirectory(tempDir.toFile());
        PlanetarySatelliteStore.SERVER.clear();

        FacilityPersistenceManager reloaded = new FacilityPersistenceManager();
        reloaded.loadFromSaveDirectory(tempDir.toFile());

        assertEquals(
            3,
            PlanetarySatelliteStore.SERVER.count(TEAM, CelestialObjectId.MARS, SatelliteKind.COMMUNICATION));
        assertEquals(2, PlanetarySatelliteStore.SERVER.count(TEAM, CelestialObjectId.MARS, SatelliteKind.PROSPECTING));
        assertEquals(
            5,
            PlanetarySatelliteStore.SERVER.count(TEAM, CelestialObjectId.MOON, SatelliteKind.COMMUNICATION));
    }

    @Test
    void deletedSatelliteKindDoesNotRoundTripAsPhantomCount(@TempDir Path tempDir) {
        FacilityPersistenceManager manager = new FacilityPersistenceManager();
        manager.loadFromSaveDirectory(tempDir.toFile());
        PlanetarySatelliteStore.SERVER.set(TEAM, CelestialObjectId.MARS, SatelliteKind.COMMUNICATION, 3);
        PlanetarySatelliteStore.SERVER.set(TEAM, CelestialObjectId.MARS, SatelliteKind.PROSPECTING, 2);
        PlanetarySatelliteStore.SERVER.deleteAll(TEAM, CelestialObjectId.MARS, SatelliteKind.COMMUNICATION);
        manager.saveToSaveDirectory(tempDir.toFile());
        PlanetarySatelliteStore.SERVER.clear();

        FacilityPersistenceManager reloaded = new FacilityPersistenceManager();
        reloaded.loadFromSaveDirectory(tempDir.toFile());

        assertEquals(
            0,
            PlanetarySatelliteStore.SERVER.count(TEAM, CelestialObjectId.MARS, SatelliteKind.COMMUNICATION));
        assertEquals(2, PlanetarySatelliteStore.SERVER.count(TEAM, CelestialObjectId.MARS, SatelliteKind.PROSPECTING));
    }
}
