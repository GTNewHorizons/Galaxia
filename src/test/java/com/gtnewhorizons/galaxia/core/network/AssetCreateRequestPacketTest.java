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
import com.gtnewhorizons.galaxia.registry.celestial.CelestialServerRuntime;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldDiscoveryWork;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldKnowledgeStore;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldResolver;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldScanContext;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldScanOrder;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryStep;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.DiscoveryState;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteKind;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class AssetCreateRequestPacketTest {

    private static final UUID TEAM = UUID.randomUUID();
    private static final CelestialServerRuntime RUNTIME = CelestialServerRuntime.create();

    @BeforeAll
    static void bootstrapRegistry() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();
    }

    @BeforeEach
    void cleanStores() {
        CelestialAssetStore.SERVER.clearInternal();
        CelestialAssetStore.CLIENT.clearInternal();
        RUNTIME.reset();
    }

    @AfterEach
    void cleanStoresAfter() {
        CelestialAssetStore.SERVER.clearInternal();
        CelestialAssetStore.CLIENT.clearInternal();
        RUNTIME.reset();
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
        CelestialObjectKey asteroidId = detectedAsteroidId();

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
    void asteroidCreateRequestRejectsHiddenAsteroidSatellites() {
        CelestialObjectKey hiddenAsteroidId = hiddenAsteroidId();

        assertThrows(
            IllegalArgumentException.class,
            () -> AssetCreateRequestPacket.createSatellite(hiddenAsteroidId, SatelliteKind.PROSPECTING, true)
                .apply(TEAM));
        assertEquals(
            0,
            CelestialAssetStore.getAssetsOnBody(hiddenAsteroidId)
                .size());
    }

    @Test
    void asteroidCreateRequestAllowsCommunicationSatellites() {
        CelestialObjectKey asteroidId = detectedAsteroidId();

        AssetSyncPacket sync = AssetCreateRequestPacket.createSatellite(asteroidId, SatelliteKind.COMMUNICATION, true)
            .apply(TEAM);

        assertNotNull(sync);
        assertEquals(
            1,
            CelestialAssetStore.getAssetsOnBody(asteroidId)
                .size());
    }

    @Test
    void asteroidCreateRequestAllowsTeamDetectedHiddenAsteroidOutposts() {
        CelestialObjectKey hiddenAsteroidId = hiddenAsteroidId();
        CelestialObject belt = CelestialRegistry.get(CelestialObjectId.FROZEN_BELT)
            .orElseThrow();
        var knowledge = AsteroidFieldKnowledgeStore.global()
            .getOrCreate(
                TEAM,
                CelestialObjectId.FROZEN_BELT,
                belt.properties()
                    .asteroidFieldProfile());
        var context = new AsteroidFieldScanContext(node -> true, AsteroidFieldScanOrder.byIndex());
        knowledge.revealDiscovery(
            new AsteroidFieldDiscoveryWork(hiddenAsteroidId, CelestialDiscoveryStep.DETECTION),
            context);

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
        return asteroidIdWithDetectionState(DiscoveryState.HIDDEN);
    }

    private static CelestialObjectKey detectedAsteroidId() {
        return asteroidIdWithDetectionState(DiscoveryState.DISCOVERED);
    }

    private static CelestialObjectKey asteroidIdWithDetectionState(DiscoveryState detectionState) {
        CelestialObject belt = CelestialRegistry.get(CelestialObjectId.FROZEN_BELT)
            .orElseThrow();
        return AsteroidFieldResolver.resolveAll(
            CelestialObjectId.FROZEN_BELT,
            belt.properties()
                .asteroidFieldProfile())
            .stream()
            .filter(node -> AsteroidFieldResolver.initialDetectionState(node) == detectionState)
            .map(node -> CelestialObjectKey.minorBody(node.id()))
            .findFirst()
            .orElseThrow();
    }
}
