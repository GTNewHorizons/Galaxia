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
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidDetectionState;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldKnowledgeService;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldResolver;
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
        AsteroidFieldKnowledgeService.clear();
    }

    @AfterEach
    void cleanStoresAfter() {
        CelestialAssetStore.SERVER.clearInternal();
        CelestialAssetStore.CLIENT.clearInternal();
        AsteroidFieldKnowledgeService.clear();
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
        assertEquals(
            1,
            CelestialAssetStore.getAssetsOnBody(asteroidId)
                .size());
        assertThrows(
            IllegalArgumentException.class,
            () -> AssetCreateRequestPacket
                .createFacility(asteroidId, "Asteroid Station", CelestialAsset.Kind.AUTOMATED_STATION, true)
                .apply(TEAM));
        assertEquals(
            1,
            CelestialAssetStore.getAssetsOnBody(asteroidId)
                .size());
    }

    @Test
    void asteroidCreateRequestRejectsHiddenAsteroidOutposts() {
        CelestialObjectKey hiddenAsteroidId = hiddenAsteroidId();

        assertThrows(
            IllegalArgumentException.class,
            () -> AssetCreateRequestPacket
                .createFacility(hiddenAsteroidId, "Hidden Outpost", CelestialAsset.Kind.AUTOMATED_OUTPOST, true)
                .apply(TEAM));
        assertEquals(
            0,
            CelestialAssetStore.getAssetsOnBody(hiddenAsteroidId)
                .size());
    }

    @Test
    void asteroidCreateRequestAllowsTeamDetectedHiddenAsteroidOutposts() {
        CelestialObjectKey hiddenAsteroidId = hiddenAsteroidId();
        CelestialObject belt = CelestialRegistry.get(CelestialObjectId.FROZEN_BELT)
            .orElseThrow();
        AsteroidFieldKnowledgeService.store()
            .getOrCreate(
                TEAM,
                CelestialObjectId.FROZEN_BELT,
                belt.properties()
                    .asteroidFieldProfile())
            .detect(hiddenAsteroidId.minorBodyId());

        AssetSyncPacket outpostSync = AssetCreateRequestPacket
            .createFacility(hiddenAsteroidId, "Detected Outpost", CelestialAsset.Kind.AUTOMATED_OUTPOST, true)
            .apply(TEAM);

        assertNotNull(outpostSync);
        assertEquals(
            1,
            CelestialAssetStore.getAssetsOnBody(hiddenAsteroidId)
                .size());
    }

    private static CelestialObjectKey hiddenAsteroidId() {
        CelestialObject belt = CelestialRegistry.get(CelestialObjectId.FROZEN_BELT)
            .orElseThrow();
        return AsteroidFieldResolver.resolveAll(
            CelestialObjectId.FROZEN_BELT,
            belt.properties()
                .asteroidFieldProfile())
            .stream()
            .filter(node -> AsteroidFieldResolver.initialDetectionState(node) == AsteroidDetectionState.HIDDEN)
            .map(node -> CelestialObjectKey.minorBody(node.id()))
            .findFirst()
            .orElseThrow();
    }
}
