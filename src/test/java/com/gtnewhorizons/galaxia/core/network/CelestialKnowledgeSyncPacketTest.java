package com.gtnewhorizons.galaxia.core.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

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
        CelestialKnowledgeSyncRegistry.resetForTesting();
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
        CelestialObjectKey mars = CelestialObjectKey.registered(CelestialObjectId.MARS);
        CelestialObjectKey asteroid = CelestialObjectKey
            .minorBody(new MinorCelestialBodyId(CelestialObjectId.FROZEN_BELT, AsteroidSlotRanges.GENERATED_SLOT_MIN));
        CelestialKnowledgeService.putFacts(
            TEAM,
            mars,
            CelestialKnowledgeFacts.of(DiscoveryState.DISCOVERED, CelestialResourceKnowledgeState.PROFILE));
        CelestialKnowledgeService.putFacts(TEAM, asteroid, CelestialKnowledgeFacts.discoveredUnknown());
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

        CelestialKnowledgeSyncPacket packet = CelestialKnowledgeSyncPacket.forTeam(TEAM);
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
        assertEquals(List.of(scan), CelestialDiscoveryClientState.snapshots());
    }

    @ParameterizedTest
    @ValueSource(ints = { -1, 65 })
    void rejectsMalformedSectionCounts(int count) {
        ByteBuf buf = Unpooled.buffer();
        PacketUtil.writeId(buf, TEAM);
        buf.writeInt(count);

        assertThrows(IllegalStateException.class, () -> new CelestialKnowledgeSyncPacket().fromBytes(buf));
    }

    @Test
    void rejectsTooManyLocallyGeneratedSyncSectionsBeforeWriting() {
        List<CelestialKnowledgeSyncAdapter> originalAdapters = CelestialKnowledgeSyncRegistry.adapters();
        CelestialKnowledgeSyncRegistry.resetForTesting();
        try {
            IntStream.range(0, 65)
                .mapToObj(index -> emptyAdapter("galaxia:test_" + index))
                .forEach(CelestialKnowledgeSyncRegistry::register);

            ByteBuf buf = Unpooled.buffer();
            assertThrows(
                IllegalStateException.class,
                () -> CelestialKnowledgeSyncPacket.forTeam(TEAM)
                    .toBytes(buf));
            assertEquals(0, buf.writerIndex());
        } finally {
            CelestialKnowledgeSyncRegistry.resetForTesting();
            originalAdapters.forEach(CelestialKnowledgeSyncRegistry::register);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = { -1, 4097 })
    void rejectsMalformedDiscoverySnapshotCounts(int count) {
        ByteBuf buf = singleSection("galaxia:celestial_discovery");
        buf.writeInt(count);

        assertThrows(IllegalStateException.class, () -> new CelestialKnowledgeSyncPacket().fromBytes(buf));
    }

    @ParameterizedTest
    @ValueSource(ints = { -1, 65537 })
    void rejectsMalformedKnowledgeEntryCounts(int count) {
        ByteBuf buf = singleSection("galaxia:celestial_knowledge");
        buf.writeInt(count);

        assertThrows(IllegalStateException.class, () -> new CelestialKnowledgeSyncPacket().fromBytes(buf));
    }

    @Test
    void unknownSectionIdFailsLoudly() {
        ByteBuf buf = singleSection("galaxia:asteroid_fields");
        buf.writeInt(0);
        assertThrows(IllegalStateException.class, () -> new CelestialKnowledgeSyncPacket().fromBytes(buf));
    }

    @Test
    void newestServerRuntimeOwnsDiscoverySync() {
        CelestialServerRuntime first = CelestialServerRuntime.create();
        CelestialServerRuntime current = CelestialServerRuntime.create();
        CelestialDiscoveryScanSnapshot stale = activePlanetScan(CelestialObjectId.MARS, 10);
        CelestialDiscoveryScanSnapshot expected = activePlanetScan(CelestialObjectId.MOON, 20);
        first.scans()
            .restore(TEAM, List.of(stale));
        current.scans()
            .restore(TEAM, List.of(expected));

        CelestialKnowledgeSyncPacket packet = CelestialKnowledgeSyncPacket.forTeam(TEAM);
        ByteBuf buf = Unpooled.buffer();
        packet.toBytes(buf);
        CelestialKnowledgeSyncPacket read = new CelestialKnowledgeSyncPacket();
        read.fromBytes(buf);
        CelestialDiscoveryClientState.clear();

        new CelestialKnowledgeSyncPacket.Handler().onMessage(read, null);

        assertEquals(List.of(expected), CelestialDiscoveryClientState.snapshots());
        runtime = current;
    }

    private static CelestialDiscoveryScanSnapshot activePlanetScan(CelestialObjectId id, long elapsedTicks) {
        CelestialObjectKey key = CelestialObjectKey.registered(id);
        return new CelestialDiscoveryScanSnapshot(
            TEAM,
            key,
            0.5,
            2,
            CelestialDiscoveryCapability.PROSPECTING,
            CelestialDiscoveryScanSnapshot.Status.ACTIVE,
            key,
            CelestialDiscoveryStep.DETECTION,
            elapsedTicks);
    }

    private static CelestialKnowledgeSyncAdapter emptyAdapter(String type) {
        return new CelestialKnowledgeSyncAdapter() {

            @Override
            public CelestialKnowledgeSyncType type() {
                return new CelestialKnowledgeSyncType(type);
            }

            @Override
            public void write(ByteBuf buf, UUID teamId) {}

            @Override
            public CelestialKnowledgeSyncPayload read(ByteBuf buf) {
                return () -> {};
            }
        };
    }

    private static ByteBuf singleSection(String type) {
        ByteBuf buf = Unpooled.buffer();
        PacketUtil.writeId(buf, TEAM);
        buf.writeInt(1);
        PacketUtil.writeString(buf, type);
        return buf;
    }
}
