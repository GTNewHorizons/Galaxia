package com.gtnewhorizons.galaxia.registry.satellite;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;

/**
 * Ticks data-producing and data-consuming debug modules.
 *
 * <p>
 * Modules only expose their desired mode through their component config. This service resolves those configs into
 * produced buffers, demand buffers, and short-lived link usage that the starmap can visualize.
 */
public final class SatelliteDataJobService {

    private static final int TICKS_PER_SECOND = 20;

    private SatelliteDataJobService() {}

    public record Usage(Map<SatelliteNetworkGraph.Edge, Long> usedByEdge,
        Map<SatelliteNetworkGraph.DirectedEdge, Long> directedUsedByEdge) {

        public Usage {
            usedByEdge = Map.copyOf(usedByEdge == null ? Map.of() : usedByEdge);
            directedUsedByEdge = Map.copyOf(directedUsedByEdge == null ? Map.of() : directedUsedByEdge);
        }
    }

    /*
     * One service tick does three things in order: refresh debug module detection, advance producer jobs that are
     * allowed
     * to start, then move already-buffered data through the current satellite network.
     */
    public static Usage tickEndpointsUsage(UUID teamId, List<SatelliteDataEndpointRegistry.Endpoint> endpoints,
        SatelliteDataBufferStore store, SatelliteNetworkState networkState) {
        if (teamId == null || endpoints == null || store == null || networkState == null) {
            return new Usage(Map.of(), Map.of());
        }
        updateDetectedCounterparts(endpoints);
        for (SatelliteDataEndpointRegistry.Endpoint producer : endpoints) {
            if (!producer.produces()) continue;
            List<SatelliteDataEndpointRegistry.Endpoint> consumers = matchingConsumers(producer, endpoints);
            if (consumers.isEmpty()) {
                producer.clearProduction();
                continue;
            }
            if (!store
                .canStart(producer.bodyKey(), producer.producedKey(), networkState.capacityKbps(producer.bodyKey()))) {
                producer.clearProduction();
                continue;
            }
            if (!producer.advanceProduction()) continue;

            completeProduction(producer, store);
        }
        return transferQueuedData(teamId, endpoints, store, networkState);
    }

    /*
     * Detection is UI-facing state stored on the debug modules. It is derived from the same matching rules as
     * production
     * so the debug panel cannot claim a producer/consumer exists when the transfer code would ignore it.
     */
    private static void updateDetectedCounterparts(List<SatelliteDataEndpointRegistry.Endpoint> endpoints) {
        for (SatelliteDataEndpointRegistry.Endpoint endpoint : endpoints) {
            updateDetectedCounterpart(endpoint, null);
        }
        for (SatelliteDataEndpointRegistry.Endpoint producer : endpoints) {
            if (!producer.produces()) continue;
            List<SatelliteDataEndpointRegistry.Endpoint> consumers = matchingConsumers(producer, endpoints);
            if (consumers.isEmpty()) continue;
            updateDetectedCounterpart(
                producer,
                consumers.get(0)
                    .bodyKey());
            for (SatelliteDataEndpointRegistry.Endpoint consumer : consumers) {
                updateDetectedCounterpart(consumer, producer.bodyKey());
            }
        }
    }

    private static void completeProduction(SatelliteDataEndpointRegistry.Endpoint producer,
        SatelliteDataBufferStore store) {
        long amount = producer.amountDeciKb();
        store.finishProduction(producer.bodyKey(), producer.producedKey(), amount);
        producer.clearProduction();
    }

