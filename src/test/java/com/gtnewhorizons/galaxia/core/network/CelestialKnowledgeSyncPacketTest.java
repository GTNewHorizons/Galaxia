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

import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialServerRuntime;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldClientKnowledgeState;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldKnowledgeSnapshot;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldKnowledgeStore;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldNodeCatalog;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.MinorCelestialBodyId;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryCapability;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryClientState;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryScanSnapshot;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryStep;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialResourceKnowledgeState;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.DiscoveryState;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteNetworkService;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

final class CelestialKnowledgeSyncPacketTest {

    private static final UUID TEAM = new UUID(7L, 8L);
    private static final MinorCelestialBodyId ASTEROID_ID = new MinorCelestialBodyId(CelestialObjectId.FROZEN_BELT, 2);
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
        AsteroidFieldKnowledgeStore.global()
            .clear();
        SatelliteNetworkService.clear();
        AsteroidFieldClientKnowledgeState.clear();
        CelestialDiscoveryClientState.clear();
    }

    @Test
    void roundTripSyncsRegisteredPlanetDiscoveryWithoutAsteroidKnowledge() {
        runtime.scans()
            .clear();
        AsteroidFieldKnowledgeStore.global()
            .clear();
        CelestialObjectKey mars = CelestialObjectKey.registered(CelestialObjectId.MARS);
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
        CelestialDiscoveryClientState.clear();

        new CelestialKnowledgeSyncPacket.Handler().onMessage(read, null);

        assertEquals(List.of(scan), CelestialDiscoveryClientState.snapshots());
        assertEquals(List.of(), AsteroidFieldClientKnowledgeState.snapshots());
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
    @ValueSource(ints = { -1, 1025 })
    void rejectsMalformedAsteroidFieldSnapshotCounts(int count) {
        ByteBuf buf = singleSection("galaxia:asteroid_fields");
        buf.writeInt(count);

        assertThrows(IllegalStateException.class, () -> new CelestialKnowledgeSyncPacket().fromBytes(buf));
    }

    @ParameterizedTest
    @ValueSource(ints = { -1, 65537 })
    void rejectsMalformedAsteroidEntryCounts(int count) {
        ByteBuf buf = singleSection("galaxia:asteroid_fields");
        buf.writeInt(1);
        PacketUtil.writeEnum(buf, CelestialObjectId.FROZEN_BELT);
        buf.writeInt(count);

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

    @Test
    void roundTripAppliesRegisteredKnowledgeSectionsOnClient() {
        var profile = GalaxiaCelestialAPI.get(CelestialObjectId.FROZEN_BELT)
            .orElseThrow()
            .properties()
            .asteroidFieldProfile();
        AsteroidFieldNodeCatalog catalog = AsteroidFieldNodeCatalog
            .fromGenerated(CelestialObjectId.FROZEN_BELT, profile);
        MinorCelestialBodyId asteroidId = catalog.nodes()
            .get(0)
            .id();
        AsteroidFieldKnowledgeSnapshot knowledge = new AsteroidFieldKnowledgeSnapshot(
            CelestialObjectId.FROZEN_BELT,
            List.of(
                new AsteroidFieldKnowledgeSnapshot.Entry(
                    asteroidId.index(),
                    DiscoveryState.DISCOVERED,
                    CelestialResourceKnowledgeState.PROFILE)),
            catalog.snapshots());
        CelestialObjectKey asteroidKey = CelestialObjectKey.minorBody(asteroidId);
        CelestialDiscoveryScanSnapshot scan = new CelestialDiscoveryScanSnapshot(
            TEAM,
            asteroidKey,
            0.5,
            5,
            CelestialDiscoveryCapability.PROSPECTING,
            CelestialDiscoveryScanSnapshot.Status.ACTIVE,
            asteroidKey,
            CelestialDiscoveryStep.PROFILE,
            1200);
        CelestialDiscoveryScanSnapshot completion = CelestialDiscoveryScanSnapshot.complete(
            TEAM,
            new com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryScanScope(
                CelestialObjectKey.minorBody(new MinorCelestialBodyId(CelestialObjectId.FROZEN_BELT, 3)),
                0.5,
                5),
            CelestialDiscoveryCapability.PROSPECTING);
        AsteroidFieldKnowledgeStore.global()
            .restore(
                TEAM,
                List.of(knowledge),
                bodyId -> bodyId == CelestialObjectId.FROZEN_BELT ? java.util.Optional.of(
                    GalaxiaCelestialAPI.get(bodyId)
                        .orElseThrow()
                        .properties()
                        .asteroidFieldProfile())
                    : java.util.Optional.empty());
        runtime.scans()
            .restore(TEAM, List.of(scan, completion));
        List<AsteroidFieldKnowledgeSnapshot> expectedKnowledge = AsteroidFieldKnowledgeStore.global()
            .snapshots(TEAM);

        CelestialKnowledgeSyncPacket packet = CelestialKnowledgeSyncPacket.forTeam(TEAM);
        ByteBuf buf = Unpooled.buffer();
        packet.toBytes(buf);
        CelestialKnowledgeSyncPacket read = new CelestialKnowledgeSyncPacket();
        read.fromBytes(buf);
        AsteroidFieldClientKnowledgeState.clear();
        CelestialDiscoveryClientState.clear();

        new CelestialKnowledgeSyncPacket.Handler().onMessage(read, null);

        assertEquals(expectedKnowledge, AsteroidFieldClientKnowledgeState.snapshots());
        assertEquals(List.of(scan, completion), CelestialDiscoveryClientState.snapshots());
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
