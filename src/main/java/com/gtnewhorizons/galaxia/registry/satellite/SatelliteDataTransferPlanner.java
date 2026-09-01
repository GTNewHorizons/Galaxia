package com.gtnewhorizons.galaxia.registry.satellite;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;

/**
 * Matches buffered produced data to compatible demand and assigns it to network paths for the current tick.
 *
 * <p>
 * The planner does not mutate buffers. It returns transfers plus edge/body usage so callers can apply the transfers
 * and sync a single atomic network state to clients.
 */
public final class SatelliteDataTransferPlanner {

    private static final long TICKS_PER_SECOND = 20L;

    private SatelliteDataTransferPlanner() {}

    public record Demand(ModuleInstance.ID sinkId, CelestialObjectKey bodyKey, SatelliteDataKey key, long deciKb) {

        public Demand {
            deciKb = Math.max(0L, deciKb);
        }
    }

    public record Transfer(UUID teamId, ModuleInstance.ID sinkId, CelestialObjectKey sourceBodyKey,
        CelestialObjectKey destinationBodyKey, SatelliteDataKey sourceKey, SatelliteDataKey demandKey, long deciKb,
        List<SatelliteNetworkGraph.Edge> path, long bottleneckKbps) {

        public Transfer {
            deciKb = Math.max(0L, deciKb);
            path = List.copyOf(path == null ? List.of() : path);
            bottleneckKbps = Math.max(0L, bottleneckKbps);
        }

    }

    public record Plan(List<Transfer> transfers, Map<SatelliteNetworkGraph.Edge, Long> usedByEdge,
        Map<SatelliteNetworkGraph.DirectedEdge, Long> directedUsedByEdge, Map<CelestialObjectKey, Long> usedByBody) {

        public Plan {
            transfers = List.copyOf(transfers == null ? List.of() : transfers);
            usedByEdge = Map.copyOf(usedByEdge == null ? Map.of() : usedByEdge);
            directedUsedByEdge = Map.copyOf(directedUsedByEdge == null ? Map.of() : directedUsedByEdge);
            usedByBody = Map.copyOf(usedByBody == null ? Map.of() : usedByBody);
        }
    }

    private record Route(Demand demand, List<SatelliteNetworkGraph.Edge> path, long bottleneckKbps) {

        private boolean local(CelestialObjectKey sourceBodyKey) {
            return demand.bodyKey()
                .equals(sourceBodyKey);
        }

        private long weight(CelestialObjectKey sourceBodyKey) {
            return local(sourceBodyKey) ? demand.deciKb() : bottleneckKbps;
        }
    }

    public static Plan plan(UUID teamId, SatelliteNetworkState networkState, SatelliteDataBufferStore store,
        List<Demand> demands) {
        if (teamId == null || networkState == null || store == null || demands == null)
            return new Plan(List.of(), Map.of(), Map.of(), Map.of());
        List<Transfer> transfers = new ArrayList<>();
        Map<SatelliteNetworkGraph.Edge, Long> usedByEdge = new HashMap<>();
        Map<CelestialObjectKey, Long> usedByBody = new HashMap<>();
        Map<ModuleInstance.ID, Long> remainingDemand = new HashMap<>();
        for (Demand demand : demands) remainingDemand.put(demand.sinkId(), demand.deciKb());
        /*
         * The planner is intentionally stateless: it reads the current produced/demand buffers and returns the
         * transfers
         * that can happen during this service tick. The buffer store applies those transfers afterwards, so routing,
         * capacity accounting, and persistence stay separated.
         */
        for (SatelliteDataBufferStore.Entry produced : store.producedEntries()) {
            List<Route> routes = routesForProducedData(networkState, demands, remainingDemand, produced);
            allocateAcrossRoutes(
                teamId,
                networkState,
                produced,
                routes,
                produced.deciKb(),
                transfers,
                usedByEdge,
                usedByBody,
                remainingDemand);
        }
        List<Transfer> resolvedTransfers = preferLocalExchangeForOpposingDemand(transfers);
        return new Plan(
            resolvedTransfers,
            usedByEdge(resolvedTransfers),
            directedUsedByEdge(resolvedTransfers),
            usedByBody(resolvedTransfers));
    }