    /*
     * Planner output is applied here rather than inside the planner so the planner can stay a pure capacity
     * calculation.
     * Delivered amounts are then pushed into matching consumers and reported as active link usage for this tick.
     */
    private static Usage transferQueuedData(UUID teamId, List<SatelliteDataEndpointRegistry.Endpoint> endpoints,
        SatelliteDataBufferStore store, SatelliteNetworkState networkState) {
        List<SatelliteDataTransferPlanner.Demand> demands = endpoints.stream()
            .filter(SatelliteDataEndpointRegistry.Endpoint::consumes)
            .map(SatelliteDataEndpointRegistry.Endpoint::demand)
            .toList();
        Map<ModuleInstance.ID, SatelliteDataEndpointRegistry.Endpoint> sinks = new HashMap<>();
        for (SatelliteDataEndpointRegistry.Endpoint endpoint : endpoints) sinks.put(endpoint.id(), endpoint);
        SatelliteDataTransferPlanner.Plan plan = SatelliteDataTransferPlanner
            .plan(teamId, networkState, store, demands);
        Map<SatelliteNetworkGraph.Edge, Long> usedByEdge = new HashMap<>();
        Map<SatelliteNetworkGraph.DirectedEdge, Long> directedUsedByEdge = new HashMap<>();
        for (SatelliteDataTransferPlanner.Transfer transfer : plan.transfers()) {
            SatelliteDataEndpointRegistry.Endpoint sink = sinks.get(transfer.sinkId());
            if (sink == null) continue;
            long available = store.pendingDeciKb(transfer.sourceBodyKey(), transfer.sourceKey());
            long accepted = sink.accept(transfer.demandKey(), Math.min(transfer.deciKb(), available));
            long drained = store.drain(transfer.sourceBodyKey(), transfer.sourceKey(), accepted);
            if (drained <= 0L) continue;
            mergeTransferUsage(usedByEdge, directedUsedByEdge, transfer, drained);
        }
        return new Usage(usedByEdge, directedUsedByEdge);
    }

    private static void mergeTransferUsage(Map<SatelliteNetworkGraph.Edge, Long> usedByEdge,
        Map<SatelliteNetworkGraph.DirectedEdge, Long> directedUsedByEdge,
        SatelliteDataTransferPlanner.Transfer transfer, long deliveredDeciKb) {
        long usedKbps = Math.min(transfer.bottleneckKbps(), Math.max(1L, deliveredDeciKb * TICKS_PER_SECOND / 10L));
        if (usedKbps <= 0L) return;
        for (SatelliteNetworkGraph.Edge edge : transfer.path()) {
            usedByEdge.merge(edge, usedKbps, SatelliteDataJobService::addSaturated);
        }
        SatelliteDataTransferPlanner.mergeDirectedUsage(
            directedUsedByEdge,
            transfer.sourceBodyKey(),
            transfer.destinationBodyKey(),
            transfer.path(),
            usedKbps);
    }

    private static long addSaturated(long left, long right) {
        if (left > Long.MAX_VALUE - right) return Long.MAX_VALUE;
        return left + right;
    }

    /*
     * If any consumer asks for a concrete origin, generic "any origin" consumers wait. This preserves the rule that
     * origin-specific demand must be satisfied before pooled demand for the same data type.
     */
    private static List<SatelliteDataEndpointRegistry.Endpoint> matchingConsumers(
        SatelliteDataEndpointRegistry.Endpoint producer, List<SatelliteDataEndpointRegistry.Endpoint> endpoints) {
        SatelliteDataKey producedKey = producer.producedKey();
        List<SatelliteDataEndpointRegistry.Endpoint> exact = new ArrayList<>();
        List<SatelliteDataEndpointRegistry.Endpoint> any = new ArrayList<>();
        for (SatelliteDataEndpointRegistry.Endpoint endpoint : endpoints) {
            if (!endpoint.consumes()) continue;
            SatelliteDataKey demandKey = endpoint.demandKey();
            if (!demandKey.matchesProduced(producedKey)) continue;
            if (demandKey.hasOrigin()) {
                exact.add(endpoint);
            } else {
                any.add(endpoint);
            }
        }
        return exact.isEmpty() ? any : exact;
    }

    private static void updateDetectedCounterpart(SatelliteDataEndpointRegistry.Endpoint endpoint,
        CelestialObjectKey bodyKey) {
        if (java.util.Objects.equals(endpoint.counterpartBodyKey(), bodyKey)) return;
        endpoint.updateCounterpart(bodyKey);
    }
}
