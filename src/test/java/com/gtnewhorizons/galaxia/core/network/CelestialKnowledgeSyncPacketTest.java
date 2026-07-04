package com.gtnewhorizons.galaxia.core.network;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialSystemAdapters;
import com.gtnewhorizons.galaxia.registry.celestial.MinorCelestialBodyId;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldClientKnowledgeState;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldKnowledgeSnapshot;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldScanPass;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidOreKnowledgeState;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.DiscoveryState;
import com.gtnewhorizons.galaxia.registry.satellite.AsteroidSatelliteScanCompletionSnapshot;
import com.gtnewhorizons.galaxia.registry.satellite.AsteroidSatelliteScanSnapshot;
import com.gtnewhorizons.galaxia.registry.satellite.AsteroidScanClientState;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteNetworkService;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

final class CelestialKnowledgeSyncPacketTest {

    private static final UUID TEAM = new UUID(7L, 8L);
    private static final MinorCelestialBodyId ASTEROID_ID = new MinorCelestialBodyId(CelestialObjectId.FROZEN_BELT, 2);

    @BeforeAll
    static void init() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();
        CelestialSystemAdapters.register();
    }

    @AfterEach
    void clearState() {
        SatelliteNetworkService.clear();
        AsteroidFieldClientKnowledgeState.clear();
        AsteroidScanClientState.clear();
    }

    @Test
    void roundTripAppliesRegisteredKnowledgeSectionsOnClient() {
        AsteroidFieldKnowledgeSnapshot knowledge = new AsteroidFieldKnowledgeSnapshot(
            CelestialObjectId.FROZEN_BELT,
            List.of(
                new AsteroidFieldKnowledgeSnapshot.Entry(
                    ASTEROID_ID.index(),
                    DiscoveryState.DISCOVERED,
                    AsteroidOreKnowledgeState.PROFILE)));
        AsteroidSatelliteScanSnapshot scan = new AsteroidSatelliteScanSnapshot(
            new CelestialAsset.ID(new UUID(1L, 2L)),
            CelestialObjectId.FROZEN_BELT,
            ASTEROID_ID,
            AsteroidFieldScanPass.PROFILE,
            1200);
        AsteroidSatelliteScanCompletionSnapshot completion = new AsteroidSatelliteScanCompletionSnapshot(
            CelestialObjectId.FROZEN_BELT,
            ASTEROID_ID,
            5);
        SatelliteNetworkService.restoreAsteroidKnowledge(TEAM, List.of(knowledge));
        SatelliteNetworkService.restoreAsteroidScans(TEAM, List.of(scan));
        SatelliteNetworkService.restoreAsteroidScanCompletions(TEAM, List.of(completion));
        List<AsteroidFieldKnowledgeSnapshot> expectedKnowledge = SatelliteNetworkService
            .asteroidKnowledgeSnapshots(TEAM);

        CelestialKnowledgeSyncPacket packet = CelestialKnowledgeSyncPacket.forTeam(TEAM);
        ByteBuf buf = Unpooled.buffer();
        packet.toBytes(buf);
        CelestialKnowledgeSyncPacket read = new CelestialKnowledgeSyncPacket();
        read.fromBytes(buf);
        AsteroidFieldClientKnowledgeState.clear();
        AsteroidScanClientState.clear();

        new CelestialKnowledgeSyncPacket.Handler().onMessage(read, null);

        assertEquals(expectedKnowledge, AsteroidFieldClientKnowledgeState.snapshots());
        assertEquals(List.of(scan), AsteroidScanClientState.scanSnapshots());
        assertEquals(List.of(completion), AsteroidScanClientState.scanCompletions());
    }
}
