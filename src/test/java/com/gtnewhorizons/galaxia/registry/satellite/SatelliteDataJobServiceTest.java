package com.gtnewhorizons.galaxia.registry.satellite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.MinorCelestialBodyId;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleDebugDataGenerator;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class SatelliteDataJobServiceTest {

    private static final UUID TEAM = UUID.fromString("00000000-0000-0000-0000-000000000120");

    @BeforeAll
    static void initModules() {
        GalaxiaTestBootstrap.ensureFacilityModules();
    }

    @Test
    void localConsumerReceivesFinishedProductionWithoutUsingSatelliteBuffer() {
        SatelliteDataBufferStore store = new SatelliteDataBufferStore();
        AutomatedFacility facility = facility(CelestialObjectId.MARS);
        ModuleDebugDataGenerator producer = addDebugModule(facility);
        ModuleDebugDataGenerator consumer = addDebugModule(facility);
        producer.configure(ModuleDebugDataGenerator.Config.produce(SatelliteDataType.PROSPECTING, 10L, 1));
        consumer.configure(ModuleDebugDataGenerator.Config.consume(SatelliteDataType.PROSPECTING, 10L, 1, null));

        SatelliteDataJobService.tick(TEAM, List.of(facility), store, emptyNetwork());

        assertEquals(SatelliteBandwidthFormatter.kilobits(10L), consumer.consumedDeciKb());
        assertEquals(
            0L,
            store.pendingDeciKb(
                TEAM,
                CelestialObjectId.MARS,
                SatelliteDataKey.origin(SatelliteDataType.PROSPECTING, CelestialObjectId.MARS)));
    }

    @Test
    void completedProductionPublishesDataJobEvent() {
        SatelliteDataBufferStore store = new SatelliteDataBufferStore();
        AutomatedFacility source = facility(CelestialObjectId.MARS);
        AutomatedFacility destination = facility(CelestialObjectId.EGORA);
        ModuleDebugDataGenerator producer = addDebugModule(source);
        ModuleDebugDataGenerator consumer = addDebugModule(destination);
        producer.configure(ModuleDebugDataGenerator.Config.produce(SatelliteDataType.PROSPECTING, 10L, 1));
        consumer.configure(ModuleDebugDataGenerator.Config.consume(SatelliteDataType.PROSPECTING, 10L, 1, null));
        SatelliteDataEndpointRegistry endpoints = new SatelliteDataEndpointRegistry();
        endpoints.refreshFacility(TEAM, source);
        endpoints.refreshFacility(TEAM, destination);
        List<SatelliteDataJobService.ProductionEvent> events = new ArrayList<>();

        SatelliteDataJobService.tickEndpointsUsage(
            TEAM,
            endpoints.endpoints(TEAM),
            store,
            network(CelestialObjectId.MARS, CelestialObjectId.EGORA, CelestialObjectId.OVERWORLD),
            events::add);

        assertEquals(1, events.size());
        SatelliteDataJobService.ProductionEvent event = events.get(0);
        assertEquals(TEAM, event.teamId());
        assertEquals(CelestialObjectKey.registered(CelestialObjectId.MARS), event.bodyKey());
        assertEquals(SatelliteDataKey.origin(SatelliteDataType.PROSPECTING, CelestialObjectId.MARS), event.key());
        assertEquals(SatelliteBandwidthFormatter.kilobits(10L), event.deciKb());
    }

    @Test
    void completedMinorBodyProductionPublishesKeyedDataJobEvent() {
        SatelliteDataBufferStore store = new SatelliteDataBufferStore();
        CelestialObjectKey asteroid = asteroidKey(7);
        CelestialObjectKey belt = CelestialObjectKey.registered(CelestialObjectId.FROZEN_BELT);
        AutomatedFacility source = facility(asteroid);
        AutomatedFacility destination = facility(belt);
        ModuleDebugDataGenerator producer = addDebugModule(source);
        ModuleDebugDataGenerator consumer = addDebugModule(destination);
        producer.configure(ModuleDebugDataGenerator.Config.produce(SatelliteDataType.PROSPECTING, 10L, 1));
        consumer.configure(ModuleDebugDataGenerator.Config.consume(SatelliteDataType.PROSPECTING, 10L, 1, null));
        SatelliteDataEndpointRegistry endpoints = new SatelliteDataEndpointRegistry();
        endpoints.refreshFacility(TEAM, source);
        endpoints.refreshFacility(TEAM, destination);
        List<SatelliteDataJobService.ProductionEvent> events = new ArrayList<>();

        SatelliteDataJobService
            .tickEndpointsUsage(TEAM, endpoints.endpoints(TEAM), store, network(asteroid, belt), events::add);

        assertEquals(1, events.size());
        SatelliteDataJobService.ProductionEvent event = events.get(0);
        assertEquals(TEAM, event.teamId());
        assertEquals(asteroid, event.bodyKey());
        assertEquals(SatelliteDataKey.origin(SatelliteDataType.PROSPECTING, asteroid), event.key());
        assertEquals(SatelliteBandwidthFormatter.kilobits(10L), event.deciKb());
    }

    @Test
    void originSpecificDemandReceivesProducedProspectingBeforeAnyDemand() {
        SatelliteDataBufferStore store = new SatelliteDataBufferStore();
        AutomatedFacility source = facility(CelestialObjectId.MARS);
        AutomatedFacility specificDestination = facility(CelestialObjectId.EGORA);
        AutomatedFacility anyDestination = facility(CelestialObjectId.OVERWORLD);
        ModuleDebugDataGenerator producer = addDebugModule(source);
        ModuleDebugDataGenerator specificConsumer = addDebugModule(specificDestination);
        ModuleDebugDataGenerator anyConsumer = addDebugModule(anyDestination);
        producer.configure(ModuleDebugDataGenerator.Config.produce(SatelliteDataType.PROSPECTING, 10L, 1));
        specificConsumer.configure(
            ModuleDebugDataGenerator.Config.consume(SatelliteDataType.PROSPECTING, 10L, 1, CelestialObjectId.MARS));
        anyConsumer.configure(ModuleDebugDataGenerator.Config.consume(SatelliteDataType.PROSPECTING, 10L, 1, null));

        SatelliteDataJobService.tick(
            TEAM,
            List.of(source, specificDestination, anyDestination),
            store,
            network(CelestialObjectId.MARS, CelestialObjectId.EGORA, CelestialObjectId.OVERWORLD));

        assertEquals(5L, specificConsumer.consumedDeciKb());
        assertEquals(0L, anyConsumer.consumedDeciKb());
    }

    @Test
    void remoteConsumerTransferReportsSatelliteBandwidthUsage() {
        SatelliteDataBufferStore store = new SatelliteDataBufferStore();
        AutomatedFacility source = facility(CelestialObjectId.MARS);
        AutomatedFacility destination = facility(CelestialObjectId.EGORA);
        ModuleDebugDataGenerator producer = addDebugModule(source);
        ModuleDebugDataGenerator consumer = addDebugModule(destination);
        producer.configure(ModuleDebugDataGenerator.Config.produce(SatelliteDataType.PROSPECTING, 10L, 1));
        consumer.configure(ModuleDebugDataGenerator.Config.consume(SatelliteDataType.PROSPECTING, 10L, 1, null));
        SatelliteNetworkGraph.Edge edge = new SatelliteNetworkGraph.Edge(
            CelestialObjectId.MARS,
            CelestialObjectId.EGORA);

        Map<SatelliteNetworkGraph.Edge, Long> usedByEdge = SatelliteDataJobService.tick(
            TEAM,
            List.of(source, destination),
            store,
            network(CelestialObjectId.MARS, CelestialObjectId.EGORA, CelestialObjectId.OVERWORLD));

        assertEquals(10L, usedByEdge.get(edge));
        assertEquals(5L, consumer.consumedDeciKb());
    }

    @Test
    void parallelQueuedTransfersOnSameLinkDoNotReportMoreThanLinkCapacity() {
        SatelliteDataBufferStore store = new SatelliteDataBufferStore();
        SatelliteDataKey research = SatelliteDataKey.any(SatelliteDataType.RESEARCH);
        SatelliteDataKey communication = SatelliteDataKey.any(SatelliteDataType.COMMUNICATION);
        SatelliteNetworkGraph.Edge sharedEdge = new SatelliteNetworkGraph.Edge(
            CelestialObjectId.MARS,
            CelestialObjectId.EGORA);
        store.finishProduction(TEAM, CelestialObjectId.MARS, research, SatelliteBandwidthFormatter.kilobits(100L));
        store.finishProduction(TEAM, CelestialObjectId.MARS, communication, SatelliteBandwidthFormatter.kilobits(100L));
        store.requestData(TEAM, CelestialObjectId.EGORA, research, SatelliteBandwidthFormatter.kilobits(100L));
        store.requestData(TEAM, CelestialObjectId.EGORA, communication, SatelliteBandwidthFormatter.kilobits(100L));

        SatelliteDataJobService.Usage usage = SatelliteDataJobService.tickEndpointsUsage(
            TEAM,
            List.of(),
            store,
            network(CelestialObjectId.MARS, CelestialObjectId.EGORA, CelestialObjectId.OVERWORLD));

        assertEquals(
            10L,
            usage.usedByEdge()
                .get(sharedEdge));
    }

    @Test
    void tickPublishesServerDetectedCounterparts() {
        SatelliteDataBufferStore store = new SatelliteDataBufferStore();
        AutomatedFacility source = facility(CelestialObjectId.MARS);
        AutomatedFacility destination = facility(CelestialObjectId.EGORA);
        ModuleDebugDataGenerator producer = addDebugModule(source);
        ModuleDebugDataGenerator consumer = addDebugModule(destination);
        producer.configure(ModuleDebugDataGenerator.Config.produce(SatelliteDataType.PROSPECTING, 10L, 1));
        consumer.configure(ModuleDebugDataGenerator.Config.consume(SatelliteDataType.PROSPECTING, 10L, 1, null));

        SatelliteDataJobService.tick(
            TEAM,
            List.of(source, destination),
            store,
            network(CelestialObjectId.MARS, CelestialObjectId.EGORA, CelestialObjectId.OVERWORLD));

        assertEquals(CelestialObjectId.EGORA, producer.detectedCounterpartBodyId());
        assertEquals(CelestialObjectId.MARS, consumer.detectedCounterpartBodyId());
    }

    @Test
    void queuedRemoteDataTransfersOneTickOfBandwidth() {
        SatelliteDataBufferStore store = new SatelliteDataBufferStore();
        AutomatedFacility source = facility(CelestialObjectId.MARS);
        AutomatedFacility destination = facility(CelestialObjectId.EGORA);
        ModuleDebugDataGenerator producer = addDebugModule(source);
        ModuleDebugDataGenerator consumer = addDebugModule(destination);
        SatelliteDataKey key = SatelliteDataKey.any(SatelliteDataType.RESEARCH);
        producer.configure(ModuleDebugDataGenerator.Config.produce(SatelliteDataType.RESEARCH, 10L, 100));
        consumer.configure(ModuleDebugDataGenerator.Config.consume(SatelliteDataType.RESEARCH, 400L, 1, null));
        store.finishProduction(TEAM, CelestialObjectId.MARS, key, SatelliteBandwidthFormatter.kilobits(400L));
        store.requestData(TEAM, CelestialObjectId.EGORA, key, SatelliteBandwidthFormatter.kilobits(400L));

        SatelliteDataJobService.tick(
            TEAM,
            List.of(source, destination),
            store,
            network(CelestialObjectId.MARS, CelestialObjectId.EGORA, CelestialObjectId.OVERWORLD));

        assertEquals(
            SatelliteBandwidthFormatter.kilobits(400L) - 5L,
            store.pendingDeciKb(TEAM, CelestialObjectId.MARS, key));
        assertEquals(5L, consumer.consumedDeciKb());
    }

    @Test
    void endpointIndexUpdatesMatchingWhenFacilityConfigChanges() {
        SatelliteDataBufferStore store = new SatelliteDataBufferStore();
        SatelliteDataEndpointRegistry endpoints = new SatelliteDataEndpointRegistry();
        AutomatedFacility source = facility(CelestialObjectId.MARS);
        AutomatedFacility destination = facility(CelestialObjectId.EGORA);
        ModuleDebugDataGenerator producer = addDebugModule(source);
        ModuleDebugDataGenerator consumer = addDebugModule(destination);
        producer.configure(ModuleDebugDataGenerator.Config.produce(SatelliteDataType.COMMUNICATION, 10L, 1));
        consumer.configure(ModuleDebugDataGenerator.Config.consume(SatelliteDataType.COMMUNICATION, 10L, 1, null));
        endpoints.refreshFacility(TEAM, source);
        endpoints.refreshFacility(TEAM, destination);

        SatelliteDataJobService.tickEndpoints(
            TEAM,
            endpoints.endpoints(TEAM),
            store,
            network(CelestialObjectId.MARS, CelestialObjectId.EGORA, CelestialObjectId.OVERWORLD));
        assertEquals(CelestialObjectId.EGORA, producer.detectedCounterpartBodyId());

        consumer.configure(ModuleDebugDataGenerator.Config.consume(SatelliteDataType.PROSPECTING, 10L, 1, null));
        endpoints.refreshFacility(TEAM, destination);

        SatelliteDataJobService.tickEndpoints(
            TEAM,
            endpoints.endpoints(TEAM),
            store,
            network(CelestialObjectId.MARS, CelestialObjectId.EGORA, CelestialObjectId.OVERWORLD));

        assertNull(producer.detectedCounterpartBodyId());
        assertNull(consumer.detectedCounterpartBodyId());
    }

    @Test
    void producerWaitsWhenSourceBufferForProducedKeyIsOverLocalLimit() {
        SatelliteDataBufferStore store = new SatelliteDataBufferStore();
        AutomatedFacility source = facility(CelestialObjectId.MARS);
        AutomatedFacility destination = facility(CelestialObjectId.EGORA);
        ModuleDebugDataGenerator producer = addDebugModule(source);
        ModuleDebugDataGenerator consumer = addDebugModule(destination);
        producer.configure(ModuleDebugDataGenerator.Config.produce(SatelliteDataType.PROSPECTING, 10L, 1));
        consumer.configure(ModuleDebugDataGenerator.Config.consume(SatelliteDataType.PROSPECTING, 10L, 1, null));
        store.finishProduction(
            TEAM,
            CelestialObjectId.MARS,
            SatelliteDataKey.origin(SatelliteDataType.PROSPECTING, CelestialObjectId.MARS),
            SatelliteBandwidthFormatter.kilobits(11L));

        SatelliteDataJobService.tick(
            TEAM,
            List.of(source, destination),
            store,
            network(CelestialObjectId.MARS, CelestialObjectId.EGORA, CelestialObjectId.OVERWORLD));

        assertEquals(0, producer.jobProgressTicks());
        assertEquals(0L, consumer.consumedDeciKb());
    }

    @Test
    void producerWithoutMatchingConsumerDoesNotGenerateData() {
        SatelliteDataBufferStore store = new SatelliteDataBufferStore();
        AutomatedFacility source = facility(CelestialObjectId.MARS);
        ModuleDebugDataGenerator producer = addDebugModule(source);
        producer.configure(ModuleDebugDataGenerator.Config.produce(SatelliteDataType.PROSPECTING, 10L, 1));

        SatelliteDataJobService.tick(
            TEAM,
            List.of(source),
            store,
            network(CelestialObjectId.MARS, CelestialObjectId.EGORA, CelestialObjectId.OVERWORLD));

        assertEquals(0, producer.jobProgressTicks());
        assertEquals(
            0L,
            store.pendingDeciKb(
                TEAM,
                CelestialObjectId.MARS,
                SatelliteDataKey.origin(SatelliteDataType.PROSPECTING, CelestialObjectId.MARS)));
    }

    @Test
    void nonProspectingProductionUsesOriginlessDataKey() {
        ModuleDebugDataGenerator producer = new ModuleDebugDataGenerator();
        producer.configure(ModuleDebugDataGenerator.Config.produce(SatelliteDataType.COMMUNICATION, 10L, 1));

        assertEquals(
            SatelliteDataKey.any(SatelliteDataType.COMMUNICATION),
            producer.producedKey(CelestialObjectId.MARS));
    }

    private static AutomatedFacility facility(CelestialObjectId bodyId) {
        return facility(CelestialObjectKey.registered(bodyId));
    }

    private static AutomatedFacility facility(CelestialObjectKey bodyKey) {
        return new AutomatedFacility(
            CelestialAsset.ID.create(),
            bodyKey,
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

    private static SatelliteNetworkState emptyNetwork() {
        return SatelliteNetworkCalculator.fromGraph(TEAM, 0, List.of(), List.of(), Map.of(), Map.of());
    }

    private static SatelliteNetworkState network(CelestialObjectId first, CelestialObjectId second,
        CelestialObjectId third) {
        return SatelliteNetworkCalculator.fromGraph(
            TEAM,
            0,
            List.of(node(first, 0.0D), node(second, 10.0D), node(third, 20.0D)),
            List.of(new SatelliteNetworkGraph.Edge(first, second), new SatelliteNetworkGraph.Edge(first, third)),
            Map.of(key(first), 10L, key(second), 10L, key(third), 10L),
            Map.of());
    }

    private static SatelliteNetworkGraph.Node node(CelestialObjectId id, double x) {
        return new SatelliteNetworkGraph.Node(id, null, id.ordinal(), x, 0.0D, 1.0D);
    }

    private static SatelliteNetworkState network(CelestialObjectKey source, CelestialObjectKey destination) {
        return SatelliteNetworkCalculator.fromGraph(
            TEAM,
            0,
            List.of(
                new SatelliteNetworkGraph.Node(source, destination, 1.0D, 0.0D, 0.0D, 1.0D),
                new SatelliteNetworkGraph.Node(destination, null, 2.0D, 10.0D, 0.0D, 1.0D)),
            List.of(new SatelliteNetworkGraph.Edge(source, destination)),
            Map.of(source, 10L, destination, 10L),
            Map.of());
    }

    private static CelestialObjectKey asteroidKey(int index) {
        return CelestialObjectKey.minorBody(new MinorCelestialBodyId(CelestialObjectId.FROZEN_BELT, index));
    }

    private static CelestialObjectKey key(CelestialObjectId id) {
        return CelestialObjectKey.registered(id);
    }
}
