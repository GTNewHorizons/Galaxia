package com.gtnewhorizons.galaxia.registry.satellite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.MinorCelestialBodyId;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;

final class SatelliteDataTransferPlannerTest {

    private static final UUID TEAM = UUID.fromString("00000000-0000-0000-0000-000000000107");

    @Test
    void opposingSharedEdgeDoesNotMoveRemoteSinksToProducerBodies() {
        SatelliteDataBufferStore store = new SatelliteDataBufferStore();
        SatelliteDataKey research = SatelliteDataKey.any(SatelliteDataType.RESEARCH);
        // A -> B -> C -> D, producers at A/D and consumers at B/C
        var a = key(CelestialObjectId.MARS);
        var b = key(CelestialObjectId.EGORA);
        var c = key(CelestialObjectId.FROZEN_BELT);
        var d = key(CelestialObjectId.OVERWORLD);
        var network = SatelliteNetworkCalculator.fromGraph(
            TEAM,
            1,
            List.of(
                node(CelestialObjectId.MARS, 0, 0),
                node(CelestialObjectId.EGORA, 10, 0),
                node(CelestialObjectId.FROZEN_BELT, 20, 0),
                node(CelestialObjectId.OVERWORLD, 30, 0)),
            List.of(
                new SatelliteNetworkGraph.Edge(a, b),
                new SatelliteNetworkGraph.Edge(b, c),
                new SatelliteNetworkGraph.Edge(c, d)),
            Map.of(a, 100L, b, 100L, c, 100L, d, 100L),
            Map.of());
        store.finishProduction(a, research, 4L);
        store.finishProduction(d, research, 4L);
        var demands = List.of(
            demand(1L, CelestialObjectId.EGORA, research, 100L),
            demand(2L, CelestialObjectId.FROZEN_BELT, research, 100L));
        var plan = SatelliteDataTransferPlanner.plan(TEAM, network, store, demands);
        var bodiesBySink = demands.stream()
            .collect(
                java.util.stream.Collectors
                    .toMap(SatelliteDataTransferPlanner.Demand::sinkId, SatelliteDataTransferPlanner.Demand::bodyKey));
        assertFalse(
            plan.transfers()
                .isEmpty());
        for (var transfer : plan.transfers()) {
            assertEquals(bodiesBySink.get(transfer.sinkId()), transfer.destinationBodyKey());
            assertFalse(
                transfer.path()
                    .isEmpty());
        }
    }

    @Test
    void concreteOriginDemandTransfersBeforeAnyDemandForSameType() {
        SatelliteDataBufferStore store = new SatelliteDataBufferStore();
        SatelliteDataKey egoraProspecting = SatelliteDataKey
            .origin(SatelliteDataType.PROSPECTING, CelestialObjectKey.registered(CelestialObjectId.EGORA));
        SatelliteDataKey anyProspecting = SatelliteDataKey.any(SatelliteDataType.PROSPECTING);
        store.finishProduction(CelestialObjectKey.registered(CelestialObjectId.EGORA), egoraProspecting, kb(100L));
        List<SatelliteDataTransferPlanner.Demand> demands = List.of(
            demand(1L, CelestialObjectId.FROZEN_BELT, egoraProspecting, kb(100L)),
            demand(2L, CelestialObjectId.MARS, anyProspecting, kb(100L)));

        SatelliteDataTransferPlanner.Plan plan = SatelliteDataTransferPlanner.plan(TEAM, network(), store, demands);
        apply(store, plan);

        assertEquals(
            kb(100L) - 5L,
            store.pendingDeciKb(CelestialObjectKey.registered(CelestialObjectId.EGORA), egoraProspecting));
        assertEquals(
            CelestialObjectKey.registered(CelestialObjectId.FROZEN_BELT),
            plan.transfers()
                .get(0)
                .destinationBodyKey());
    }

    @Test
    void equalReachableDemandSplitsSourceDataEvenly() {
        SatelliteDataBufferStore store = new SatelliteDataBufferStore();
        SatelliteDataKey prospecting = SatelliteDataKey.any(SatelliteDataType.PROSPECTING);
        store.finishProduction(CelestialObjectKey.registered(CelestialObjectId.MARS), prospecting, kb(100L));
        List<SatelliteDataTransferPlanner.Demand> demands = List.of(
            demand(1L, CelestialObjectId.EGORA, prospecting, kb(100L)),
            demand(2L, CelestialObjectId.FROZEN_BELT, prospecting, kb(100L)));

        SatelliteDataTransferPlanner.Plan plan = SatelliteDataTransferPlanner
            .plan(TEAM, sourceFanoutNetwork(), store, demands);

        assertEquals(5L, transferredTo(plan, CelestialObjectId.EGORA));
        assertEquals(5L, transferredTo(plan, CelestialObjectId.FROZEN_BELT));
    }

