package com.gtnewhorizons.galaxia.core.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.MinorCelestialBodyId;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteKind;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class AssetCreateRequestPacketTest {

    private static final UUID TEAM = UUID.randomUUID();

    @BeforeAll
    static void bootstrapRegistry() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();
    }

    @BeforeEach
    void cleanStores() {
        CelestialAssetStore.SERVER.clearInternal();
        CelestialAssetStore.CLIENT.clearInternal();
    }

    @AfterEach
    void cleanStoresAfter() {
        CelestialAssetStore.SERVER.clearInternal();
        CelestialAssetStore.CLIENT.clearInternal();
    }

    @Test
    void createRequestRegistersSatelliteAssets() {
        AssetCreateRequestPacket request = AssetCreateRequestPacket
            .createSatellite(CelestialObjectId.MARS, SatelliteKind.COMMUNICATION, true);

        AssetSyncPacket sync = request.apply(TEAM);

        assertNotNull(sync);
        assertEquals(
            1,
            CelestialAssetStore.SERVER.satelliteCount(TEAM, CelestialObjectId.MARS, SatelliteKind.COMMUNICATION));
    }

    @Test
    void asteroidCreateRequestAllowsOutpostsAndRejectsAutomatedStations() {
        CelestialObjectKey asteroidId = CelestialObjectKey
            .minorBody(new MinorCelestialBodyId(CelestialObjectId.FROZEN_BELT, 0));

        AssetSyncPacket outpostSync = AssetCreateRequestPacket
            .createFacility(asteroidId, "Asteroid Outpost", CelestialAsset.Kind.AUTOMATED_OUTPOST, true)
            .apply(TEAM);

        assertNotNull(outpostSync);
        assertEquals(1, CelestialAssetStore.getAssetsOnBody(asteroidId).size());
        assertThrows(
            IllegalArgumentException.class,
            () -> AssetCreateRequestPacket
                .createFacility(asteroidId, "Asteroid Station", CelestialAsset.Kind.AUTOMATED_STATION, true)
                .apply(TEAM));
        assertEquals(1, CelestialAssetStore.getAssetsOnBody(asteroidId).size());
    }
}
