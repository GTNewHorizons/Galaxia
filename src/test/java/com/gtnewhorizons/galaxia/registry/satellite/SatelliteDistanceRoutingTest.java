package com.gtnewhorizons.galaxia.registry.satellite;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;

class SatelliteDistanceRoutingTest {

    private static final CelestialObjectKey A = CelestialObjectKey.registered(CelestialObjectId.MARS);
    private static final CelestialObjectKey B = CelestialObjectKey.registered(CelestialObjectId.EGORA);
    private static final CelestialObjectKey C = CelestialObjectKey.registered(CelestialObjectId.OVERWORLD);

    @Test
    void aFullSourceBufferStillSharesBandwidthBetweenEqualConsumers() {
        UUID team = UUID.randomUUID();
        var network = new SatelliteNetworkState(
            team,
            1,
            Map.of(
                A,
                new SatelliteNetworkState.Body(A, 40L, 0L),
                B,
                new SatelliteNetworkState.Body(B, 40L, 0L),
                C,
                new SatelliteNetworkState.Body(C, 40L, 0L)),
            List.of(
                new SatelliteNetworkState.Link(A, B, 40L, 0L, 0L, 0L),
                new SatelliteNetworkState.Link(A, C, 40L, 0L, 0L, 0L)));
        var data = SatelliteDataKey.origin(SatelliteDataType.RESEARCH, A);
        var buffers = new SatelliteDataBufferStore();
        buffers.finishProduction(A, data, 400L);
        var plan = SatelliteDataTransferPlanner.plan(
            team,
            network,
            buffers,
            List.of(
                new SatelliteDataTransferPlanner.Demand(new UUID(0L, 1L), B, data, 400L),
                new SatelliteDataTransferPlanner.Demand(new UUID(0L, 2L), C, data, 400L)));

        assertEquals(
            10L,
            plan.transfers()
                .stream()
                .filter(
                    t -> t.destinationBodyKey()
                        .equals(B))
                .mapToLong(SatelliteDataTransferPlanner.Transfer::deciKb)
                .sum());
        assertEquals(
            10L,
            plan.transfers()
                .stream()
                .filter(
                    t -> t.destinationBodyKey()
                        .equals(C))
                .mapToLong(SatelliteDataTransferPlanner.Transfer::deciKb)
                .sum());
    }

    @Test
    void relayPaysTheWorseAdjacentCostOnce() {
        var ab = new SatelliteNetworkState.Link(A, B, 1000L, 0L, 0L, 0L, SatelliteNetworkState.Quality.LOCAL);
        var bc = new SatelliteNetworkState.Link(B, C, 1000L, 0L, 0L, 0L, SatelliteNetworkState.Quality.LONG_RANGE);
        var plan = plan(List.of(ab, bc), 1000L);

        assertEquals(
            250L,
            plan.transfers()
                .stream()
                .mapToLong(SatelliteDataTransferPlanner.Transfer::deciKb)
                .sum());
        assertEquals(
            500L,
            plan.usedByBody()
                .get(A));
        assertEquals(
            1000L,
            plan.usedByBody()
                .get(B));
        assertEquals(
            1000L,
            plan.usedByBody()
                .get(C));
        assertEquals(
            500L,
            plan.usedByEdge()
                .get(ab.asEdge()));
        assertEquals(
            1000L,
            plan.usedByEdge()
                .get(bc.asEdge()));
    }

    @Test
    void betterRelayRouteFillsBeforeDeepSpaceOverflow() {
        var ab = new SatelliteNetworkState.Link(A, B, 1000L, 0L, 0L, 0L, SatelliteNetworkState.Quality.LOCAL);
        var bc = new SatelliteNetworkState.Link(B, C, 1000L, 0L, 0L, 0L, SatelliteNetworkState.Quality.LOCAL);
        var ac = new SatelliteNetworkState.Link(A, C, 1000L, 0L, 0L, 0L, SatelliteNetworkState.Quality.DEEP_SPACE);
        var plan = plan(List.of(ac, ab, bc), 40L);

        assertEquals(
            2,
            plan.transfers()
                .size());
        assertEquals(
            2,
            plan.transfers()
                .get(0)
                .path()
                .size());
        assertEquals(
            List.of(ac.asEdge()),
            plan.transfers()
                .get(1)
                .path());
        assertEquals(
            140L,
            plan.transfers()
                .stream()
                .mapToLong(SatelliteDataTransferPlanner.Transfer::deciKb)
                .sum());
        assertEquals(
            40L,
            plan.usedByBody()
                .get(B));
        assertEquals(
            1000L,
            plan.usedByBody()
                .get(A));
        assertEquals(
            1000L,
            plan.usedByBody()
                .get(C));
    }

    private static SatelliteDataTransferPlanner.Plan plan(List<SatelliteNetworkState.Link> links, long relayCapacity) {
        UUID team = UUID.randomUUID();
        var network = new SatelliteNetworkState(
            team,
            1,
            Map.of(
                A,
                new SatelliteNetworkState.Body(A, 1000L, 0L),
                B,
                new SatelliteNetworkState.Body(B, relayCapacity, 0L),
                C,
                new SatelliteNetworkState.Body(C, 1000L, 0L)),
            links);
        var data = SatelliteDataKey.origin(SatelliteDataType.RESEARCH, A);
        var buffers = new SatelliteDataBufferStore();
        buffers.finishProduction(A, data, 10000L);
        return SatelliteDataTransferPlanner.plan(
            team,
            network,
            buffers,
            List.of(new SatelliteDataTransferPlanner.Demand(UUID.randomUUID(), C, data, 10000L)));
    }

    @Test
    void deepSpaceUsesFourUnitsOfBandwidthPerDeliveredUnit() {
        var a = CelestialObjectKey.registered(CelestialObjectId.MARS);
        var b = CelestialObjectKey.registered(CelestialObjectId.OVERWORLD);
        UUID team = UUID.randomUUID();
        var link = new SatelliteNetworkState.Link(a, b, 1000L, 0L, 0L, 0L, SatelliteNetworkState.Quality.DEEP_SPACE);
        var network = new SatelliteNetworkState(
            team,
            1,
            Map.of(a, new SatelliteNetworkState.Body(a, 1000L, 0L), b, new SatelliteNetworkState.Body(b, 1000L, 0L)),
            List.of(link));
        var data = SatelliteDataKey.origin(SatelliteDataType.RESEARCH, a);
        var buffers = new SatelliteDataBufferStore();
        buffers.finishProduction(a, data, 10000L);

        var plan = SatelliteDataTransferPlanner.plan(
            team,
            network,
            buffers,
            List.of(new SatelliteDataTransferPlanner.Demand(UUID.randomUUID(), b, data, 10000L)));

        assertEquals(
            125L,
            plan.transfers()
                .stream()
                .mapToLong(SatelliteDataTransferPlanner.Transfer::deciKb)
                .sum());
        assertEquals(
            1000L,
            plan.usedByEdge()
                .get(link.asEdge()));
        assertEquals(
            1000L,
            plan.usedByBody()
                .get(a));
        assertEquals(
            1000L,
            plan.usedByBody()
                .get(b));
    }
}