    @Test
    void plannedTransfersReportUsedLinkBandwidthForNetworkSnapshot() {
        SatelliteDataBufferStore store = new SatelliteDataBufferStore();
        SatelliteDataKey prospecting = SatelliteDataKey.any(SatelliteDataType.PROSPECTING);
        SatelliteNetworkGraph.Edge edge = new SatelliteNetworkGraph.Edge(
            CelestialObjectKey.registered(CelestialObjectId.MARS),
            CelestialObjectKey.registered(CelestialObjectId.EGORA));
        SatelliteNetworkGraph.DirectedEdge forward = new SatelliteNetworkGraph.DirectedEdge(
            CelestialObjectKey.registered(CelestialObjectId.MARS),
            CelestialObjectKey.registered(CelestialObjectId.EGORA));
        SatelliteNetworkGraph.DirectedEdge reverse = new SatelliteNetworkGraph.DirectedEdge(
            CelestialObjectKey.registered(CelestialObjectId.EGORA),
            CelestialObjectKey.registered(CelestialObjectId.MARS));
        store.finishProduction(CelestialObjectKey.registered(CelestialObjectId.MARS), prospecting, kb(100L));
        SatelliteDataTransferPlanner.Plan plan = SatelliteDataTransferPlanner
            .plan(TEAM, network(), store, List.of(demand(1L, CelestialObjectId.EGORA, prospecting, kb(100L))));

        assertEquals(
            10L,
            plan.usedByEdge()
                .get(edge));
        assertEquals(
            10L,
            plan.directedUsedByEdge()
                .get(forward));
        assertNull(
            plan.directedUsedByEdge()
                .get(reverse));
    }

    @Test
    void multipleRoutesIntoSameDestinationShareDestinationBandwidth() {
        SatelliteDataBufferStore store = new SatelliteDataBufferStore();
        SatelliteDataKey research = SatelliteDataKey.any(SatelliteDataType.RESEARCH);
        store.finishProduction(CelestialObjectKey.registered(CelestialObjectId.MARS), research, kb(100L));
        store.finishProduction(CelestialObjectKey.registered(CelestialObjectId.FROZEN_BELT), research, kb(100L));
        SatelliteDataTransferPlanner.Plan plan = SatelliteDataTransferPlanner.plan(
            TEAM,
            convergingNetwork(),
            store,
            List.of(demand(1L, CelestialObjectId.OVERWORLD, research, kb(100L))));

        long transferredToOverworld = plan.transfers()
            .stream()
            .filter(
                transfer -> transfer.destinationBodyKey()
                    .equals(CelestialObjectKey.registered(CelestialObjectId.OVERWORLD)))
            .mapToLong(SatelliteDataTransferPlanner.Transfer::deciKb)
            .sum();
        assertEquals(5L, transferredToOverworld);
        assertEquals(
            10L,
            plan.usedByBody()
                .get(key(CelestialObjectId.OVERWORLD)));
    }

    @Test
    void opposingSameDemandTransfersOnSameLinkAreSatisfiedLocally() {
        SatelliteDataBufferStore store = new SatelliteDataBufferStore();
        SatelliteDataKey research = SatelliteDataKey.any(SatelliteDataType.RESEARCH);
        long amount = kb(1L);
        store.finishProduction(CelestialObjectKey.registered(CelestialObjectId.MARS), research, amount);
        store.finishProduction(CelestialObjectKey.registered(CelestialObjectId.EGORA), research, amount);
        List<SatelliteDataTransferPlanner.Demand> demands = List.of(
            demand(1L, CelestialObjectId.MARS, research, amount),
            demand(2L, CelestialObjectId.EGORA, research, amount));
        SatelliteDataTransferPlanner.Plan plan = SatelliteDataTransferPlanner
            .plan(TEAM, reciprocalNetwork(), store, demands);
        apply(store, plan);

        assertTrue(
            plan.usedByEdge()
                .isEmpty());
        assertTrue(
            plan.directedUsedByEdge()
                .isEmpty());
        assertEquals(0L, store.pendingDeciKb(key(CelestialObjectId.MARS), research));
        assertEquals(0L, store.pendingDeciKb(key(CelestialObjectId.EGORA), research));
    }