    private static List<Route> routesForProducedData(SatelliteNetworkState networkState, List<Demand> demands,
        Map<ModuleInstance.ID, Long> remainingDemand, SatelliteDataBufferStore.Entry produced) {
        List<SatelliteDataKey> demandKeys = demands.stream()
            .filter(demand -> remainingDemand.getOrDefault(demand.sinkId(), 0L) > 0L)
            .map(Demand::key)
            .toList();
        // Origin-specific demand wins over "any origin" demand for the same data type.
        List<SatelliteDataKey> matchedKeys = SatelliteDataKey.matchingDemandKeys(produced.key(), demandKeys);
        return routesForKeys(networkState, produced, demands, remainingDemand, matchedKeys);
    }

    /*
     * Convert compatible demand entries into concrete network routes. Same-body demand uses an empty path, so it
     * shares sink allocation with remote traffic without consuming satellite bandwidth.
     */
    private static List<Route> routesForKeys(SatelliteNetworkState networkState,
        SatelliteDataBufferStore.Entry produced, List<Demand> demands, Map<ModuleInstance.ID, Long> remainingDemand,
        List<SatelliteDataKey> keys) {
        if (keys.isEmpty()) return List.of();
        Set<SatelliteDataKey> allowedKeys = new HashSet<>(keys);
        List<Route> routes = new ArrayList<>();
        for (Demand demand : demands) {
            if (remainingDemand.getOrDefault(demand.sinkId(), 0L) <= 0L || !allowedKeys.contains(demand.key()))
                continue;
            Route route = route(networkState, produced.bodyKey(), demand);
            if (route != null) routes.add(route);
        }
        if (routes.stream()
            .anyMatch(route -> route.local(produced.bodyKey()))) {
            routes.removeIf(route -> !route.local(produced.bodyKey()));
        }
        routes.sort(
            Comparator.comparing(
                route -> route.demand()
                    .bodyKey()));
        return routes;
    }

    private static long allocateAcrossRoutes(UUID teamId, SatelliteNetworkState networkState,
        SatelliteDataBufferStore.Entry produced, List<Route> routes, long availableDeciKb, List<Transfer> transfers,
        Map<SatelliteNetworkGraph.Edge, Long> usedByEdge, Map<CelestialObjectKey, Long> usedByBody,
        Map<ModuleInstance.ID, Long> remainingDemand) {
        long totalWeight = routes.stream()
            .mapToLong(route -> route.weight(produced.bodyKey()))
            .reduce(0L, SatelliteDataTransferPlanner::addSaturated);
        if (availableDeciKb <= 0L || totalWeight <= 0L) return 0L;
        long allocated = 0L;
        for (int i = 0; i < routes.size(); i++) {
            Route route = routes.get(i);
            long availableKbps = route.local(produced.bodyKey()) ? 0L
                : availableKbps(produced.bodyKey(), route, networkState, usedByEdge, usedByBody);
            long sinkDemand = remainingDemand.getOrDefault(
                route.demand()
                    .sinkId(),
                0L);
            long amount = allocatedAmount(
                produced.bodyKey(),
                route,
                availableDeciKb,
                allocated,
                i == routes.size() - 1,
                totalWeight,
                availableKbps,
                sinkDemand);
            if (amount <= 0L) continue;
            transfers.add(
                new Transfer(
                    teamId,
                    route.demand()
                        .sinkId(),
                    produced.bodyKey(),
                    route.demand()
                        .bodyKey(),
                    produced.key(),
                    route.demand()
                        .key(),
                    amount,
                    route.path(),
                    route.bottleneckKbps()));
            recordCapacityUsage(produced.bodyKey(), route, amount, availableKbps, usedByEdge, usedByBody);
            allocated += amount;
            remainingDemand.put(
                route.demand()
                    .sinkId(),
                sinkDemand - amount);
        }
        return allocated;
    }

