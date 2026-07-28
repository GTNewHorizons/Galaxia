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
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldResolver;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeFacts;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeFacts.DiscoveryState;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeService;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
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
            CelestialAssetStore.SERVER.satelliteCount(
                TEAM,
                CelestialObjectKey.registered(CelestialObjectId.MARS),
                SatelliteKind.COMMUNICATION));
    }

    @Test
    void operationalRequestIsHonouredOnlyForCreativeOperators() {
        AssetCreateRequestPacket request = AssetCreateRequestPacket
            .createFacility(CelestialObjectId.MARS, "Skip Construction", CelestialAsset.Kind.AUTOMATED_OUTPOST, true);

        request.apply(TEAM, false);

        assertEquals(
            Buildable.Status.CONSTRUCTION_SITE,
            onlyMarsAsset().status(),
            "a plain client asking for an operational asset must still start a construction site");

        CelestialAssetStore.SERVER.clearInternal();
        request.apply(TEAM, true);

        assertEquals(Buildable.Status.OPERATIONAL, onlyMarsAsset().status());
    }

    @Test
    void asteroidCreateRequestAllowsOutpostsAndRejectsAutomatedStations() {
        CelestialObjectKey asteroidKey = detectedAsteroidKey();

        AssetSyncPacket outpostSync = AssetCreateRequestPacket
            .createFacility(asteroidKey, "Asteroid Outpost", CelestialAsset.Kind.AUTOMATED_OUTPOST, true)
            .apply(TEAM);

        assertNotNull(outpostSync);
        assertEquals(
            1,
            CelestialAssetStore.getAssetsOnBody(asteroidKey)
                .size());
        assertThrows(
            IllegalArgumentException.class,
            () -> AssetCreateRequestPacket
                .createFacility(asteroidKey, "Asteroid Station", CelestialAsset.Kind.AUTOMATED_STATION, true)
                .apply(TEAM));
        assertEquals(
            1,
            CelestialAssetStore.getAssetsOnBody(asteroidKey)
                .size());
    }

    @Test
    void asteroidCreateRequestRejectsHiddenAsteroidOutposts() {
        CelestialObjectKey hiddenAsteroidKey = hiddenAsteroidKey();

        assertThrows(
            IllegalArgumentException.class,
            () -> AssetCreateRequestPacket
                .createFacility(hiddenAsteroidKey, "Hidden Outpost", CelestialAsset.Kind.AUTOMATED_OUTPOST, true)
                .apply(TEAM));
        assertEquals(
            0,
            CelestialAssetStore.getAssetsOnBody(hiddenAsteroidKey)
                .size());
    }

    @Test
    void asteroidCreateRequestRejectsHiddenAsteroidSatellites() {
        CelestialObjectKey hiddenAsteroidKey = hiddenAsteroidKey();

        assertThrows(
            IllegalArgumentException.class,
            () -> AssetCreateRequestPacket.createSatellite(hiddenAsteroidKey, SatelliteKind.PROSPECTING, true)
                .apply(TEAM));
        assertEquals(
            0,
            CelestialAssetStore.getAssetsOnBody(hiddenAsteroidKey)
                .size());
    }

    @Test
    void asteroidCreateRequestAllowsCommunicationSatellites() {
        CelestialObjectKey asteroidKey = detectedAsteroidKey();

        AssetSyncPacket sync = AssetCreateRequestPacket.createSatellite(asteroidKey, SatelliteKind.COMMUNICATION, true)
            .apply(TEAM);

        assertNotNull(sync);
        assertEquals(
            1,
            CelestialAssetStore.getAssetsOnBody(asteroidKey)
                .size());
    }

    @Test
    void asteroidCreateRequestAllowsTeamDetectedHiddenAsteroidOutposts() {
        CelestialObjectKey hiddenAsteroidKey = hiddenAsteroidKey();
        CelestialKnowledgeService.putFacts(TEAM, hiddenAsteroidKey, CelestialKnowledgeFacts.discoveredUnknown());

        AssetSyncPacket outpostSync = AssetCreateRequestPacket
            .createFacility(hiddenAsteroidKey, "Detected Outpost", CelestialAsset.Kind.AUTOMATED_OUTPOST, true)
            .apply(TEAM);

        assertNotNull(outpostSync);
        assertEquals(
            1,
            CelestialAssetStore.getAssetsOnBody(hiddenAsteroidKey)
                .size());
    }

    private static CelestialObjectKey hiddenAsteroidKey() {
        return asteroidIdWithDetectionState(DiscoveryState.HIDDEN);
    }

    private static CelestialObjectKey detectedAsteroidKey() {
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
            .filter(node -> node.initialDetectionState() == detectionState)
            .map(node -> CelestialObjectKey.minorBody(node.id()))
            .findFirst()
            .orElseThrow();
    }

    private static CelestialAsset onlyMarsAsset() {
        return CelestialAssetStore.SERVER.getStateInternal(TEAM, CelestialObjectKey.registered(CelestialObjectId.MARS))
            .get(0);
    }
}