    @Test
    void minorBodyDataCanRouteThroughSatelliteNetwork() {
        SatelliteDataBufferStore store = new SatelliteDataBufferStore();
        SatelliteDataKey research = SatelliteDataKey.any(SatelliteDataType.RESEARCH);
        CelestialObjectKey asteroid = asteroidKey(3);
        CelestialObjectKey belt = CelestialObjectKey.registered(CelestialObjectId.FROZEN_BELT);
        store.finishProduction(asteroid, research, kb(100L));
        SatelliteDataTransferPlanner.Plan plan = SatelliteDataTransferPlanner.plan(
            TEAM,
            SatelliteNetworkCalculator.fromGraph(
                TEAM,
                5,
                List.of(
                    new SatelliteNetworkGraph.Node(asteroid, belt, 1.1D, 0.0D, 0.0D, 1.0D),
                    new SatelliteNetworkGraph.Node(belt, null, 1.0D, 10.0D, 0.0D, 4.0D)),
                List.of(new SatelliteNetworkGraph.Edge(asteroid, belt)),
                Map.of(asteroid, 10L, belt, 10L),
                Map.of()),
            store,
            List.of(
                new SatelliteDataTransferPlanner.Demand(
                    new ModuleInstance.ID(new UUID(0L, 1L)),
                    belt,
                    research,
                    kb(100L))));

        assertEquals(
            1,
            plan.transfers()
                .size());
        assertEquals(
            asteroid,
            plan.transfers()
                .get(0)
                .sourceBodyKey());
        assertEquals(
            belt,
            plan.transfers()
                .get(0)
                .destinationBodyKey());
        assertEquals(
            10L,
            plan.usedByBody()
                .get(asteroid));
        assertEquals(
            10L,
            plan.usedByBody()
                .get(belt));
    }

    private static SatelliteNetworkState network() {
        return networkWithMarsCapacity(10L);
    }

    // Product regression: local priority must not starve remote demand when buffered data remains.
    @Test
    void recurringLocalDemandLeavesSurplusForRemoteReceivers() {
        SatelliteDataBufferStore store = new SatelliteDataBufferStore();
        SatelliteDataKey research = SatelliteDataKey.any(SatelliteDataType.RESEARCH);
        List<SatelliteDataTransferPlanner.Demand> demands = List
            .of(demand(1L, CelestialObjectId.MARS, research, 10L), demand(2L, CelestialObjectId.EGORA, research, 30L));
        for (int tick = 0; tick < 2; tick++) {
            store.finishProduction(key(CelestialObjectId.MARS), research, 30L);

            SatelliteDataTransferPlanner.Plan plan = SatelliteDataTransferPlanner
                .plan(TEAM, reciprocalNetwork(), store, demands);

            assertEquals(10L, transferredTo(plan, CelestialObjectId.MARS));
            assertEquals(20L, transferredTo(plan, CelestialObjectId.EGORA));
            assertEquals(
                40L,
                plan.usedByBody()
                    .get(key(CelestialObjectId.MARS)));
            apply(store, plan);
            assertEquals(0L, store.pendingDeciKb(key(CelestialObjectId.MARS), research));
        }

        store.finishProduction(key(CelestialObjectId.MARS), research, 5L);
        SatelliteDataTransferPlanner.Plan scarce = SatelliteDataTransferPlanner
            .plan(TEAM, reciprocalNetwork(), store, demands);
        assertEquals(5L, transferredTo(scarce, CelestialObjectId.MARS));
        assertEquals(0L, transferredTo(scarce, CelestialObjectId.EGORA));
        assertTrue(
            scarce.usedByEdge()
                .isEmpty());
    }

    private static SatelliteNetworkState networkWithMarsCapacity(long marsCapacityKbps) {
        return SatelliteNetworkCalculator.fromGraph(
            TEAM,
            1,
            List.of(
                node(CelestialObjectId.MARS, 0.0D, 0.0D),
                node(CelestialObjectId.EGORA, 10.0D, 0.0D),
                node(CelestialObjectId.FROZEN_BELT, 0.0D, 10.0D)),
            List.of(
                new SatelliteNetworkGraph.Edge(
                    CelestialObjectKey.registered(CelestialObjectId.MARS),
                    CelestialObjectKey.registered(CelestialObjectId.EGORA)),
                new SatelliteNetworkGraph.Edge(
                    CelestialObjectKey.registered(CelestialObjectId.MARS),
                    CelestialObjectKey.registered(CelestialObjectId.FROZEN_BELT)),
                new SatelliteNetworkGraph.Edge(
                    CelestialObjectKey.registered(CelestialObjectId.EGORA),
                    CelestialObjectKey.registered(CelestialObjectId.FROZEN_BELT))),
            Map.of(
                key(CelestialObjectId.MARS),
                marsCapacityKbps,
                key(CelestialObjectId.EGORA),
                10L,
                key(CelestialObjectId.FROZEN_BELT),
                10L),
            Map.of());
    }

