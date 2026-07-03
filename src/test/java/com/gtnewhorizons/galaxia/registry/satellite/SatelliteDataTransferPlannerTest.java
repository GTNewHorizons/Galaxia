package com.gtnewhorizons.galaxia.registry.satellite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.MinorCelestialBodyId;

final class SatelliteDataTransferPlannerTest {

    private static final UUID TEAM = UUID.fromString("00000000-0000-0000-0000-000000000107");

    @Test
    void concreteOriginDemandTransfersBeforeAnyDemandForSameType() {
        SatelliteDataBufferStore store = new SatelliteDataBufferStore();
        SatelliteDataKey egoraProspecting = SatelliteDataKey
            .origin(SatelliteDataType.PROSPECTING, CelestialObjectId.EGORA);
        SatelliteDataKey anyProspecting = SatelliteDataKey.any(SatelliteDataType.PROSPECTING);
        store.finishProduction(TEAM, CelestialObjectId.EGORA, egoraProspecting, kb(100L));
        store.requestData(TEAM, CelestialObjectId.FROZEN_BELT, egoraProspecting, kb(100L));
        store.requestData(TEAM, CelestialObjectId.MARS, anyProspecting, kb(100L));

        SatelliteDataTransferPlanner.Plan plan = SatelliteDataTransferPlanner.plan(TEAM, network(), store);
        apply(store, plan);

        assertEquals(kb(100L) - 5L, store.pendingDeciKb(TEAM, CelestialObjectId.EGORA, egoraProspecting));
        assertEquals(kb(100L) - 5L, store.pendingDemandDeciKb(TEAM, CelestialObjectId.FROZEN_BELT, egoraProspecting));
        assertEquals(kb(100L), store.pendingDemandDeciKb(TEAM, CelestialObjectId.MARS, anyProspecting));
    }

    @Test
    void equalReachableDemandSplitsSourceDataEvenly() {
        SatelliteDataBufferStore store = new SatelliteDataBufferStore();
        SatelliteDataKey prospecting = SatelliteDataKey.any(SatelliteDataType.PROSPECTING);
        store.finishProduction(TEAM, CelestialObjectId.MARS, prospecting, kb(100L));
        store.requestData(TEAM, CelestialObjectId.EGORA, prospecting, kb(100L));
        store.requestData(TEAM, CelestialObjectId.FROZEN_BELT, prospecting, kb(100L));

        SatelliteDataTransferPlanner.Plan plan = SatelliteDataTransferPlanner.plan(TEAM, sourceFanoutNetwork(), store);
        apply(store, plan);

        assertEquals(kb(100L) - 5L, store.pendingDemandDeciKb(TEAM, CelestialObjectId.EGORA, prospecting));
        assertEquals(kb(100L) - 5L, store.pendingDemandDeciKb(TEAM, CelestialObjectId.FROZEN_BELT, prospecting));
    }

    @Test
    void plannedTransfersReportUsedLinkBandwidthForNetworkSnapshot() {
        SatelliteDataBufferStore store = new SatelliteDataBufferStore();
        SatelliteDataKey prospecting = SatelliteDataKey.any(SatelliteDataType.PROSPECTING);
        SatelliteNetworkGraph.Edge edge = new SatelliteNetworkGraph.Edge(
            CelestialObjectId.MARS,
            CelestialObjectId.EGORA);
        SatelliteNetworkGraph.DirectedEdge forward = new SatelliteNetworkGraph.DirectedEdge(
            CelestialObjectId.MARS,
            CelestialObjectId.EGORA);
        SatelliteNetworkGraph.DirectedEdge reverse = new SatelliteNetworkGraph.DirectedEdge(
            CelestialObjectId.EGORA,
            CelestialObjectId.MARS);
        store.finishProduction(TEAM, CelestialObjectId.MARS, prospecting, kb(100L));
        store.requestData(TEAM, CelestialObjectId.EGORA, prospecting, kb(100L));

        SatelliteDataTransferPlanner.Plan plan = SatelliteDataTransferPlanner.plan(TEAM, network(), store);

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
        store.finishProduction(TEAM, CelestialObjectId.MARS, research, kb(100L));
        store.finishProduction(TEAM, CelestialObjectId.FROZEN_BELT, research, kb(100L));
        store.requestData(TEAM, CelestialObjectId.OVERWORLD, research, kb(100L));

        SatelliteDataTransferPlanner.Plan plan = SatelliteDataTransferPlanner.plan(TEAM, convergingNetwork(), store);

        long transferredToOverworld = plan.transfers()
            .stream()
            .filter(transfer -> transfer.destinationBodyId() == CelestialObjectId.OVERWORLD)
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
        store.finishProduction(TEAM, CelestialObjectId.MARS, research, amount);
        store.finishProduction(TEAM, CelestialObjectId.EGORA, research, amount);
        store.requestData(TEAM, CelestialObjectId.MARS, research, amount);
        store.requestData(TEAM, CelestialObjectId.EGORA, research, amount);

        SatelliteDataTransferPlanner.Plan plan = SatelliteDataTransferPlanner.plan(TEAM, reciprocalNetwork(), store);
        apply(store, plan);

        assertTrue(
            plan.usedByEdge()
                .isEmpty());
        assertTrue(
            plan.directedUsedByEdge()
                .isEmpty());
        assertEquals(0L, store.pendingDemandDeciKb(TEAM, CelestialObjectId.MARS, research));
        assertEquals(0L, store.pendingDemandDeciKb(TEAM, CelestialObjectId.EGORA, research));
    }

