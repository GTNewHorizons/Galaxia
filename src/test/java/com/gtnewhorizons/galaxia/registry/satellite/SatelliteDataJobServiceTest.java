package com.gtnewhorizons.galaxia.registry.satellite;

import static com.gtnewhorizons.galaxia.registry.outpost.FacilityTestFixtures.addModule;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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

        tickUsage(TEAM, List.of(facility), store, emptyNetwork());

        assertEquals(SatelliteBandwidthFormatter.kilobits(10L), consumer.consumedDeciKb());
        assertEquals(
            0L,
            store.pendingDeciKb(
                CelestialObjectKey.registered(CelestialObjectId.MARS),
                SatelliteDataKey
                    .origin(SatelliteDataType.PROSPECTING, CelestialObjectKey.registered(CelestialObjectId.MARS))));
    }

    @Test
    void localConsumerAcceptsOnlyItsDemandAndBuffersTheRemainder() {
        SatelliteDataBufferStore store = new SatelliteDataBufferStore();
        AutomatedFacility facility = facility(CelestialObjectId.MARS);
        ModuleDebugDataGenerator producer = addDebugModule(facility);
        ModuleDebugDataGenerator consumer = addDebugModule(facility);
        producer.configure(ModuleDebugDataGenerator.Config.produce(SatelliteDataType.PROSPECTING, 10L, 1));
        consumer.configure(ModuleDebugDataGenerator.Config.consume(SatelliteDataType.PROSPECTING, 1L, 1, null));

        tickUsage(TEAM, List.of(facility), store, emptyNetwork());

        assertEquals(SatelliteBandwidthFormatter.kilobits(1L), consumer.consumedDeciKb());
        assertEquals(
            SatelliteBandwidthFormatter.kilobits(9L),
            store.pendingDeciKb(
                CelestialObjectKey.registered(CelestialObjectId.MARS),
                SatelliteDataKey
                    .origin(SatelliteDataType.PROSPECTING, CelestialObjectKey.registered(CelestialObjectId.MARS))));
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
            ModuleDebugDataGenerator.Config
                .consume(SatelliteDataType.PROSPECTING, 10L, 1, CelestialObjectKey.registered(CelestialObjectId.MARS)));
        anyConsumer.configure(ModuleDebugDataGenerator.Config.consume(SatelliteDataType.PROSPECTING, 10L, 1, null));

        tickUsage(
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
            CelestialObjectKey.registered(CelestialObjectId.MARS),
            CelestialObjectKey.registered(CelestialObjectId.EGORA));

        Map<SatelliteNetworkGraph.Edge, Long> usedByEdge = tickUsage(
            TEAM,
            List.of(source, destination),
            store,
            network(CelestialObjectId.MARS, CelestialObjectId.EGORA, CelestialObjectId.OVERWORLD)).usedByEdge();

        assertEquals(10L, usedByEdge.get(edge));
        assertEquals(5L, consumer.consumedDeciKb());
    }

    @Test
    void oneRemoteTransferIsCreditedToOnlyOneMatchingConsumer() {
        SatelliteDataBufferStore store = new SatelliteDataBufferStore();
        AutomatedFacility source = facility(CelestialObjectId.MARS);
        AutomatedFacility destination = facility(CelestialObjectId.EGORA);
        ModuleDebugDataGenerator producer = addDebugModule(source);
        ModuleDebugDataGenerator firstConsumer = addDebugModule(destination);
        ModuleDebugDataGenerator secondConsumer = addDebugModule(destination);
        producer.configure(ModuleDebugDataGenerator.Config.produce(SatelliteDataType.PROSPECTING, 10L, 1));
        firstConsumer.configure(ModuleDebugDataGenerator.Config.consume(SatelliteDataType.PROSPECTING, 10L, 1, null));
        secondConsumer.configure(ModuleDebugDataGenerator.Config.consume(SatelliteDataType.PROSPECTING, 10L, 1, null));

        tickUsage(
            TEAM,
            List.of(source, destination),
            store,
            network(CelestialObjectId.MARS, CelestialObjectId.EGORA, CelestialObjectId.OVERWORLD));

        assertEquals(5L, firstConsumer.consumedDeciKb() + secondConsumer.consumedDeciKb());
    }

    @Test
    void twoRemoteSourcesCannotEachSpendTheSameConcreteDemand() {
        SatelliteDataBufferStore store = new SatelliteDataBufferStore();
        AutomatedFacility firstSource = facility(CelestialObjectId.MARS);
        AutomatedFacility secondSource = facility(CelestialObjectId.OVERWORLD);
        AutomatedFacility destination = facility(CelestialObjectId.EGORA);
        ModuleDebugDataGenerator firstProducer = addDebugModule(firstSource);
        ModuleDebugDataGenerator secondProducer = addDebugModule(secondSource);
        ModuleDebugDataGenerator consumer = addDebugModule(destination);
        firstProducer.configure(ModuleDebugDataGenerator.Config.produce(SatelliteDataType.RESEARCH, 10L, 1));
        secondProducer.configure(ModuleDebugDataGenerator.Config.produce(SatelliteDataType.RESEARCH, 10L, 1));
        consumer.configure(ModuleDebugDataGenerator.Config.consume(SatelliteDataType.RESEARCH, 10L, 1, null));

        tickUsage(TEAM, List.of(firstSource, secondSource, destination), store, highBandwidthNetwork());

        assertEquals(SatelliteBandwidthFormatter.kilobits(10L), consumer.consumedDeciKb());
        assertEquals(
            SatelliteBandwidthFormatter.kilobits(10L),
            store.pendingDeciKb(
                CelestialObjectKey.registered(CelestialObjectId.MARS),
                SatelliteDataKey.any(SatelliteDataType.RESEARCH))
                + store.pendingDeciKb(
                    CelestialObjectKey.registered(CelestialObjectId.OVERWORLD),
                    SatelliteDataKey.any(SatelliteDataType.RESEARCH)));
    }

    @Test
    void parallelQueuedTransfersOnSameLinkDoNotReportMoreThanLinkCapacity() {
        SatelliteDataBufferStore store = new SatelliteDataBufferStore();
        AutomatedFacility source = facility(CelestialObjectId.MARS);
        AutomatedFacility destination = facility(CelestialObjectId.EGORA);
        SatelliteDataKey research = SatelliteDataKey.any(SatelliteDataType.RESEARCH);
        SatelliteDataKey communication = SatelliteDataKey.any(SatelliteDataType.COMMUNICATION);
        SatelliteNetworkGraph.Edge sharedEdge = new SatelliteNetworkGraph.Edge(
            CelestialObjectKey.registered(CelestialObjectId.MARS),
            CelestialObjectKey.registered(CelestialObjectId.EGORA));
        store.finishProduction(
            CelestialObjectKey.registered(CelestialObjectId.MARS),
            research,
            SatelliteBandwidthFormatter.kilobits(100L));
        store.finishProduction(
            CelestialObjectKey.registered(CelestialObjectId.MARS),
            communication,
            SatelliteBandwidthFormatter.kilobits(100L));
        addDebugModule(source).configure(ModuleDebugDataGenerator.Config.produce(SatelliteDataType.RESEARCH, 1L, 100));
        addDebugModule(destination)
            .configure(ModuleDebugDataGenerator.Config.consume(SatelliteDataType.RESEARCH, 100L, 1, null));
        addDebugModule(destination)
            .configure(ModuleDebugDataGenerator.Config.consume(SatelliteDataType.COMMUNICATION, 100L, 1, null));

        SatelliteDataJobService.Usage usage = tickUsage(
            TEAM,
            List.of(source, destination),
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

        tickUsage(
            TEAM,
            List.of(source, destination),
            store,
            network(CelestialObjectId.MARS, CelestialObjectId.EGORA, CelestialObjectId.OVERWORLD));

        assertEquals(CelestialObjectKey.registered(CelestialObjectId.EGORA), producer.detectedCounterpartBodyKey());
        assertEquals(CelestialObjectKey.registered(CelestialObjectId.MARS), consumer.detectedCounterpartBodyKey());
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
        store.finishProduction(
            CelestialObjectKey.registered(CelestialObjectId.MARS),
            key,
            SatelliteBandwidthFormatter.kilobits(400L));
        tickUsage(
            TEAM,
            List.of(source, destination),
            store,
            network(CelestialObjectId.MARS, CelestialObjectId.EGORA, CelestialObjectId.OVERWORLD));

        assertEquals(
            SatelliteBandwidthFormatter.kilobits(400L) - 5L,
            store.pendingDeciKb(CelestialObjectKey.registered(CelestialObjectId.MARS), key));
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
        endpoints.refreshFacility(source);
        endpoints.refreshFacility(destination);

        SatelliteDataJobService.tickEndpointsUsage(
            TEAM,
            endpoints.endpoints(),
            store,
            network(CelestialObjectId.MARS, CelestialObjectId.EGORA, CelestialObjectId.OVERWORLD));
        assertEquals(CelestialObjectKey.registered(CelestialObjectId.EGORA), producer.detectedCounterpartBodyKey());

        consumer.configure(ModuleDebugDataGenerator.Config.consume(SatelliteDataType.PROSPECTING, 10L, 1, null));
        endpoints.refreshFacility(destination);

        SatelliteDataJobService.tickEndpointsUsage(
            TEAM,
            endpoints.endpoints(),
            store,
            network(CelestialObjectId.MARS, CelestialObjectId.EGORA, CelestialObjectId.OVERWORLD));

        assertNull(producer.detectedCounterpartBodyKey());
        assertNull(consumer.detectedCounterpartBodyKey());
    }

    @Test
    void producerWaitsWhileExistingOverCapacityBufferCanDrain() {
        SatelliteDataBufferStore store = new SatelliteDataBufferStore();
        AutomatedFacility source = facility(CelestialObjectId.MARS);
        AutomatedFacility destination = facility(CelestialObjectId.EGORA);
        ModuleDebugDataGenerator producer = addDebugModule(source);
        ModuleDebugDataGenerator consumer = addDebugModule(destination);
        producer.configure(ModuleDebugDataGenerator.Config.produce(SatelliteDataType.PROSPECTING, 10L, 1));
        consumer.configure(ModuleDebugDataGenerator.Config.consume(SatelliteDataType.PROSPECTING, 10L, 1, null));
        store.finishProduction(
            CelestialObjectKey.registered(CelestialObjectId.MARS),
            SatelliteDataKey
                .origin(SatelliteDataType.PROSPECTING, CelestialObjectKey.registered(CelestialObjectId.MARS)),
            SatelliteBandwidthFormatter.kilobits(11L));

        tickUsage(
            TEAM,
            List.of(source, destination),
            store,
            network(CelestialObjectId.MARS, CelestialObjectId.EGORA, CelestialObjectId.OVERWORLD));

        assertEquals(0, producer.jobProgressTicks());
        assertEquals(5L, consumer.consumedDeciKb());
    }

    @Test
    void producerWithoutMatchingConsumerDoesNotGenerateData() {
        SatelliteDataBufferStore store = new SatelliteDataBufferStore();
        AutomatedFacility source = facility(CelestialObjectId.MARS);
        ModuleDebugDataGenerator producer = addDebugModule(source);
        producer.configure(ModuleDebugDataGenerator.Config.produce(SatelliteDataType.PROSPECTING, 10L, 1));

        tickUsage(
            TEAM,
            List.of(source),
            store,
            network(CelestialObjectId.MARS, CelestialObjectId.EGORA, CelestialObjectId.OVERWORLD));

        assertEquals(0, producer.jobProgressTicks());
        assertEquals(
            0L,
            store.pendingDeciKb(
                CelestialObjectKey.registered(CelestialObjectId.MARS),
                SatelliteDataKey
                    .origin(SatelliteDataType.PROSPECTING, CelestialObjectKey.registered(CelestialObjectId.MARS))));
    }

    @Test
    void nonProspectingProductionUsesOriginlessDataKey() {
        ModuleDebugDataGenerator producer = new ModuleDebugDataGenerator();
        producer.configure(ModuleDebugDataGenerator.Config.produce(SatelliteDataType.COMMUNICATION, 10L, 1));

        assertEquals(
            SatelliteDataKey.any(SatelliteDataType.COMMUNICATION),
            producer.producedKey(CelestialObjectKey.registered(CelestialObjectId.MARS)));
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

    private static SatelliteDataJobService.Usage tickUsage(UUID teamId, List<AutomatedFacility> facilities,
        SatelliteDataBufferStore store, SatelliteNetworkState networkState) {
        SatelliteDataEndpointRegistry endpoints = new SatelliteDataEndpointRegistry();
        for (AutomatedFacility facility : facilities) endpoints.refreshFacility(facility);
        return SatelliteDataJobService.tickEndpointsUsage(teamId, endpoints.endpoints(), store, networkState);
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

    private static SatelliteNetworkState emptyNetwork() {
        return SatelliteNetworkCalculator.fromGraph(TEAM, 0, List.of(), List.of(), Map.of(), Map.of());
    }

    private static SatelliteNetworkState highBandwidthNetwork() {
        return SatelliteNetworkCalculator.fromGraph(
            TEAM,
            0,
            List.of(
                node(CelestialObjectId.MARS, 0.0D),
                node(CelestialObjectId.EGORA, 10.0D),
                node(CelestialObjectId.OVERWORLD, 20.0D)),
            List.of(
                new SatelliteNetworkGraph.Edge(key(CelestialObjectId.MARS), key(CelestialObjectId.EGORA)),
                new SatelliteNetworkGraph.Edge(key(CelestialObjectId.EGORA), key(CelestialObjectId.OVERWORLD))),
            Map.of(
                key(CelestialObjectId.MARS),
                1000L,
                key(CelestialObjectId.EGORA),
                1000L,
                key(CelestialObjectId.OVERWORLD),
                1000L),
            Map.of());
    }

    private static SatelliteNetworkState network(CelestialObjectId first, CelestialObjectId second,
        CelestialObjectId third) {
        return SatelliteNetworkCalculator.fromGraph(
            TEAM,
            0,
            List.of(node(first, 0.0D), node(second, 10.0D), node(third, 20.0D)),
            List.of(
                new SatelliteNetworkGraph.Edge(
                    CelestialObjectKey.registered(first),
                    CelestialObjectKey.registered(second)),
                new SatelliteNetworkGraph.Edge(
                    CelestialObjectKey.registered(first),
                    CelestialObjectKey.registered(third))),
            Map.of(key(first), 10L, key(second), 10L, key(third), 10L),
            Map.of());
    }

    private static SatelliteNetworkGraph.Node node(CelestialObjectId id, double x) {
        return new SatelliteNetworkGraph.Node(CelestialObjectKey.registered(id), null, id.ordinal(), x, 0.0D, 1.0D);
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
