package com.gtnewhorizons.galaxia.registry.satellite;

import static com.gtnewhorizons.galaxia.registry.outpost.FacilityTestFixtures.addModule;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    /** Product contract: disabled satellites remain owned assets but cannot sustain a bandwidth reservation. */
    @Test
    void disablingCommunicationSatellitesRemovesCapacityWithoutConsumingAssets() {
        var earth = CelestialObjectKey.registered(CelestialObjectId.OVERWORLD);
        var mars = CelestialObjectKey.registered(CelestialObjectId.MARS);
        CelestialAssetStore.SERVER.addSatellites(TEAM, earth, SatelliteKind.COMMUNICATION, 10);
        var satellites = CelestialAssetStore.SERVER.addSatellites(TEAM, mars, SatelliteKind.COMMUNICATION, 10);
        long capacity = SatelliteNetworkService.rebuild(TEAM, 0)
            .capacityKbps(mars);
        assertTrue(capacity > 0);
        UUID reservation = UUID.randomUUID();
        assertTrue(SatelliteNetworkService.reserveBandwidth(TEAM, reservation, earth, mars, 1));

        for (var id : satellites) assertTrue(CelestialAssetStore.disableAsset(id));
        assertEquals(
            0,
            SatelliteNetworkService.rebuild(TEAM, 0)
                .capacityKbps(mars));
        assertFalse(SatelliteNetworkService.hasReservation(TEAM, reservation));
        assertEquals(
            satellites.size(),
            CelestialAssetStore.SERVER.satelliteCount(TEAM, mars, SatelliteKind.COMMUNICATION));

        for (var id : satellites) assertTrue(CelestialAssetStore.enableAsset(id));
        assertEquals(
            capacity,
            SatelliteNetworkService.rebuild(TEAM, 0)
                .capacityKbps(mars));
        assertTrue(SatelliteNetworkService.reserveBandwidth(TEAM, UUID.randomUUID(), earth, mars, 1));
    }

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

    // Product contract: reservations own shared satellite capacity until released and IDs cannot change their terms.
    @Test
    void reservationsAreIdempotentAndShareRelayCapacity() {
        CelestialObjectKey mars = CelestialObjectKey.registered(CelestialObjectId.MARS);
        CelestialObjectKey overworld = CelestialObjectKey.registered(CelestialObjectId.OVERWORLD);
        CelestialObjectKey egora = CelestialObjectKey.registered(CelestialObjectId.EGORA);
        UUID first = new UUID(1L, 1L);
        UUID second = new UUID(1L, 2L);
        SatelliteNetworkService.rebuild(
            TEAM,
            nodes(),
            capacity(CelestialObjectId.MARS, 10L, CelestialObjectId.OVERWORLD, 10L, CelestialObjectId.EGORA, 10L),
            new SatelliteDataBufferStore());

        assertTrue(SatelliteNetworkService.reserveBandwidth(TEAM, first, mars, overworld, 6L));
        assertTrue(SatelliteNetworkService.reserveBandwidth(TEAM, first, mars, overworld, 6L));
        assertFalse(SatelliteNetworkService.reserveBandwidth(TEAM, first, mars, overworld, 4L));
        assertFalse(SatelliteNetworkService.reserveBandwidth(TEAM, first, egora, overworld, 6L));
        assertFalse(SatelliteNetworkService.reserveBandwidth(TEAM, second, egora, overworld, 5L));
        assertTrue(SatelliteNetworkService.reserveBandwidth(TEAM, second, egora, overworld, 4L));
        assertTrue(SatelliteNetworkService.hasReservation(TEAM, first));

        SatelliteNetworkService.releaseBandwidth(TEAM, first);

        assertFalse(SatelliteNetworkService.hasReservation(TEAM, first));
        assertTrue(SatelliteNetworkService.reserveBandwidth(TEAM, new UUID(1L, 3L), mars, overworld, 6L));
    }

    // Product contract: a dropped reservation stays ended when satellite capacity returns.
    @Test
    void capacityLossEndsReservationsWithoutResumingThem() {
        CelestialObjectKey mars = CelestialObjectKey.registered(CelestialObjectId.MARS);
        CelestialObjectKey overworld = CelestialObjectKey.registered(CelestialObjectId.OVERWORLD);
        UUID first = new UUID(2L, 1L);
        UUID second = new UUID(2L, 2L);
        SatelliteDataBufferStore buffers = new SatelliteDataBufferStore();
        Map<CelestialObjectKey, Long> fullCapacity = capacity(
            CelestialObjectId.MARS,
            10L,
            CelestialObjectId.OVERWORLD,
            10L);
        SatelliteNetworkService.rebuild(TEAM, nodes(), fullCapacity, buffers);
        assertTrue(SatelliteNetworkService.reserveBandwidth(TEAM, first, mars, overworld, 6L));
        assertTrue(SatelliteNetworkService.reserveBandwidth(TEAM, second, mars, overworld, 4L));

        SatelliteNetworkService
            .rebuild(TEAM, nodes(), capacity(CelestialObjectId.MARS, 6L, CelestialObjectId.OVERWORLD, 6L), buffers);

        assertTrue(SatelliteNetworkService.hasReservation(TEAM, first));
        assertFalse(SatelliteNetworkService.hasReservation(TEAM, second));
        SatelliteNetworkService.rebuild(TEAM, nodes(), fullCapacity, buffers);
        assertFalse(SatelliteNetworkService.hasReservation(TEAM, second));
        assertTrue(SatelliteNetworkService.reserveBandwidth(TEAM, second, mars, overworld, 4L));
    }

    // Product contract: local control still requires a real satellite bandwidth pool.
    @Test
    void sameBodyReservationsRequireSatelliteCapacityAndRetireWithTheirTeam() {
        CelestialObjectKey mars = CelestialObjectKey.registered(CelestialObjectId.MARS);
        UUID reservation = new UUID(3L, 1L);
        assertFalse(SatelliteNetworkService.reserveBandwidth(TEAM, reservation, mars, mars, 1L));
        SatelliteNetworkService
            .rebuild(TEAM, nodes(), capacity(CelestialObjectId.MARS, 10L), new SatelliteDataBufferStore());
        assertFalse(SatelliteNetworkService.reserveBandwidth(TEAM, reservation, mars, mars, 0L));
        assertTrue(SatelliteNetworkService.reserveBandwidth(TEAM, reservation, mars, mars, 10L));
        assertFalse(SatelliteNetworkService.reserveBandwidth(TEAM, new UUID(3L, 2L), mars, mars, 1L));

        SatelliteNetworkService.mergeTeams(TEAM, new UUID(31L, 32L), List.of());

        assertFalse(SatelliteNetworkService.hasReservation(TEAM, reservation));
        assertFalse(SatelliteNetworkService.hasReservation(new UUID(31L, 32L), reservation));
        SatelliteNetworkService
            .rebuild(TEAM, nodes(), capacity(CelestialObjectId.MARS, 10L), new SatelliteDataBufferStore());
        assertTrue(SatelliteNetworkService.reserveBandwidth(TEAM, reservation, mars, mars, 10L));
        SatelliteNetworkService.clear();
        assertFalse(SatelliteNetworkService.hasReservation(TEAM, reservation));
    }

    // Integration contract: real data jobs and rebuild planning use bandwidth left after live reservations.
    @Test
    void dataJobsAndRebuildPlanningUseResidualCapacity() {
        CelestialObjectKey mars = CelestialObjectKey.registered(CelestialObjectId.MARS);
        CelestialObjectKey egora = CelestialObjectKey.registered(CelestialObjectId.EGORA);
        AutomatedFacility source = facility(CelestialObjectId.MARS);
        AutomatedFacility destination = facility(CelestialObjectId.EGORA);
        CelestialAssetStore.registerAsset(TEAM, source);
        CelestialAssetStore.registerAsset(TEAM, destination);
        ModuleDebugDataGenerator producer = addDebugModule(source);
        ModuleDebugDataGenerator consumer = addDebugModule(destination);
        producer.configure(ModuleDebugDataGenerator.Config.produce(SatelliteDataType.COMMUNICATION, 100L, 1));
        consumer.configure(ModuleDebugDataGenerator.Config.consume(SatelliteDataType.COMMUNICATION, 100L, 1, null));
        SatelliteNetworkService.refreshFacilityEndpoints(source);
        SatelliteNetworkService.refreshFacilityEndpoints(destination);
        SatelliteDataBufferStore buffers = SatelliteNetworkService.dataBuffers(TEAM);
        Map<CelestialObjectKey, Long> capacities = capacity(
            CelestialObjectId.MARS,
            10L,
            CelestialObjectId.OVERWORLD,
            10L,
            CelestialObjectId.EGORA,
            10L);
        SatelliteNetworkService.rebuild(TEAM, nodes(), capacities, buffers);
        UUID reservation = new UUID(4L, 1L);
        assertTrue(SatelliteNetworkService.reserveBandwidth(TEAM, reservation, mars, egora, 8L));

        SatelliteNetworkService.tickDataJobs();
        SatelliteNetworkState planned = SatelliteNetworkService.rebuild(TEAM, nodes(), capacities, buffers);

        assertEquals(1L, consumer.consumedDeciKb());
        assertEquals(10L, planned.usedKbps(mars));
        assertEquals(10L, planned.usedKbps(egora));
        assertTrue(
            planned.links()
                .stream()
                .allMatch(link -> link.usedKbps() <= link.capacityKbps()));
        assertTrue(SatelliteNetworkService.hasReservation(TEAM, reservation));
        SatelliteNetworkService.releaseBandwidth(TEAM, reservation);
        SatelliteNetworkService.tickDataJobs();
        assertEquals(6L, consumer.consumedDeciKb());
        SatelliteNetworkService.rebuild(TEAM, nodes(), capacities, buffers);
        assertTrue(SatelliteNetworkService.reserveBandwidth(TEAM, reservation, mars, egora, 10L));
        SatelliteNetworkService.tickDataJobs();
        assertEquals(6L, consumer.consumedDeciKb());
    }

    // Product contract: a topology change can reroute a live reservation without restarting its controller.
    @Test
    void topologyChangesRerouteExistingReservations() {
        CelestialObjectKey mars = CelestialObjectKey.registered(CelestialObjectId.MARS);
        CelestialObjectKey egora = CelestialObjectKey.registered(CelestialObjectId.EGORA);
        UUID reservation = new UUID(5L, 1L);
        SatelliteDataBufferStore buffers = new SatelliteDataBufferStore();
        SatelliteNetworkService.rebuild(
            TEAM,
            nodes(),
            capacity(CelestialObjectId.MARS, 10L, CelestialObjectId.OVERWORLD, 10L, CelestialObjectId.EGORA, 10L),
            buffers);
        assertTrue(SatelliteNetworkService.reserveBandwidth(TEAM, reservation, mars, egora, 10L));

        SatelliteNetworkService.rebuild(
            TEAM,
            List.of(node(CelestialObjectId.MARS, 0.0D), node(CelestialObjectId.EGORA, 20.0D)),
            capacity(CelestialObjectId.MARS, 10L, CelestialObjectId.EGORA, 10L),
            buffers);

        assertTrue(SatelliteNetworkService.hasReservation(TEAM, reservation));
        assertFalse(SatelliteNetworkService.reserveBandwidth(TEAM, new UUID(5L, 2L), mars, egora, 1L));
    }

    @Test
    void rebuildStoresDerivedSnapshotAndKeepsRevisionWhenContentIsUnchanged() {
        SatelliteNetworkState state = SatelliteNetworkService.rebuild(
            TEAM,
            nodes(),
            capacity(CelestialObjectId.MARS, 10L, CelestialObjectId.OVERWORLD, 10L),
            new SatelliteDataBufferStore());
        SatelliteNetworkState unchanged = SatelliteNetworkService.rebuild(
            TEAM,
            nodes(),
            capacity(CelestialObjectId.MARS, 10L, CelestialObjectId.OVERWORLD, 10L),
            new SatelliteDataBufferStore());

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
    void liveNetworkIncludesCommunicationBodiesAndExcludesProspectingOnlyBodies() {
        AsteroidFieldProfile profile = GalaxiaCelestialAPI.get(CelestialObjectId.FROZEN_BELT)
            .orElseThrow()
            .properties()
            .asteroidFieldProfile();
        var asteroids = AsteroidFieldResolver.resolveAll(CelestialObjectId.FROZEN_BELT, profile);
        CelestialObjectKey activeAsteroid = CelestialObjectKey.minorBody(
            asteroids.get(0)
                .id());
        CelestialObjectKey prospectingAsteroid = CelestialObjectKey.minorBody(
            asteroids.get(1)
                .id());
        CelestialObjectKey mars = CelestialObjectKey.registered(CelestialObjectId.MARS);
        CelestialObjectKey egora = CelestialObjectKey.registered(CelestialObjectId.EGORA);
        CelestialAssetStore.SERVER.addSatellites(TEAM, mars, SatelliteKind.COMMUNICATION, 1);
        CelestialAssetStore.SERVER.addSatellites(TEAM, activeAsteroid, SatelliteKind.COMMUNICATION, 1);
        CelestialAssetStore.SERVER.addSatellites(TEAM, prospectingAsteroid, SatelliteKind.PROSPECTING, 1);
        CelestialAssetStore.SERVER.addSatellites(TEAM, egora, SatelliteKind.PROSPECTING, 1);

        SatelliteNetworkState network = SatelliteNetworkService.rebuild(TEAM, 10.0D);

        assertEquals(
            Set.of(mars, activeAsteroid),
            network.bodies()
                .keySet());
        assertEquals(
            1,
            network.links()
                .size());

        CelestialAssetStore.SERVER.deleteSatellites(TEAM, activeAsteroid, SatelliteKind.COMMUNICATION);
        SatelliteNetworkState changed = SatelliteNetworkService.rebuild(TEAM, 10.0D);
        assertEquals(
            Set.of(mars),
            changed.bodies()
                .keySet());
        assertTrue(
            changed.links()
                .isEmpty());
    }

    @Test
    void rebuildIncrementsRevisionWhenCapacityChanges() {
        SatelliteNetworkState first = SatelliteNetworkService.rebuild(
            TEAM,
            nodes(),
            capacity(CelestialObjectId.MARS, 10L, CelestialObjectId.OVERWORLD, 10L),
            new SatelliteDataBufferStore());

        SatelliteNetworkState changed = SatelliteNetworkService.rebuild(
            TEAM,
            nodes(),
            capacity(CelestialObjectId.MARS, 20L, CelestialObjectId.OVERWORLD, 10L),
            new SatelliteDataBufferStore());

        assertEquals(first.revision() + 1, changed.revision());
        assertEquals(20L, changed.capacityKbps(CelestialObjectKey.registered(CelestialObjectId.MARS)));
    }

    @Test
    void rebuildUsesDataPlannerLoadForSnapshotUsage() {
        SatelliteDataBufferStore store = new SatelliteDataBufferStore();
        SatelliteDataKey prospecting = SatelliteDataKey.any(SatelliteDataType.PROSPECTING);
        store.finishProduction(
            CelestialObjectKey.registered(CelestialObjectId.MARS),
            prospecting,
            SatelliteBandwidthFormatter.kilobits(100L));
        AutomatedFacility destination = facility(CelestialObjectId.OVERWORLD);
        addDebugModule(destination)
            .configure(ModuleDebugDataGenerator.Config.consume(SatelliteDataType.PROSPECTING, 100L, 1, null));
        SatelliteNetworkService.refreshAssetEndpoints(TEAM, destination);

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
        CelestialAssetStore.SERVER.setSatelliteCount(
            TEAM,
            CelestialObjectKey.registered(CelestialObjectId.MARS),
            SatelliteKind.COMMUNICATION,
            1);
        CelestialAssetStore.SERVER.setSatelliteCount(
            TEAM,
            CelestialObjectKey.registered(CelestialObjectId.EGORA),
            SatelliteKind.COMMUNICATION,
            1);
        SatelliteNetworkService.rebuild(TEAM, 0.0D);

        SatelliteNetworkService.tickDataJobs();
        SatelliteNetworkState state = SatelliteNetworkService.rebuild(TEAM, 0.0D);

        assertEquals(CelestialObjectKey.registered(CelestialObjectId.EGORA), producer.detectedCounterpartBodyKey());
        assertEquals(CelestialObjectKey.registered(CelestialObjectId.MARS), consumer.detectedCounterpartBodyKey());
        SatelliteNetworkState.Link link = state.links()
            .get(0);
        long delivered = consumer.consumedDeciKb();
        assertTrue(delivered > 0L);
        assertTrue(
            delivered * 2L
                * link.quality()
                    .airtimeCost()
                <= 10L);
        assertEquals(
            delivered * 2L
                * link.quality()
                    .airtimeCost(),
            state.usedKbps(CelestialObjectKey.registered(CelestialObjectId.MARS)));
        assertEquals(
            delivered * 2L
                * link.quality()
                    .airtimeCost(),
            state.usedKbps(CelestialObjectKey.registered(CelestialObjectId.EGORA)));
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
        CelestialAssetStore.SERVER.setSatelliteCount(
            TEAM,
            CelestialObjectKey.registered(CelestialObjectId.MARS),
            SatelliteKind.COMMUNICATION,
            1);
        CelestialAssetStore.SERVER.setSatelliteCount(
            TEAM,
            CelestialObjectKey.registered(CelestialObjectId.EGORA),
            SatelliteKind.COMMUNICATION,
            1);
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

    // Integration contract: team merge transfers assets/endpoints but only the survivor retains buffered data.
    @Test
    void runtimeMergeTransfersAssetsAndRetiresOnlyConsumedNetworkData() {
        CelestialServerRuntime runtime = CelestialServerRuntime.create();
        UUID consumedTeam = new UUID(21L, 22L);
        AutomatedFacility survivingFacility = facility(CelestialObjectId.MARS);
        AutomatedFacility consumedFacility = facility(CelestialObjectId.EGORA);
        CelestialAssetStore.registerAsset(TEAM, survivingFacility);
        CelestialAssetStore.registerAsset(consumedTeam, consumedFacility);
        ModuleDebugDataGenerator survivingProducer = addDebugModule(survivingFacility);
        survivingProducer.configure(ModuleDebugDataGenerator.Config.produce(SatelliteDataType.RESEARCH, 70L, 1));
        addDebugModule(survivingFacility)
            .configure(ModuleDebugDataGenerator.Config.consume(SatelliteDataType.RESEARCH, 1L, 1, null));
        ModuleDebugDataGenerator consumedProducer = addDebugModule(consumedFacility);
        consumedProducer.configure(ModuleDebugDataGenerator.Config.produce(SatelliteDataType.COMMUNICATION, 40L, 1));
        addDebugModule(consumedFacility)
            .configure(ModuleDebugDataGenerator.Config.consume(SatelliteDataType.COMMUNICATION, 1L, 1, null));
        SatelliteNetworkService.refreshFacilityEndpoints(survivingFacility);
        SatelliteNetworkService.refreshFacilityEndpoints(consumedFacility);
        SatelliteNetworkService.tickDataJobs();
        SatelliteDataKey survivingKey = survivingProducer.producedKey(survivingFacility.celestialObjectKey);
        SatelliteDataKey consumedKey = consumedProducer.producedKey(consumedFacility.celestialObjectKey);
        assertFalse(SatelliteNetworkService.canStartProcess(TEAM, survivingFacility.celestialObjectKey, survivingKey));
        assertFalse(
            SatelliteNetworkService.canStartProcess(consumedTeam, consumedFacility.celestialObjectKey, consumedKey));

        runtime.mergeTeams(consumedTeam, TEAM);

        assertSame(consumedFacility, CelestialAssetStore.findAsset(consumedFacility.assetId));
        assertEquals(TEAM, CelestialAssetStore.getTeamId(consumedFacility.assetId));
        assertEquals(TEAM, CelestialAssetStore.getTeamId(survivingFacility.assetId));
        assertTrue(
            CelestialAssetStore.getTeamAssets(consumedTeam)
                .isEmpty());
        assertFalse(SatelliteNetworkService.canStartProcess(TEAM, survivingFacility.celestialObjectKey, survivingKey));
        assertTrue(
            SatelliteNetworkService.canStartProcess(consumedTeam, consumedFacility.celestialObjectKey, consumedKey));
        assertTrue(SatelliteNetworkService.canStartProcess(TEAM, consumedFacility.celestialObjectKey, consumedKey));

        SatelliteNetworkService.tickDataJobs();
        assertFalse(SatelliteNetworkService.canStartProcess(TEAM, consumedFacility.celestialObjectKey, consumedKey));
        assertTrue(
            SatelliteNetworkService.canStartProcess(consumedTeam, consumedFacility.celestialObjectKey, consumedKey));
    }

    @Test
    void worldLoadClearsCachedSatelliteNetworkState(@TempDir Path tempDir) {
        SatelliteNetworkService.rebuild(
            TEAM,
            nodes(),
            capacity(CelestialObjectId.MARS, 10L, CelestialObjectId.OVERWORLD, 10L),
            new SatelliteDataBufferStore());

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
        addModule(facility, module);
        return (ModuleDebugDataGenerator) module.component();
    }
}