    private static long allocatedAmount(CelestialObjectKey sourceBodyKey, Route route, long availableDeciKb,
        long allocatedDeciKb, boolean lastRoute, long totalWeight, long availableKbps, long sinkDemand) {
        boolean local = route.local(sourceBodyKey);
        long weight = local ? route.weight(sourceBodyKey) : availableKbps;
        if (weight <= 0L) return 0L;
        long share = lastRoute ? availableDeciKb - allocatedDeciKb
            : weightedShare(availableDeciKb, weight, totalWeight);
        long routeLimit = local ? availableDeciKb : deciKbPerTick(availableKbps);
        return Math.min(Math.min(sinkDemand, routeLimit), Math.max(0L, share));
    }

    private static void recordCapacityUsage(CelestialObjectKey sourceBodyKey, Route route, long amount,
        long availableKbps, Map<SatelliteNetworkGraph.Edge, Long> usedByEdge,
        Map<CelestialObjectKey, Long> usedByBody) {
        if (route.local(sourceBodyKey)) return;
        long usedKbps = Math.min(availableKbps, Math.max(1L, amount * TICKS_PER_SECOND / 10L));
        for (SatelliteNetworkGraph.Edge edge : route.path())
            usedByEdge.merge(edge, usedKbps, SatelliteDataTransferPlanner::addSaturated);
        for (CelestialObjectKey bodyKey : routeBodies(sourceBodyKey, route.path()))
            usedByBody.merge(bodyKey, usedKbps, SatelliteDataTransferPlanner::addSaturated);
    }

    private static long availableKbps(CelestialObjectKey sourceBodyKey, Route route, SatelliteNetworkState networkState,
        Map<SatelliteNetworkGraph.Edge, Long> usedByEdge, Map<CelestialObjectKey, Long> usedByBody) {
        long availableKbps = Math.max(0L, route.bottleneckKbps());
        /*
         * Capacity is consumed by both the traversed links and every body on the route. A destination with two inbound
         * paths still has one local satellite bandwidth pool, so the planner clamps by usedByBody as well as
         * usedByEdge.
         */
        for (SatelliteNetworkGraph.Edge edge : route.path()) {
            long usedKbps = Math.max(0L, usedByEdge.getOrDefault(edge, 0L));
            availableKbps = Math.min(availableKbps, Math.max(0L, route.bottleneckKbps() - usedKbps));
        }
        for (CelestialObjectKey bodyKey : routeBodies(sourceBodyKey, route.path())) {
            long bodyCapacityKbps = networkState.capacityKbps(bodyKey);
            long usedKbps = Math.max(0L, usedByBody.getOrDefault(bodyKey, 0L));
            availableKbps = Math.min(availableKbps, Math.max(0L, bodyCapacityKbps - usedKbps));
        }
        return availableKbps;
    }

    private static List<CelestialObjectKey> routeBodies(CelestialObjectKey sourceBodyKey,
        List<SatelliteNetworkGraph.Edge> path) {
        if (sourceBodyKey == null || path == null) return List.of();
        List<CelestialObjectKey> bodies = new ArrayList<>();
        bodies.add(sourceBodyKey);
        CelestialObjectKey current = sourceBodyKey;
        for (SatelliteNetworkGraph.Edge edge : path) {
            CelestialObjectKey next = edge.from()
                .equals(current) ? edge.to()
                    : edge.to()
                        .equals(current) ? edge.from() : null;
            if (next == null) return bodies;
            bodies.add(next);
            current = next;
        }
        return bodies;
    }

    /*
     * Directional usage is derived from an undirected path plus the source body. The client uses this to decide which
     * way packets should travel on a link while the topology itself remains normalized.
     */
    static void mergeDirectedUsage(Map<SatelliteNetworkGraph.DirectedEdge, Long> target, CelestialObjectKey source,
        CelestialObjectKey destination, List<SatelliteNetworkGraph.Edge> path, long usedKbps) {
        if (target == null || source == null || destination == null || path == null || usedKbps <= 0L) return;
        CelestialObjectKey current = source;
        for (SatelliteNetworkGraph.Edge edge : path) {
            CelestialObjectKey next = edge.from()
                .equals(current) ? edge.to()
                    : edge.to()
                        .equals(current) ? edge.from() : null;
            if (next == null) return;
            target.merge(
                new SatelliteNetworkGraph.DirectedEdge(current, next),
                usedKbps,
                SatelliteDataTransferPlanner::addSaturated);
            current = next;
        }
        if (!current.equals(destination)) return;
    }

