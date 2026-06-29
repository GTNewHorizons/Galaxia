package com.gtnewhorizons.galaxia.core.network;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidDetectionState;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldKnowledgeSnapshot;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidOreKnowledgeState;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteBandwidthFormatter;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteDataKey;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteDataType;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteNetworkClientState;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteNetworkState;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

final class SatelliteNetworkSyncPacketTest {

    @Test
    void roundTripPreservesSatelliteNetworkSnapshot() {
        UUID teamId = new UUID(5L, 6L);
        SatelliteNetworkState state = new SatelliteNetworkState(
            teamId,
            12,
            Map.of(
                CelestialObjectId.MARS,
                new SatelliteNetworkState.Body(CelestialObjectId.MARS, 20L, 4L),
                CelestialObjectId.OVERWORLD,
                new SatelliteNetworkState.Body(CelestialObjectId.OVERWORLD, 10L, 4L)),
            List.of(
                new SatelliteNetworkState.Link(CelestialObjectId.MARS, CelestialObjectId.OVERWORLD, 10L, 4L, 3L, 1L)),
            List.of(
                new SatelliteNetworkState.PendingData(
                    CelestialObjectId.MARS,
                    List.of(CelestialObjectId.OVERWORLD),
                    SatelliteDataKey.origin(SatelliteDataType.PROSPECTING, CelestialObjectId.EGORA),
                    SatelliteBandwidthFormatter.kilobits(12L))));

        SatelliteNetworkSyncPacket packet = new SatelliteNetworkSyncPacket(state);
        ByteBuf buf = Unpooled.buffer();
        packet.toBytes(buf);
        SatelliteNetworkSyncPacket read = new SatelliteNetworkSyncPacket();
        read.fromBytes(buf);

        assertEquals(state, read.state());
    }

    @Test
    void roundTripPreservesAsteroidKnowledgeSnapshots() {
        UUID teamId = new UUID(5L, 7L);
        SatelliteNetworkState state = SatelliteNetworkState.empty(teamId, 21);
        AsteroidFieldKnowledgeSnapshot snapshot = new AsteroidFieldKnowledgeSnapshot(
            CelestialObjectId.FROZEN_BELT,
            List.of(
                new AsteroidFieldKnowledgeSnapshot.Entry(
                    2,
                    AsteroidDetectionState.DETECTED,
                    AsteroidOreKnowledgeState.SIGNATURE),
                new AsteroidFieldKnowledgeSnapshot.Entry(
                    3,
                    AsteroidDetectionState.HIDDEN,
                    AsteroidOreKnowledgeState.UNKNOWN)));

        SatelliteNetworkSyncPacket packet = new SatelliteNetworkSyncPacket(state, List.of(snapshot));
        ByteBuf buf = Unpooled.buffer();
        packet.toBytes(buf);
        SatelliteNetworkSyncPacket read = new SatelliteNetworkSyncPacket();
        read.fromBytes(buf);

        assertEquals(List.of(snapshot), read.asteroidKnowledge());
    }

    @Test
    void handlerStoresSnapshotOnClient() {
        SatelliteNetworkClientState.clear();
        SatelliteNetworkState state = new SatelliteNetworkState(
            new UUID(7L, 8L),
            14,
            Map.of(CelestialObjectId.MARS, new SatelliteNetworkState.Body(CelestialObjectId.MARS, 10L, 0L)),
            List.of());

        new SatelliteNetworkSyncPacket.Handler().onMessage(new SatelliteNetworkSyncPacket(state), null);

        assertEquals(state, SatelliteNetworkClientState.current());
        SatelliteNetworkClientState.clear();
    }
}
