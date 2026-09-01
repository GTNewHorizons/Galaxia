package com.gtnewhorizons.galaxia.core.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialServerRuntime;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidSlotRanges;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.MinorCelestialBodyId;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryCapability;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryClientState;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryScanSnapshot;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryStep;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeClientState;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeFacts;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeFacts.CelestialResourceKnowledgeState;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeFacts.DiscoveryState;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeService;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteNetworkService;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

final class CelestialKnowledgeSyncPacketTest {

    private static final UUID TEAM = new UUID(7L, 8L);
    private static CelestialServerRuntime runtime;

    @BeforeAll
    static void init() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();
        runtime = CelestialServerRuntime.create();
    }

    @AfterEach
    void clearState() {
        runtime.scans()
            .clear();
        CelestialKnowledgeService.clearFacts();
        SatelliteNetworkService.clear();
        CelestialKnowledgeClientState.clear();
        CelestialDiscoveryClientState.clear();
    }

    @Test
    void roundTripSyncsRegisteredAndMinorFactsPlusDiscovery() {
        UUID otherTeam = new UUID(9L, 10L);
        CelestialObjectKey mars = CelestialObjectKey.registered(CelestialObjectId.MARS);
        CelestialObjectKey moon = CelestialObjectKey.registered(CelestialObjectId.MOON);
        CelestialObjectKey asteroid = CelestialObjectKey
            .minorBody(new MinorCelestialBodyId(CelestialObjectId.FROZEN_BELT, AsteroidSlotRanges.GENERATED_SLOT_MIN));
        CelestialKnowledgeService.putFacts(
            TEAM,
            mars,
            CelestialKnowledgeFacts.of(DiscoveryState.DISCOVERED, CelestialResourceKnowledgeState.PROFILE));
        CelestialKnowledgeService.putFacts(TEAM, asteroid, CelestialKnowledgeFacts.discoveredUnknown());
        CelestialKnowledgeService.putFacts(otherTeam, moon, CelestialKnowledgeFacts.discoveredUnknown());
        CelestialDiscoveryScanSnapshot scan = new CelestialDiscoveryScanSnapshot(
            TEAM,
            mars,
            0.5,
            2,
            CelestialDiscoveryCapability.PROSPECTING,
            CelestialDiscoveryScanSnapshot.Status.ACTIVE,
            mars,
            CelestialDiscoveryStep.DETECTION,
            40);
        runtime.scans()
            .restore(TEAM, List.of(scan));
        runtime.scans()
            .restore(otherTeam, List.of(activePlanetScan(otherTeam, CelestialObjectId.MOON, 20)));

        CelestialKnowledgeSyncPacket packet = CelestialKnowledgeSyncPacket.forTeam(TEAM, runtime.scans());
        ByteBuf buf = Unpooled.buffer();
        packet.toBytes(buf);
        CelestialKnowledgeSyncPacket read = new CelestialKnowledgeSyncPacket();
        read.fromBytes(buf);
        CelestialKnowledgeClientState.clear();
        CelestialDiscoveryClientState.clear();

        new CelestialKnowledgeSyncPacket.Handler().onMessage(read, null);

        assertEquals(DiscoveryState.DISCOVERED, CelestialKnowledgeClientState.effectiveDiscoveryState(mars));
        assertEquals(
            CelestialResourceKnowledgeState.PROFILE,
            CelestialKnowledgeClientState.resourceKnowledge(mars)
                .orElseThrow());
        assertEquals(
            DiscoveryState.DISCOVERED,
            CelestialKnowledgeClientState.discoveryView()
                .discoveryState(asteroid)
                .orElse(null));
        assertTrue(
            CelestialKnowledgeClientState.discoveryView()
                .discoveryState(moon)
                .isEmpty());
        assertEquals(List.of(scan), CelestialDiscoveryClientState.snapshots());
    }

    @ParameterizedTest
    @ValueSource(ints = { -1, 4097 })
    void rejectsMalformedDiscoverySnapshotCounts(int count) {
        ByteBuf buf = directPayload();
        buf.writeInt(0);
        buf.writeInt(count);

        assertThrows(IllegalStateException.class, () -> new CelestialKnowledgeSyncPacket().fromBytes(buf));
    }

    @ParameterizedTest
    @ValueSource(ints = { -1, 65537 })
    void rejectsMalformedKnowledgeEntryCounts(int count) {
        ByteBuf buf = directPayload();
        buf.writeInt(count);

        assertThrows(IllegalStateException.class, () -> new CelestialKnowledgeSyncPacket().fromBytes(buf));
    }

    @Test
    void encodesKnowledgeAndDiscoveryCountsWithoutPluginEnvelope() {
        ByteBuf buf = Unpooled.buffer();
        CelestialKnowledgeSyncPacket.forTeam(TEAM, runtime.scans())
            .toBytes(buf);

        assertEquals(TEAM, PacketUtil.readId(buf));
        assertEquals(0, buf.readInt());
        assertEquals(0, buf.readInt());
        assertEquals(0, buf.readableBytes());
    }

    @Test
    void rejectsDiscoverySnapshotsFromAnotherTeam() {
        ByteBuf buf = directPayload();
        buf.writeInt(0);
        buf.writeInt(1);
        writeScan(buf, activePlanetScan(new UUID(9L, 10L), CelestialObjectId.MARS, 10));

        assertThrows(IllegalStateException.class, () -> new CelestialKnowledgeSyncPacket().fromBytes(buf));
    }

    @Test
    void syncUsesTheProvidedDiscoveryRuntime() {
        CelestialServerRuntime first = CelestialServerRuntime.create();
        CelestialServerRuntime current = CelestialServerRuntime.create();
        CelestialDiscoveryScanSnapshot stale = activePlanetScan(TEAM, CelestialObjectId.MARS, 10);
        CelestialDiscoveryScanSnapshot expected = activePlanetScan(TEAM, CelestialObjectId.MOON, 20);
        first.scans()
            .restore(TEAM, List.of(stale));
        current.scans()
            .restore(TEAM, List.of(expected));

        CelestialKnowledgeSyncPacket packet = CelestialKnowledgeSyncPacket.forTeam(TEAM, current.scans());
        ByteBuf buf = Unpooled.buffer();
        packet.toBytes(buf);
        CelestialKnowledgeSyncPacket read = new CelestialKnowledgeSyncPacket();
        read.fromBytes(buf);
        CelestialDiscoveryClientState.clear();

        new CelestialKnowledgeSyncPacket.Handler().onMessage(read, null);

        assertEquals(List.of(expected), CelestialDiscoveryClientState.snapshots());
        runtime = current;
    }

    private static CelestialDiscoveryScanSnapshot activePlanetScan(UUID teamId, CelestialObjectId id,
        long elapsedTicks) {
        CelestialObjectKey key = CelestialObjectKey.registered(id);
        return new CelestialDiscoveryScanSnapshot(
            teamId,
            key,
            0.5,
            2,
            CelestialDiscoveryCapability.PROSPECTING,
            CelestialDiscoveryScanSnapshot.Status.ACTIVE,
            key,
            CelestialDiscoveryStep.DETECTION,
            elapsedTicks);
    }

    private static void writeScan(ByteBuf buf, CelestialDiscoveryScanSnapshot scan) {
        PacketUtil.writeId(buf, scan.teamId());
        PacketUtil.writeCelestialObjectKey(buf, scan.anchorKey());
        buf.writeDouble(scan.radius());
        buf.writeLong(scan.scopeRevision());
        PacketUtil.writeEnum(buf, scan.capability());
        PacketUtil.writeEnum(buf, scan.status());
        PacketUtil.writeCelestialObjectKey(buf, scan.targetKey());
        PacketUtil.writeEnum(buf, scan.step());
        buf.writeLong(scan.elapsedTicks());
    }

    private static ByteBuf directPayload() {
        ByteBuf buf = Unpooled.buffer();
        PacketUtil.writeId(buf, TEAM);
        return buf;
    }
}
