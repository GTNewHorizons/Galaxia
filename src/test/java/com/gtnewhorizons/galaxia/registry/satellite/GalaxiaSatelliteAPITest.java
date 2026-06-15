package com.gtnewhorizons.galaxia.registry.satellite;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.api.GalaxiaSatelliteAPI;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;

final class GalaxiaSatelliteAPITest {

    private static final UUID TEAM_A = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID TEAM_B = UUID.fromString("00000000-0000-0000-0000-0000000000b2");

    @BeforeEach
    void resetStore() {
        CelestialAssetStore.SERVER.clearInternal();
    }

    @Test
    void apiReadsTeamScopedCountsAndEffects() {
        CelestialAssetStore.SERVER.setSatelliteCount(TEAM_A, CelestialObjectId.MARS, SatelliteKind.COMMUNICATION, 3);
        CelestialAssetStore.SERVER.setSatelliteCount(TEAM_A, CelestialObjectId.MARS, SatelliteKind.PROSPECTING, 2);
        CelestialAssetStore.SERVER.setSatelliteCount(TEAM_B, CelestialObjectId.MARS, SatelliteKind.COMMUNICATION, 7);

        assertEquals(3, GalaxiaSatelliteAPI.count(TEAM_A, CelestialObjectId.MARS, SatelliteKind.COMMUNICATION));
        assertEquals(7, GalaxiaSatelliteAPI.count(TEAM_B, CelestialObjectId.MARS, SatelliteKind.COMMUNICATION));
        assertEquals(3L, GalaxiaSatelliteAPI.bandwidth(TEAM_A, CelestialObjectId.MARS));
        assertEquals(0.20D, GalaxiaSatelliteAPI.miningSpeedBonus(TEAM_A, CelestialObjectId.MARS));
    }

    @Test
    void deleteAllClearsOnlySelectedKind() {
        CelestialAssetStore.SERVER.setSatelliteCount(TEAM_A, CelestialObjectId.MARS, SatelliteKind.COMMUNICATION, 4);
        CelestialAssetStore.SERVER.setSatelliteCount(TEAM_A, CelestialObjectId.MARS, SatelliteKind.PROSPECTING, 5);

        CelestialAssetStore.SERVER.deleteSatellites(TEAM_A, CelestialObjectId.MARS, SatelliteKind.COMMUNICATION);

        assertEquals(0, GalaxiaSatelliteAPI.count(TEAM_A, CelestialObjectId.MARS, SatelliteKind.COMMUNICATION));
        assertEquals(5, GalaxiaSatelliteAPI.count(TEAM_A, CelestialObjectId.MARS, SatelliteKind.PROSPECTING));
    }
}
