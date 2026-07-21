package com.gtnewhorizons.galaxia.core.network;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldClientCatalogState;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.MinorCelestialBodyId;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryClientState;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryScanSnapshot;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryScanSnapshot.CelestialDiscoveryCapability;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryScanSnapshot.CelestialDiscoveryScanScope;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeClientState;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeFacts;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeFacts.CelestialResourceKnowledgeState;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeFacts.DiscoveryState;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteNetworkClientState;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteNetworkState;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class ClientStateLifecycleTest {

    private static final UUID TEAM = new UUID(1L, 2L);
    private static final CelestialObjectKey ASTEROID_KEY = CelestialObjectKey
        .minorBody(new MinorCelestialBodyId(CelestialObjectId.FROZEN_BELT, 3));

    @BeforeAll
    static void init() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();
    }

    @AfterEach
    void clearState() {
        CelestialAssetStore.CLIENT.clearInternal();
        CelestialKnowledgeClientState.clear();
        AsteroidFieldClientCatalogState.clear();
        CelestialDiscoveryClientState.clear();
        SatelliteNetworkClientState.clear();
    }

    @Test
    void clearAllClearsEveryClientSideSyncStore() {
        CelestialAsset asset = CelestialAsset
            .create(CelestialObjectId.MARS, CelestialAsset.Kind.AUTOMATED_OUTPOST, Buildable.Status.OPERATIONAL);
        CelestialAssetStore.CLIENT.registerAssetInternal(TEAM, asset);
        CelestialKnowledgeClientState.apply(
            Map.of(
                ASTEROID_KEY,
                CelestialKnowledgeFacts.of(DiscoveryState.DISCOVERED, CelestialResourceKnowledgeState.PROFILE)));
        CelestialDiscoveryClientState.update(
            List.of(
                CelestialDiscoveryScanSnapshot.complete(
                    TEAM,
                    new CelestialDiscoveryScanScope(ASTEROID_KEY, 0.5, 4),
                    CelestialDiscoveryCapability.PROSPECTING)));
        SatelliteNetworkClientState.update(
            new SatelliteNetworkState(
                TEAM,
                5,
                Map.of(
                    CelestialObjectKey.registered(CelestialObjectId.MARS),
                    new SatelliteNetworkState.Body(CelestialObjectId.MARS, 10L, 0L)),
                List.of()));

        ClientStateLifecycle.clearAll();

        assertTrue(
            CelestialAssetStore.CLIENT.allAssetsInternal()
                .isEmpty());
        assertTrue(
            CelestialKnowledgeClientState.discoveryView()
                .discoveryState(ASTEROID_KEY)
                .isEmpty());
        assertTrue(
            CelestialDiscoveryClientState.snapshots()
                .isEmpty());
        assertTrue(
            SatelliteNetworkClientState.current()
                .bodies()
                .isEmpty());
    }
}