    private static List<Transfer> preferLocalExchangeForOpposingDemand(List<Transfer> transfers) {
        if (transfers == null || transfers.size() < 2) return transfers == null ? List.of() : List.copyOf(transfers);
        long[] remainingDeciKb = transfers.stream()
            .mapToLong(Transfer::deciKb)
            .toArray();
        List<Set<SatelliteNetworkGraph.DirectedEdge>> directedPaths = transfers.stream()
            .map(SatelliteDataTransferPlanner::directedPath)
            .toList();
        List<Transfer> localTransfers = new ArrayList<>();
        /*
         * If the same data requirement would flow both directions over any shared link, treat the overlap as local
         * exchange at each producer instead. That avoids two matching jobs burning satellite bandwidth just to cross
         * over
         * each other on the same route.
         */
        for (int left = 0; left < transfers.size(); left++) {
            Transfer leftTransfer = transfers.get(left);
            for (int right = left + 1; right < transfers.size(); right++) {
                Transfer rightTransfer = transfers.get(right);
                if (remainingDeciKb[left] <= 0L || remainingDeciKb[right] <= 0L) continue;
                if (!leftTransfer.demandKey()
                    .equals(rightTransfer.demandKey())) {
                    continue;
                }
                if (!hasOpposingEdge(directedPaths.get(left), directedPaths.get(right))) continue;
                long resolvedDeciKb = Math.min(remainingDeciKb[left], remainingDeciKb[right]);
                remainingDeciKb[left] -= resolvedDeciKb;
                remainingDeciKb[right] -= resolvedDeciKb;
                localTransfers.add(
                    new Transfer(
                        leftTransfer.teamId(),
                        rightTransfer.sinkId(),
                        leftTransfer.sourceBodyKey(),
                        leftTransfer.sourceBodyKey(),
                        leftTransfer.sourceKey(),
                        rightTransfer.demandKey(),
                        resolvedDeciKb,
                        List.of(),
                        0L));
                localTransfers.add(
                    new Transfer(
                        rightTransfer.teamId(),
                        leftTransfer.sinkId(),
                        rightTransfer.sourceBodyKey(),
                        rightTransfer.sourceBodyKey(),
                        rightTransfer.sourceKey(),
                        leftTransfer.demandKey(),
                        resolvedDeciKb,
                        List.of(),
                        0L));
            }
        }
        List<Transfer> resolved = new ArrayList<>();
        for (int i = 0; i < transfers.size(); i++) {
            Transfer transfer = transfers.get(i);
            if (remainingDeciKb[i] <= 0L) continue;
            resolved.add(
                new Transfer(
                    transfer.teamId(),
                    transfer.sinkId(),
                    transfer.sourceBodyKey(),
                    transfer.destinationBodyKey(),
                    transfer.sourceKey(),
                    transfer.demandKey(),
                    remainingDeciKb[i],
                    transfer.path(),
                    transfer.bottleneckKbps()));
        }
        resolved.addAll(localTransfers);
        return resolved;
    }

    private static Set<SatelliteNetworkGraph.DirectedEdge> directedPath(Transfer transfer) {
        Set<SatelliteNetworkGraph.DirectedEdge> directedEdges = new HashSet<>();
        CelestialObjectKey current = transfer.sourceBodyKey();
        for (SatelliteNetworkGraph.Edge edge : transfer.path()) {
            CelestialObjectKey next = edge.from()
                .equals(current) ? edge.to()
                    : edge.to()
                        .equals(current) ? edge.from() : null;
            if (next == null) return directedEdges;
            directedEdges.add(new SatelliteNetworkGraph.DirectedEdge(current, next));
            current = next;
        }
        return directedEdges;
    }

    private static boolean hasOpposingEdge(Set<SatelliteNetworkGraph.DirectedEdge> left,
        Set<SatelliteNetworkGraph.DirectedEdge> right) {
        for (SatelliteNetworkGraph.DirectedEdge edge : left) {
            if (right.contains(new SatelliteNetworkGraph.DirectedEdge(edge.to(), edge.from()))) return true;
        }
        return false;
    }

