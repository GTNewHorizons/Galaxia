package com.gtnewhorizons.galaxia.registry.satellite;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;

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

    public static Usage tickUsage(UUID teamId, List<AutomatedFacility> facilities, SatelliteDataBufferStore store,
        SatelliteNetworkState networkState) {
        SatelliteDataEndpointRegistry endpoints = new SatelliteDataEndpointRegistry();
        if (facilities != null) {
            for (AutomatedFacility facility : facilities) {
                endpoints.refreshFacility(teamId, facility);
            }
        }
        return tickEndpointsUsage(teamId, endpoints.endpoints(teamId), store, networkState);
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
        Map<SatelliteNetworkGraph.Edge, Long> usedByEdge = new HashMap<>();
        Map<SatelliteNetworkGraph.DirectedEdge, Long> directedUsedByEdge = new HashMap<>();
        updateDetectedCounterparts(endpoints);
        for (SatelliteDataEndpointRegistry.Endpoint producer : endpoints) {
            if (!producer.module()
                .enabled()
                || !producer.module()
                    .isProducer())
                continue;
            List<SatelliteDataEndpointRegistry.Endpoint> consumers = matchingConsumers(producer, endpoints);
            if (consumers.isEmpty()) {
                producer.module()
                    .clearJob();
                continue;
            }
            if (!hasLocalConsumer(producer, consumers) && !store.canStart(
                teamId,
                producer.bodyKey(),
                producedKey(producer),
                networkState.capacityKbps(producer.bodyKey()))) {
                producer.module()
                    .clearJob();
                continue;
            }
            producer.module()
                .advanceJob();
            if (!producer.module()
                .jobComplete()) continue;

            completeProduction(teamId, producer, consumers, store);
        }
        Usage transferUsage = transferQueuedData(teamId, endpoints, store, networkState);
        mergeUsage(usedByEdge, transferUsage.usedByEdge());
        mergeUsage(directedUsedByEdge, transferUsage.directedUsedByEdge());
        return new Usage(usedByEdge, directedUsedByEdge);
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
            if (!producer.module()
                .enabled()
                || !producer.module()
                    .isProducer())
                continue;
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

    private static boolean hasLocalConsumer(SatelliteDataEndpointRegistry.Endpoint producer,
        List<SatelliteDataEndpointRegistry.Endpoint> consumers) {
        for (SatelliteDataEndpointRegistry.Endpoint consumer : consumers) {
            if (consumer.bodyKey()
                .equals(producer.bodyKey())) return true;
        }
        return false;
    }

    private static void completeProduction(UUID teamId, SatelliteDataEndpointRegistry.Endpoint producer,
        List<SatelliteDataEndpointRegistry.Endpoint> consumers, SatelliteDataBufferStore store) {
        long amount = producer.module()
            .amountDeciKb();
        SatelliteDataKey producedKey = producedKey(producer);
        /*
         * Same-body consumers bypass the satellite network entirely. The produced data is accepted locally and no
         * bandwidth usage is reported, so colocated debug modules behave like a local machine chain instead of a
         * network transfer.
         */
        for (SatelliteDataEndpointRegistry.Endpoint consumer : consumers) {
            if (consumer.bodyKey()
                .equals(producer.bodyKey())) {
                consumeAndMarkDirty(consumer, amount);
                producer.module()
                    .clearJob();
                return;
            }
        }

        /*
         * Remote consumers receive demand entries rather than being ticked directly here. The network service rebuild
         * turns produced buffers plus demand buffers into one bandwidth-limited transfer plan for this team.
         */
        store.finishProduction(teamId, producer.bodyKey(), producedKey, amount);
        for (SatelliteDataEndpointRegistry.Endpoint consumer : consumers) {
            store.requestData(
                teamId,
                consumer.bodyKey(),
                consumer.module()
                    .demandKey(),
                amount);
        }
        producer.module()
            .clearJob();
    }

    /*
     * Planner output is applied here rather than inside the planner so the planner can stay a pure capacity
     * calculation.
     * Delivered amounts are then pushed into matching consumers and reported as active link usage for this tick.
     */
    private static Usage transferQueuedData(UUID teamId, List<SatelliteDataEndpointRegistry.Endpoint> endpoints,
        SatelliteDataBufferStore store, SatelliteNetworkState networkState) {
        SatelliteDataTransferPlanner.Plan plan = SatelliteDataTransferPlanner.plan(teamId, networkState, store);
        Map<SatelliteNetworkGraph.Edge, Long> usedByEdge = new HashMap<>();
        Map<SatelliteNetworkGraph.DirectedEdge, Long> directedUsedByEdge = new HashMap<>();
        for (SatelliteDataTransferPlanner.Transfer transfer : plan.transfers()) {
            long delivered = store.transfer(
                transfer.teamId(),
                transfer.sourceBodyKey(),
                transfer.sourceKey(),
                transfer.destinationBodyKey(),
                transfer.demandKey(),
                transfer.deciKb());
            if (delivered <= 0L) continue;
            mergeTransferUsage(usedByEdge, directedUsedByEdge, transfer);
            for (SatelliteDataEndpointRegistry.Endpoint consumer : endpoints) {
                if (!consumer.module()
                    .enabled()
                    || !consumer.module()
                        .isConsumer())
                    continue;
                if (!consumer.bodyKey()
                    .equals(transfer.destinationBodyKey())) continue;
                if (!consumer.module()
                    .demandKey()
                    .equals(transfer.demandKey())) continue;
                consumeAndMarkDirty(consumer, delivered);
            }
        }
        return new Usage(usedByEdge, directedUsedByEdge);
    }

    private static void mergeTransferUsage(Map<SatelliteNetworkGraph.Edge, Long> usedByEdge,
        Map<SatelliteNetworkGraph.DirectedEdge, Long> directedUsedByEdge,
        SatelliteDataTransferPlanner.Transfer transfer) {
        long usedKbps = Math.min(transfer.bottleneckKbps(), Math.max(1L, transfer.deciKb() * TICKS_PER_SECOND / 10L));
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

    private static <T> void mergeUsage(Map<T, Long> target, Map<T, Long> source) {
        for (Map.Entry<T, Long> entry : source.entrySet()) {
            long amount = entry.getValue() == null ? 0L : entry.getValue();
            if (amount > 0L) target.merge(entry.getKey(), amount, SatelliteDataJobService::addSaturated);
        }
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
        SatelliteDataKey producedKey = producedKey(producer);
        List<SatelliteDataEndpointRegistry.Endpoint> exact = new ArrayList<>();
        List<SatelliteDataEndpointRegistry.Endpoint> any = new ArrayList<>();
        for (SatelliteDataEndpointRegistry.Endpoint endpoint : endpoints) {
            if (!endpoint.module()
                .enabled()
                || !endpoint.module()
                    .isConsumer())
                continue;
            SatelliteDataKey demandKey = endpoint.module()
                .demandKey();
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
        if (java.util.Objects.equals(
            endpoint.module()
                .detectedCounterpartBodyKey(),
            bodyKey)) return;
        endpoint.module()
            .updateDetectedCounterpart(bodyKey);
        endpoint.facility()
            .markModuleDirty(endpoint.instance().id);
    }

    private static SatelliteDataKey producedKey(SatelliteDataEndpointRegistry.Endpoint producer) {
        return producer.module()
            .producedKey(producer.bodyKey());
    }

    private static void consumeAndMarkDirty(SatelliteDataEndpointRegistry.Endpoint consumer, long deciKb) {
        if (deciKb <= 0L) return;
        consumer.module()
            .consume(deciKb);
        consumer.facility()
            .markModuleDirty(consumer.instance().id);
    }
}