    private static SatelliteNetworkState convergingNetwork() {
        return SatelliteNetworkCalculator.fromGraph(
            TEAM,
            2,
            List.of(
                node(CelestialObjectId.MARS, 0.0D, 0.0D),
                node(CelestialObjectId.FROZEN_BELT, 20.0D, 0.0D),
                node(CelestialObjectId.OVERWORLD, 10.0D, 0.0D)),
            List.of(
                new SatelliteNetworkGraph.Edge(
                    CelestialObjectKey.registered(CelestialObjectId.MARS),
                    CelestialObjectKey.registered(CelestialObjectId.OVERWORLD)),
                new SatelliteNetworkGraph.Edge(
                    CelestialObjectKey.registered(CelestialObjectId.FROZEN_BELT),
                    CelestialObjectKey.registered(CelestialObjectId.OVERWORLD))),
            Map.of(
                key(CelestialObjectId.MARS),
                10L,
                key(CelestialObjectId.FROZEN_BELT),
                10L,
                key(CelestialObjectId.OVERWORLD),
                10L),
            Map.of());
    }

    private static SatelliteNetworkState sourceFanoutNetwork() {
        return SatelliteNetworkCalculator.fromGraph(
            TEAM,
            3,
            List.of(
                node(CelestialObjectId.MARS, 0.0D, 0.0D),
                node(CelestialObjectId.EGORA, 10.0D, 0.0D),
                node(CelestialObjectId.FROZEN_BELT, 0.0D, 10.0D)),
            List.of(
                new SatelliteNetworkGraph.Edge(
                    CelestialObjectKey.registered(CelestialObjectId.MARS),
                    CelestialObjectKey.registered(CelestialObjectId.EGORA)),
                new SatelliteNetworkGraph.Edge(
                    CelestialObjectKey.registered(CelestialObjectId.MARS),
                    CelestialObjectKey.registered(CelestialObjectId.FROZEN_BELT))),
            Map.of(
                key(CelestialObjectId.MARS),
                20L,
                key(CelestialObjectId.EGORA),
                10L,
                key(CelestialObjectId.FROZEN_BELT),
                10L),
            Map.of());
    }

    private static SatelliteNetworkState reciprocalNetwork() {
        return SatelliteNetworkCalculator.fromGraph(
            TEAM,
            4,
            List.of(node(CelestialObjectId.MARS, 0.0D, 0.0D), node(CelestialObjectId.EGORA, 10.0D, 0.0D)),
            List.of(
                new SatelliteNetworkGraph.Edge(
                    CelestialObjectKey.registered(CelestialObjectId.MARS),
                    CelestialObjectKey.registered(CelestialObjectId.EGORA))),
            Map.of(key(CelestialObjectId.MARS), 40L, key(CelestialObjectId.EGORA), 40L),
            Map.of());
    }

    private static SatelliteNetworkGraph.Node node(CelestialObjectId id, double x, double y) {
        return new SatelliteNetworkGraph.Node(
            CelestialObjectKey.registered(id),
            CelestialObjectKey.registered(id),
            0.0D,
            x,
            y,
            1.0D);
    }

    private static CelestialObjectKey asteroidKey(int index) {
        return CelestialObjectKey.minorBody(new MinorCelestialBodyId(CelestialObjectId.FROZEN_BELT, index));
    }

    private static CelestialObjectKey key(CelestialObjectId id) {
        return CelestialObjectKey.registered(id);
    }

    private static void apply(SatelliteDataBufferStore store, SatelliteDataTransferPlanner.Plan plan) {
        for (SatelliteDataTransferPlanner.Transfer transfer : plan.transfers()) {
            store.drain(transfer.sourceBodyKey(), transfer.sourceKey(), transfer.deciKb());
        }
    }

    private static SatelliteDataTransferPlanner.Demand demand(long id, CelestialObjectId bodyId, SatelliteDataKey key,
        long amount) {
        return new SatelliteDataTransferPlanner.Demand(
            new ModuleInstance.ID(new UUID(0L, id)),
            CelestialObjectKey.registered(bodyId),
            key,
            amount);
    }

    private static long transferredTo(SatelliteDataTransferPlanner.Plan plan, CelestialObjectId bodyId) {
        return plan.transfers()
            .stream()
            .filter(
                transfer -> transfer.destinationBodyKey()
                    .equals(CelestialObjectKey.registered(bodyId)))
            .mapToLong(SatelliteDataTransferPlanner.Transfer::deciKb)
            .sum();
    }

    private static long kb(long value) {
        return SatelliteBandwidthFormatter.kilobits(value);
    }
}