    /*
     * Collapsed link usage drives link colour. Opposite directions add together here because a link has one shared
     * capacity pool regardless of packet direction.
     */
    private static Map<SatelliteNetworkGraph.Edge, Long> usedByEdge(List<Transfer> transfers) {
        Map<SatelliteNetworkGraph.Edge, Long> usedByEdge = new HashMap<>();
        for (Transfer transfer : transfers) {
            long usedKbps = transferUsedKbps(transfer);
            if (usedKbps <= 0L) continue;
            for (SatelliteNetworkGraph.Edge edge : transfer.path())
                usedByEdge.merge(edge, usedKbps, SatelliteDataTransferPlanner::addSaturated);
        }
        return usedByEdge;
    }

    private static Map<SatelliteNetworkGraph.DirectedEdge, Long> directedUsedByEdge(List<Transfer> transfers) {
        Map<SatelliteNetworkGraph.DirectedEdge, Long> directedUsedByEdge = new HashMap<>();
        for (Transfer transfer : transfers) {
            mergeDirectedUsage(
                directedUsedByEdge,
                transfer.sourceBodyKey(),
                transfer.destinationBodyKey(),
                transfer.path(),
                transferUsedKbps(transfer));
        }
        return directedUsedByEdge;
    }

    /*
     * Body usage is charged to every node on the path, including relay bodies. This is what prevents two inbound routes
     * from exceeding the destination or relay planet's local satellite bandwidth.
     */
    private static Map<CelestialObjectKey, Long> usedByBody(List<Transfer> transfers) {
        Map<CelestialObjectKey, Long> usedByBody = new HashMap<>();
        for (Transfer transfer : transfers) {
            long usedKbps = transferUsedKbps(transfer);
            if (usedKbps <= 0L) continue;
            for (CelestialObjectKey bodyKey : routeBodies(transfer.sourceBodyKey(), transfer.path()))
                usedByBody.merge(bodyKey, usedKbps, SatelliteDataTransferPlanner::addSaturated);
        }
        return usedByBody;
    }

    /*
     * Transfers are stored as an amount moved this tick. Convert that back into Kbps for the network snapshot and clamp
     * it to the selected route bottleneck so visuals never report more throughput than the route can carry.
     */
    private static long transferUsedKbps(Transfer transfer) {
        if (transfer.path()
            .isEmpty()) {
            return 0L;
        }
        return Math.min(transfer.bottleneckKbps(), Math.max(1L, transfer.deciKb() * TICKS_PER_SECOND / 10L));
    }

    private static long deciKbPerTick(long kbps) {
        if (kbps <= 0L) return 0L;
        return Math.max(1L, SatelliteBandwidthFormatter.kilobits(kbps) / TICKS_PER_SECOND);
    }

    private static long weightedShare(long availableDeciKb, long weight, long totalWeight) {
        if (availableDeciKb <= 0L || weight <= 0L || totalWeight <= 0L) return 0L;
        if (availableDeciKb <= Long.MAX_VALUE / weight) return availableDeciKb * weight / totalWeight;
        return BigInteger.valueOf(availableDeciKb)
            .multiply(BigInteger.valueOf(weight))
            .divide(BigInteger.valueOf(totalWeight))
            .min(BigInteger.valueOf(Long.MAX_VALUE))
            .longValue();
    }

    private static long addSaturated(long left, long right) {
        if (left > Long.MAX_VALUE - right) return Long.MAX_VALUE;
        return left + right;
    }

    private static Route route(SatelliteNetworkState networkState, CelestialObjectKey from, Demand demand) {
        if (from.equals(demand.bodyKey())) return new Route(demand, List.of(), 0L);
        SatelliteNetworkCalculator.WidestPath path = SatelliteNetworkCalculator
            .widestPath(from, demand.bodyKey(), networkState);
        if (path.capacityKbps() <= 0L) return null;
        return new Route(demand, path.edges(), path.capacityKbps());
    }

}
