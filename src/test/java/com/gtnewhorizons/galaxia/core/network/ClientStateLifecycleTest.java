package com.gtnewhorizons.galaxia.core.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldClientKnowledgeState;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldKnowledgeSnapshot;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.MinorCelestialBodyId;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryCapability;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryClientState;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryScanScope;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryScanSnapshot;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeClientState;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialResourceKnowledgeState;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.DiscoveryState;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteNetworkClientState;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteNetworkState;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class ClientStateLifecycleTest {

    private static final UUID TEAM = new UUID(1L, 2L);
    private static final MinorCelestialBodyId ASTEROID_ID = new MinorCelestialBodyId(CelestialObjectId.FROZEN_BELT, 3);
    private static final CelestialObjectKey ASTEROID_KEY = CelestialObjectKey.minorBody(ASTEROID_ID);

    @BeforeAll
    static void init() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();
    }

    @AfterEach
    void clearState() {
        CelestialAssetStore.CLIENT.clearInternal();
        CelestialKnowledgeClientState.clear();
        AsteroidFieldClientKnowledgeState.clear();
        CelestialDiscoveryClientState.clear();
        SatelliteNetworkClientState.clear();
    }

    @Test
    void clearAllClearsEveryClientSideSyncStore() {
        CelestialAsset asset = CelestialAsset
            .create(CelestialObjectId.MARS, CelestialAsset.Kind.AUTOMATED_OUTPOST, Buildable.Status.OPERATIONAL);
        CelestialAssetStore.CLIENT.registerAssetInternal(TEAM, asset);
        AsteroidFieldClientKnowledgeState.updateFields(
            List.of(
                new AsteroidFieldKnowledgeSnapshot(
                    CelestialObjectId.FROZEN_BELT,
                    List.of(
                        new AsteroidFieldKnowledgeSnapshot.Entry(
                            3,
                            DiscoveryState.DISCOVERED,
                            CelestialResourceKnowledgeState.PROFILE)))));
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
            CelestialKnowledgeClientState.discoveryState(ASTEROID_KEY)
                .isEmpty());
        assertTrue(
            AsteroidFieldClientKnowledgeState.snapshots()
                .isEmpty());
        assertTrue(
            AsteroidFieldClientKnowledgeState.oreKnowledge(ASTEROID_KEY)
                .isEmpty());
        assertTrue(
            CelestialDiscoveryClientState.snapshots()
                .isEmpty());
        assertEquals(
            0,
            SatelliteNetworkClientState.current()
                .revision());
        assertEquals(
            0L,
            SatelliteNetworkClientState.current()
                .capacityKbps(CelestialObjectId.MARS));
    }
}