    @Test
    void minorBodyDataCanRouteThroughSatelliteNetwork() {
        SatelliteDataBufferStore store = new SatelliteDataBufferStore();
        SatelliteDataKey research = SatelliteDataKey.any(SatelliteDataType.RESEARCH);
        CelestialObjectKey asteroid = asteroidKey(3);
        CelestialObjectKey belt = CelestialObjectKey.registered(CelestialObjectId.FROZEN_BELT);
        store.finishProduction(TEAM, asteroid, research, kb(100L));
        store.requestData(TEAM, belt, research, kb(100L));

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
            store);

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

    private static SatelliteNetworkState networkWithMarsCapacity(long marsCapacityKbps) {
        return SatelliteNetworkCalculator.fromGraph(
            TEAM,
            1,
            List.of(
                node(CelestialObjectId.MARS, 0.0D, 0.0D),
                node(CelestialObjectId.EGORA, 10.0D, 0.0D),
                node(CelestialObjectId.FROZEN_BELT, 0.0D, 10.0D)),
            List.of(
                new SatelliteNetworkGraph.Edge(CelestialObjectId.MARS, CelestialObjectId.EGORA),
                new SatelliteNetworkGraph.Edge(CelestialObjectId.MARS, CelestialObjectId.FROZEN_BELT),
                new SatelliteNetworkGraph.Edge(CelestialObjectId.EGORA, CelestialObjectId.FROZEN_BELT)),
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
                new SatelliteNetworkGraph.Edge(CelestialObjectId.MARS, CelestialObjectId.OVERWORLD),
                new SatelliteNetworkGraph.Edge(CelestialObjectId.FROZEN_BELT, CelestialObjectId.OVERWORLD)),
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
                new SatelliteNetworkGraph.Edge(CelestialObjectId.MARS, CelestialObjectId.EGORA),
                new SatelliteNetworkGraph.Edge(CelestialObjectId.MARS, CelestialObjectId.FROZEN_BELT)),
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
            List.of(new SatelliteNetworkGraph.Edge(CelestialObjectId.MARS, CelestialObjectId.EGORA)),
            Map.of(key(CelestialObjectId.MARS), 40L, key(CelestialObjectId.EGORA), 40L),
            Map.of());
    }

    private static SatelliteNetworkGraph.Node node(CelestialObjectId id, double x, double y) {
        return new SatelliteNetworkGraph.Node(id, x, y, 1.0D);
    }

    private static CelestialObjectKey asteroidKey(int index) {
        return CelestialObjectKey.minorBody(new MinorCelestialBodyId(CelestialObjectId.FROZEN_BELT, index));
    }

    private static CelestialObjectKey key(CelestialObjectId id) {
        return CelestialObjectKey.registered(id);
    }

    private static void apply(SatelliteDataBufferStore store, SatelliteDataTransferPlanner.Plan plan) {
        for (SatelliteDataTransferPlanner.Transfer transfer : plan.transfers()) {
            store.transfer(
                transfer.teamId(),
                transfer.sourceBodyId(),
                transfer.sourceKey(),
                transfer.destinationBodyId(),
                transfer.demandKey(),
                transfer.deciKb());
        }
    }

    private static long kb(long value) {
        return SatelliteBandwidthFormatter.kilobits(value);
    }
}
