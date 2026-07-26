package com.gtnewhorizons.galaxia.registry.satellite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.core.persistence.FacilityPersistenceManager;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialServerRuntime;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldProfile;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldResolver;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeFacts.DiscoveryState;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeService;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleDebugDataGenerator;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class SatelliteNetworkServiceTest {

    private static final UUID TEAM = new UUID(11L, 12L);

    @BeforeAll
    static void initModules() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();
        CelestialServerRuntime.create();
        GalaxiaTestBootstrap.ensureFacilityModules();
    }

    @AfterEach
    void clearState() {
        CelestialAssetStore.clear();
        SatelliteNetworkService.clear();
        CelestialKnowledgeService.clearFacts();
    }

    @Test
    void rebuildStoresDerivedSnapshotAndKeepsRevisionWhenContentIsUnchanged() {
        SatelliteNetworkState state = SatelliteNetworkService
            .rebuild(TEAM, nodes(), capacity(CelestialObjectId.MARS, 10L, CelestialObjectId.OVERWORLD, 10L), Map.of());
        SatelliteNetworkState unchanged = SatelliteNetworkService
            .rebuild(TEAM, nodes(), capacity(CelestialObjectId.MARS, 10L, CelestialObjectId.OVERWORLD, 10L), Map.of());

        assertSame(state, SatelliteNetworkService.current(TEAM));
        assertSame(state, unchanged);
        assertEquals(1, state.revision());
        assertEquals(10L, state.capacityKbps(CelestialObjectKey.registered(CelestialObjectId.MARS)));
        assertEquals(0L, state.capacityKbps(CelestialObjectKey.registered(CelestialObjectId.EGORA)));
        assertEquals(
            2,
            state.bodies()
                .size());
        assertEquals(
            1,
            state.links()
                .size());
    }

    @Test
    void rebuildIncrementsRevisionWhenCapacityChanges() {
        SatelliteNetworkState first = SatelliteNetworkService
            .rebuild(TEAM, nodes(), capacity(CelestialObjectId.MARS, 10L, CelestialObjectId.OVERWORLD, 10L), Map.of());

        SatelliteNetworkState changed = SatelliteNetworkService
            .rebuild(TEAM, nodes(), capacity(CelestialObjectId.MARS, 20L, CelestialObjectId.OVERWORLD, 10L), Map.of());

        assertEquals(first.revision() + 1, changed.revision());
        assertEquals(20L, changed.capacityKbps(CelestialObjectKey.registered(CelestialObjectId.MARS)));
    }

    @Test
    void rebuildUsesDataPlannerLoadForSnapshotUsage() {
        SatelliteDataBufferStore store = new SatelliteDataBufferStore();
        SatelliteDataKey prospecting = SatelliteDataKey.any(SatelliteDataType.PROSPECTING);
        store.finishProduction(
            TEAM,
            CelestialObjectKey.registered(CelestialObjectId.MARS),
            prospecting,
            SatelliteBandwidthFormatter.kilobits(100L));
        store.requestData(
            TEAM,
            CelestialObjectKey.registered(CelestialObjectId.OVERWORLD),
            prospecting,
            SatelliteBandwidthFormatter.kilobits(100L));

        SatelliteNetworkState state = SatelliteNetworkService
            .rebuild(TEAM, nodes(), capacity(CelestialObjectId.MARS, 10L, CelestialObjectId.OVERWORLD, 10L), store);

        assertEquals(10L, state.usedKbps(CelestialObjectKey.registered(CelestialObjectId.MARS)));
        assertEquals(10L, state.usedKbps(CelestialObjectKey.registered(CelestialObjectId.OVERWORLD)));
        assertEquals(
            10L,
            state.links()
                .get(0)
                .usedKbps());
        assertEquals(
            List.of(CelestialObjectKey.registered(CelestialObjectId.OVERWORLD)),
            state.pendingData(CelestialObjectKey.registered(CelestialObjectId.MARS))
                .get(0)
                .destinationBodyKeys());
    }

    @Test
    void transitBodyCountsForwardedBandwidthOnce() {
        SatelliteNetworkGraph.Edge incoming = new SatelliteNetworkGraph.Edge(
            CelestialObjectKey.registered(CelestialObjectId.MARS),
            CelestialObjectKey.registered(CelestialObjectId.EGORA));
        SatelliteNetworkGraph.Edge outgoing = new SatelliteNetworkGraph.Edge(
            CelestialObjectKey.registered(CelestialObjectId.EGORA),
            CelestialObjectKey.registered(CelestialObjectId.OVERWORLD));

        SatelliteNetworkState state = SatelliteNetworkCalculator.fromGraph(
            TEAM,
            1,
            nodes(),
            List.of(incoming, outgoing),
            capacity(CelestialObjectId.MARS, 10L, CelestialObjectId.EGORA, 10L, CelestialObjectId.OVERWORLD, 10L),
            Map.of(incoming, 10L, outgoing, 10L));

        assertEquals(10L, state.usedKbps(CelestialObjectKey.registered(CelestialObjectId.MARS)));
        assertEquals(10L, state.usedKbps(CelestialObjectKey.registered(CelestialObjectId.EGORA)));
        assertEquals(10L, state.usedKbps(CelestialObjectKey.registered(CelestialObjectId.OVERWORLD)));
    }

    @Test
    void rebuildIncrementsRevisionWhenPendingDataChanges() {
        SatelliteDataBufferStore store = new SatelliteDataBufferStore();
        SatelliteNetworkState first = SatelliteNetworkService
            .rebuild(TEAM, nodes(), capacity(CelestialObjectId.MARS, 10L, CelestialObjectId.OVERWORLD, 10L), store);

        SatelliteDataKey prospecting = SatelliteDataKey.any(SatelliteDataType.PROSPECTING);
        store.finishProduction(
            TEAM,
            CelestialObjectKey.registered(CelestialObjectId.MARS),
            prospecting,
            SatelliteBandwidthFormatter.kilobits(25L));
        SatelliteNetworkState changed = SatelliteNetworkService
            .rebuild(TEAM, nodes(), capacity(CelestialObjectId.MARS, 10L, CelestialObjectId.OVERWORLD, 10L), store);

        assertEquals(first.revision() + 1, changed.revision());
        assertEquals(
            SatelliteBandwidthFormatter.kilobits(25L),
            changed.pendingData(CelestialObjectKey.registered(CelestialObjectId.MARS))
                .get(0)
                .deciKb());
    }

    @Test
    void tickDataJobsUsesRegisteredDebugDataEndpoints() {
        AutomatedFacility source = facility(CelestialObjectId.MARS);
        AutomatedFacility destination = facility(CelestialObjectId.EGORA);
        CelestialAssetStore.registerAsset(TEAM, source);
        CelestialAssetStore.registerAsset(TEAM, destination);
        ModuleDebugDataGenerator producer = addDebugModule(source);
        ModuleDebugDataGenerator consumer = addDebugModule(destination);
        producer.configure(ModuleDebugDataGenerator.Config.produce(SatelliteDataType.COMMUNICATION, 10L, 1));
        consumer.configure(ModuleDebugDataGenerator.Config.consume(SatelliteDataType.COMMUNICATION, 10L, 1, null));
        SatelliteNetworkService.refreshFacilityEndpoints(source);
        SatelliteNetworkService.refreshFacilityEndpoints(destination);

        SatelliteNetworkService.tickDataJobs();

        assertEquals(CelestialObjectKey.registered(CelestialObjectId.EGORA), producer.detectedCounterpartBodyKey());
        assertEquals(CelestialObjectKey.registered(CelestialObjectId.MARS), consumer.detectedCounterpartBodyKey());

        consumer.configure(ModuleDebugDataGenerator.Config.consume(SatelliteDataType.PROSPECTING, 10L, 1, null));
        SatelliteNetworkService.refreshFacilityEndpoints(destination);
        SatelliteNetworkService.tickDataJobs();

        assertNull(producer.detectedCounterpartBodyKey());
        assertNull(consumer.detectedCounterpartBodyKey());
    }

    @Test
    void serverRuntimeAdvancesProspectingDataJobsWithoutBypassingDiscovery() {
        AsteroidFieldProfile profile = GalaxiaCelestialAPI.get(CelestialObjectId.FROZEN_BELT)
            .orElseThrow()
            .properties()
            .asteroidFieldProfile();
        long initiallyDetected = AsteroidFieldResolver.resolveAll(CelestialObjectId.FROZEN_BELT, profile)
            .stream()
            .filter(node -> node.initialDetectionState() == DiscoveryState.DISCOVERED)
            .count();
        AutomatedFacility facility = facility(CelestialObjectId.FROZEN_BELT);
        CelestialAssetStore.registerAsset(TEAM, facility);
        ModuleDebugDataGenerator producer = addDebugModule(facility);
        ModuleDebugDataGenerator consumer = addDebugModule(facility);
        producer.configure(ModuleDebugDataGenerator.Config.produce(SatelliteDataType.PROSPECTING, 10L, 1));
        consumer.configure(ModuleDebugDataGenerator.Config.consume(SatelliteDataType.PROSPECTING, 10L, 1, null));
        SatelliteNetworkService.refreshFacilityEndpoints(facility);

        CelestialServerRuntime.create()
            .tick();

        long detectedAfterTick = AsteroidFieldResolver.resolveAll(CelestialObjectId.FROZEN_BELT, profile)
            .stream()
            .filter(
                node -> CelestialKnowledgeService.discoveryState(TEAM, CelestialObjectKey.minorBody(node.id()))
                    == DiscoveryState.DISCOVERED)
            .count();
        assertEquals(initiallyDetected, detectedAfterTick);
        assertEquals(100L, consumer.consumedDeciKb());
        assertTrue(
            CelestialKnowledgeService.snapshot(TEAM)
                .isEmpty());
    }

    @Test
    void registeredDebugDataEndpointsProduceTransferUsageOnServiceTick() {
        AutomatedFacility source = facility(CelestialObjectId.MARS);
        AutomatedFacility destination = facility(CelestialObjectId.EGORA);
        CelestialAssetStore.registerAsset(TEAM, source);
        CelestialAssetStore.registerAsset(TEAM, destination);
        ModuleDebugDataGenerator producer = addDebugModule(source);
        ModuleDebugDataGenerator consumer = addDebugModule(destination);
        producer.configure(ModuleDebugDataGenerator.Config.produce(SatelliteDataType.COMMUNICATION, 10L, 1));
        consumer.configure(ModuleDebugDataGenerator.Config.consume(SatelliteDataType.COMMUNICATION, 10L, 1, null));
        SatelliteNetworkService.refreshFacilityEndpoints(source);
        SatelliteNetworkService.refreshFacilityEndpoints(destination);
        CelestialAssetStore.SERVER.setSatelliteCount(TEAM, CelestialObjectId.MARS, SatelliteKind.COMMUNICATION, 1);
        CelestialAssetStore.SERVER.setSatelliteCount(TEAM, CelestialObjectId.EGORA, SatelliteKind.COMMUNICATION, 1);
        SatelliteNetworkService.rebuild(TEAM, 0.0D);

        SatelliteNetworkService.tickDataJobs();
        SatelliteNetworkState state = SatelliteNetworkService.rebuild(TEAM, 0.0D);

        assertEquals(CelestialObjectKey.registered(CelestialObjectId.EGORA), producer.detectedCounterpartBodyKey());
        assertEquals(CelestialObjectKey.registered(CelestialObjectId.MARS), consumer.detectedCounterpartBodyKey());
        assertEquals(5L, consumer.consumedDeciKb());
        assertEquals(10L, state.usedKbps(CelestialObjectKey.registered(CelestialObjectId.MARS)));
        assertEquals(10L, state.usedKbps(CelestialObjectKey.registered(CelestialObjectId.EGORA)));
    }

    @Test
    void rebuildClearsVisibleTransferUsageWhenLinkTopologyChanges() {
        AutomatedFacility source = facility(CelestialObjectId.MARS);
        AutomatedFacility destination = facility(CelestialObjectId.EGORA);
        CelestialAssetStore.registerAsset(TEAM, source);
        CelestialAssetStore.registerAsset(TEAM, destination);
        ModuleDebugDataGenerator producer = addDebugModule(source);
        ModuleDebugDataGenerator consumer = addDebugModule(destination);
        producer.configure(ModuleDebugDataGenerator.Config.produce(SatelliteDataType.COMMUNICATION, 10L, 1));
        consumer.configure(ModuleDebugDataGenerator.Config.consume(SatelliteDataType.COMMUNICATION, 10L, 1, null));
        SatelliteNetworkService.refreshFacilityEndpoints(source);
        SatelliteNetworkService.refreshFacilityEndpoints(destination);
        List<SatelliteNetworkGraph.Node> fullRoute = nodes();
        Map<CelestialObjectKey, Long> fullCapacity = capacity(
            CelestialObjectId.MARS,
            1000L,
            CelestialObjectId.OVERWORLD,
            1000L,
            CelestialObjectId.EGORA,
            1000L);
        SatelliteNetworkService.rebuild(TEAM, fullRoute, fullCapacity, new SatelliteDataBufferStore());

        SatelliteNetworkService.tickDataJobs();
        SatelliteNetworkState active = SatelliteNetworkService
            .rebuild(TEAM, fullRoute, fullCapacity, new SatelliteDataBufferStore());

        assertTrue(
            active.links()
                .stream()
                .anyMatch(link -> link.usedKbps() > 0L));

        SatelliteNetworkState changed = SatelliteNetworkService.rebuild(
            TEAM,
            List.of(node(CelestialObjectId.MARS, 0.0D), node(CelestialObjectId.OVERWORLD, 10.0D)),
            capacity(CelestialObjectId.MARS, 1000L, CelestialObjectId.OVERWORLD, 1000L),
            new SatelliteDataBufferStore());

        assertEquals(0L, changed.usedKbps(CelestialObjectKey.registered(CelestialObjectId.MARS)));
        assertEquals(0L, changed.usedKbps(CelestialObjectKey.registered(CelestialObjectId.OVERWORLD)));
        assertTrue(
            changed.links()
                .stream()
                .allMatch(link -> link.usedKbps() == 0L));
    }

    @Test
    void enabledResearchDebugDataEndpointsDetectEachOther() {
        AutomatedFacility source = facility(CelestialObjectId.MARS);
        AutomatedFacility destination = facility(CelestialObjectId.EGORA);
        CelestialAssetStore.registerAsset(TEAM, source);
        CelestialAssetStore.registerAsset(TEAM, destination);
        ModuleDebugDataGenerator producer = addDebugModule(source);
        ModuleDebugDataGenerator consumer = addDebugModule(destination);
        producer.configure(ModuleDebugDataGenerator.Config.produce(SatelliteDataType.RESEARCH, 400L, 1));
        consumer.configure(ModuleDebugDataGenerator.Config.consume(SatelliteDataType.RESEARCH, 500L, 1, null));
        SatelliteNetworkService.refreshFacilityEndpoints(source);
        SatelliteNetworkService.refreshFacilityEndpoints(destination);
        CelestialAssetStore.SERVER.setSatelliteCount(TEAM, CelestialObjectId.MARS, SatelliteKind.COMMUNICATION, 1);
        CelestialAssetStore.SERVER.setSatelliteCount(TEAM, CelestialObjectId.EGORA, SatelliteKind.COMMUNICATION, 1);
        SatelliteNetworkService.rebuild(TEAM, 0.0D);

        SatelliteNetworkService.tickDataJobs();

        assertEquals(CelestialObjectKey.registered(CelestialObjectId.EGORA), producer.detectedCounterpartBodyKey());
        assertEquals(CelestialObjectKey.registered(CelestialObjectId.MARS), consumer.detectedCounterpartBodyKey());
    }

    @Test
    void unknownTeamRefreshDoesNotRemoveRegisteredDebugDataEndpoint() {
        AutomatedFacility source = facility(CelestialObjectId.MARS);
        AutomatedFacility destination = facility(CelestialObjectId.EGORA);
        CelestialAssetStore.registerAsset(TEAM, source);
        CelestialAssetStore.registerAsset(TEAM, destination);
        ModuleDebugDataGenerator producer = addDebugModule(source);
        ModuleDebugDataGenerator consumer = addDebugModule(destination);
        producer.configure(ModuleDebugDataGenerator.Config.produce(SatelliteDataType.COMMUNICATION, 10L, 1));
        consumer.configure(ModuleDebugDataGenerator.Config.consume(SatelliteDataType.COMMUNICATION, 10L, 1, null));
        SatelliteNetworkService.refreshFacilityEndpoints(source);
        SatelliteNetworkService.refreshFacilityEndpoints(destination);

        SatelliteNetworkService.refreshAssetEndpoints(null, source);
        SatelliteNetworkService.refreshAssetEndpoints(null, destination);
        SatelliteNetworkService.tickDataJobs();

        assertEquals(CelestialObjectKey.registered(CelestialObjectId.EGORA), producer.detectedCounterpartBodyKey());
        assertEquals(CelestialObjectKey.registered(CelestialObjectId.MARS), consumer.detectedCounterpartBodyKey());
    }

    @Test
    void worldLoadClearsCachedSatelliteNetworkState(@TempDir Path tempDir) {
        SatelliteNetworkService
            .rebuild(TEAM, nodes(), capacity(CelestialObjectId.MARS, 10L, CelestialObjectId.OVERWORLD, 10L), Map.of());

        new FacilityPersistenceManager(CelestialServerRuntime.create()).loadFromSaveDirectory(tempDir.toFile());

        assertEquals(
            0,
            SatelliteNetworkService.current(TEAM)
                .revision());
    }

    private static List<SatelliteNetworkGraph.Node> nodes() {
        return List.of(
            node(CelestialObjectId.MARS, 0.0D),
            node(CelestialObjectId.OVERWORLD, 10.0D),
            node(CelestialObjectId.EGORA, 20.0D));
    }

    private static SatelliteNetworkGraph.Node node(CelestialObjectId id, double x) {
        return new SatelliteNetworkGraph.Node(CelestialObjectKey.registered(id), null, id.ordinal(), x, 0.0D, 1.0D);
    }

    private static Map<CelestialObjectKey, Long> capacity(Object... entries) {
        Map<CelestialObjectKey, Long> result = new java.util.LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            result.put(CelestialObjectKey.registered((CelestialObjectId) entries[i]), (Long) entries[i + 1]);
        }
        return Map.copyOf(result);
    }

    private static AutomatedFacility facility(CelestialObjectId bodyId) {
        return new AutomatedFacility(
            CelestialAsset.ID.create(),
            bodyId,
            CelestialAsset.Kind.AUTOMATED_OUTPOST,
            Buildable.Status.OPERATIONAL);
    }

    private static ModuleDebugDataGenerator addDebugModule(AutomatedFacility facility) {
        ModuleInstance module = FacilityModuleKind.DEBUG_DATA_GENERATOR.create(
            StationTileCoord.of(
                facility.modules()
                    .size(),
                0),
            ModuleShape.SINGLE,
            ModuleTier.HV);
        module.updateStatus(Buildable.Status.OPERATIONAL);
        facility.addModule(module);
        return (ModuleDebugDataGenerator) module.component();
    }
}
