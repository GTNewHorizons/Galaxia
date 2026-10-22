package com.gtnewhorizons.galaxia.registry.satellite;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalMechanics;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalParams;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;

/**
 * Owns the server-side satellite network runtime for all teams.
 *
 * <p>
 * The service keeps the mutable pieces that are not persisted directly on assets: active transfer glow, registered
 * producer/consumer endpoints, and buffered data. Each rebuild turns that runtime plus the asset store into an
 * immutable
 * {@link SatelliteNetworkState} packet for API reads and client sync.
 */
public final class SatelliteNetworkService {

    private static final int DATA_USAGE_VISIBLE_TICKS = 40;
    private static final Map<UUID, TeamRuntime> RUNTIMES = new HashMap<>();
    private static int sampleTicks;

    private SatelliteNetworkService() {}

    private static TeamRuntime runtime(UUID teamId) {
        return RUNTIMES.computeIfAbsent(teamId, TeamRuntime::new);
    }

    public static SatelliteNetworkState current(UUID teamId) {
        TeamRuntime runtime = RUNTIMES.get(teamId);
        return runtime == null ? SatelliteNetworkState.empty(teamId, 0) : runtime.state;
    }

    /**
     * Reserves shared link and body capacity until release or a rebuild can no longer route it. Repeating the same
     * request is idempotent. Changing the terms of an existing reservation fails without replacing it.
     */
    public static boolean reserveBandwidth(UUID teamId, UUID reservationId, CelestialObjectKey from,
        CelestialObjectKey to, long requiredKbps) {
        if (teamId == null || reservationId == null || from == null || to == null || requiredKbps <= 0L) return false;
        TeamRuntime runtime = runtime(teamId);
        BandwidthReservation existing = runtime.reservations.get(reservationId);
        if (existing != null) {
            return existing.from()
                .equals(from)
                && existing.to()
                    .equals(to)
                && existing.requiredKbps() == requiredKbps;
        }
        SatelliteNetworkState available = remainingNetwork(
            runtime.state,
            reservationUsage(runtime.state, runtime.reservations.values()));
        SatelliteNetworkCalculator.WidestPath path = SatelliteNetworkCalculator.widestPath(from, to, available);
        if (path.capacityKbps() < requiredKbps) return false;
        runtime.reservations.put(reservationId, new BandwidthReservation(from, to, requiredKbps, path.edges()));
        return true;
    }

    public static void releaseBandwidth(UUID teamId, UUID reservationId) {
        TeamRuntime runtime = RUNTIMES.get(teamId);
        if (runtime != null) runtime.reservations.remove(reservationId);
    }

    public static boolean hasReservation(UUID teamId, UUID reservationId) {
        TeamRuntime runtime = RUNTIMES.get(teamId);
        return runtime != null && runtime.reservations.containsKey(reservationId);
    }

    /*
     * Normal rebuild entry point for the live server. It samples current orbital positions and satellite counts, then
     * delegates to the snapshot builder that also accounts for buffered data traffic.
     */
    public static SatelliteNetworkState rebuild(UUID teamId, double orbitalTime) {
        Map<CelestialObjectKey, Long> capacityByBody = new HashMap<>();
        List<SatelliteNetworkGraph.Node> nodes = buildNodes(teamId, orbitalTime, capacityByBody);
        return rebuild(teamId, nodes, capacityByBody, runtime(teamId).buffers);
    }

    static SatelliteNetworkState rebuild(UUID teamId, List<SatelliteNetworkGraph.Node> nodes,
        Map<CelestialObjectKey, Long> capacityByBody, SatelliteDataBufferStore bufferStore) {
        SatelliteNetworkState previous = current(teamId);
        TeamRuntime runtime = runtime(teamId);
        List<SatelliteNetworkGraph.Node> activeNodes = activeNodes(nodes, capacityByBody);
        /*
         * Rebuild is deliberately two-phase. First derive the topology from satellite ownership and capacity; then use
         * that topology to plan buffered data transfers. The final snapshot is what gets synced, so clients receive
         * edges and their colors/usage atomically.
         */
        SatelliteNetworkState unloaded = SatelliteNetworkCalculator
            .forTeam(teamId, previous.revision() + 1, activeNodes, capacityByBody, Map.of());
        revalidateReservations(runtime, unloaded);
        BandwidthUsage reserved = reservationUsage(unloaded, runtime.reservations.values());
        SatelliteNetworkState available = remainingNetwork(unloaded, reserved);
        clearActiveUsageIfTopologyChanged(runtime, previous, unloaded);
        SatelliteDataTransferPlanner.Plan plan = SatelliteDataTransferPlanner
            .plan(teamId, available, bufferStore, runtime.endpoints.demands());
        // Planned transfers are real throughput; active usage keeps short visual pulses visible between rebuilds.
        BandwidthUsage usage = visibleUsage(runtime, unloaded, reserved, plan);
        SatelliteNetworkState next = SatelliteNetworkCalculator
            .fromGraph(
                teamId,
                previous.revision() + 1,
                activeNodes,
                unloaded.links()
                    .stream()
                    .map(SatelliteNetworkState.Link::asEdge)
                    .toList(),
                capacityByBody,
                usage.usedByEdge(),
                usage.directedUsedByEdge(),
                usage.usedByBody())
            .withPendingData(pendingData(bufferStore, plan.transfers()));
        if (sameContent(previous, next)) return previous;
        runtime.state = next;
        return next;
    }

    public static void clear() {
        RUNTIMES.clear();
        sampleTicks = 0;
    }

    static SatelliteDataBufferStore dataBuffers(UUID teamId) {
        return runtime(teamId).buffers;
    }

    public static boolean canStartProcess(UUID teamId, CelestialObjectKey bodyKey, SatelliteDataKey outputKey) {
        return runtime(teamId).buffers.canStart(bodyKey, outputKey, current(teamId).capacityKbps(bodyKey));
    }

    public static void registerEndpoint(UUID teamId, SatelliteDataEndpointRegistry.Endpoint endpoint) {
        runtime(teamId).endpoints.register(endpoint);
    }

    public static void unregisterEndpoint(UUID teamId, UUID endpointId) {
        TeamRuntime runtime = RUNTIMES.get(teamId);
        if (runtime != null) runtime.endpoints.unregister(endpointId);
    }

    public static long outputRoom(UUID teamId, CelestialObjectKey bodyKey, SatelliteDataKey key) {
        long limit = Math.max(100L, SatelliteBandwidthFormatter.kilobits(current(teamId).capacityKbps(bodyKey)));
        return Math.max(0L, limit - runtime(teamId).buffers.pendingDeciKb(bodyKey, key));
    }

    public static long offerData(UUID teamId, CelestialObjectKey bodyKey, SatelliteDataKey key, long amount) {
        long accepted = Math.min(Math.max(0L, amount), outputRoom(teamId, bodyKey, key));
        runtime(teamId).buffers.finishProduction(bodyKey, key, accepted);
        return accepted;
    }

    static void acceptProspectingFlow(UUID teamId, CelestialObjectKey bodyKey, long amount) {
        if (teamId != null && amount > 0L) runtime(teamId).prospectingFlow.merge(bodyKey, amount, Long::sum);
    }

    /** Mining reads the previous completed network tick. No bonus survives a tick with no received flow. */
    public static double miningYieldBonus(UUID teamId, CelestialObjectKey bodyKey) {
        TeamRuntime runtime = RUNTIMES.get(teamId);
        return runtime == null ? 0.0D : 0.1D * Math.sqrt(runtime.prospectingFlow.getOrDefault(bodyKey, 0L) / 5.0D);
    }

    public static int mineralYieldMultiplier(double bonus, Random random) {
        double nonnegative = Math.max(0.0D, bonus);
        int whole = (int) Math.floor(nonnegative);
        double fraction = nonnegative - whole;
        return 1 + whole + (fraction > 0.0D && random.nextDouble() < fraction ? 1 : 0);
    }

    /*
     * Called from the server tick. Endpoint modules advance jobs here; the resulting short-lived usage is kept until a
     * rebuild packages it into the synced network snapshot.
     */
    public static void tickDataJobs() {
        tickDataJobsSingleTick();
    }

    private static void tickDataJobsSingleTick() {
        tickActiveUsage();
        if (++sampleTicks >= 20) {
            sampleTicks = 0;
            double orbitalTime = GalaxiaCelestialAPI.currentOrbitalTime();
            for (UUID teamId : List.copyOf(RUNTIMES.keySet())) {
                if (teamId != null) rebuild(teamId, orbitalTime);
            }
        }
        for (Map.Entry<UUID, TeamRuntime> entry : RUNTIMES.entrySet()) {
            TeamRuntime runtime = entry.getValue();
            runtime.prospectingFlow.clear();
            if (runtime.endpoints.isEmpty()) continue;
            SatelliteDataJobService.Usage usage = SatelliteDataJobService.tickEndpointsUsage(
                entry.getKey(),
                runtime.endpoints.endpoints(),
                runtime.buffers,
                remainingNetwork(runtime.state, reservationUsage(runtime.state, runtime.reservations.values())));
            recordActiveUsage(runtime, usage);
        }
    }

    public static void refreshAssetEndpoints(UUID teamId, CelestialAsset asset) {
        if (teamId == null) return;
        if (asset instanceof AutomatedFacility facility) {
            runtime(teamId).endpoints.refreshFacility(facility);
        } else if (asset instanceof Satellite satellite) {
            runtime(teamId).endpoints.refreshSatellite(satellite);
        } else if (asset != null) {
            unregisterAssetEndpoints(asset.assetId);
        }
    }

    public static void refreshFacilityEndpoints(AutomatedFacility facility) {
        if (facility == null) return;
        refreshAssetEndpoints(CelestialAssetStore.getTeamId(facility.assetId), facility);
    }

    public static void unregisterAssetEndpoints(CelestialAsset.ID assetId) {
        for (TeamRuntime runtime : RUNTIMES.values()) runtime.endpoints.unregisterAsset(assetId);
    }

    public static void mergeTeams(UUID consumedTeam, UUID survivingTeam, Iterable<CelestialAsset> transferredAssets) {
        if (consumedTeam.equals(survivingTeam)) return;
        RUNTIMES.remove(consumedTeam);
        for (CelestialAsset asset : transferredAssets) {
            refreshAssetEndpoints(survivingTeam, asset);
        }
    }

    /* Keep valid paths before rerouting broken paths, so a topology refresh cannot displace an existing session. */
    private static void revalidateReservations(TeamRuntime runtime, SatelliteNetworkState network) {
        Map<UUID, BandwidthReservation> retained = new LinkedHashMap<>();
        List<UUID> reroute = new ArrayList<>();
        for (Map.Entry<UUID, BandwidthReservation> entry : runtime.reservations.entrySet()) {
            SatelliteNetworkState available = remainingNetwork(network, reservationUsage(network, retained.values()));
            if (fits(entry.getValue(), available)) retained.put(entry.getKey(), entry.getValue());
            else reroute.add(entry.getKey());
        }
        for (UUID id : reroute) {
            BandwidthReservation reservation = runtime.reservations.get(id);
            SatelliteNetworkState available = remainingNetwork(network, reservationUsage(network, retained.values()));
            SatelliteNetworkCalculator.WidestPath path = SatelliteNetworkCalculator
                .widestPath(reservation.from(), reservation.to(), available);
            if (path.capacityKbps() >= reservation.requiredKbps()) {
                retained.put(
                    id,
                    new BandwidthReservation(
                        reservation.from(),
                        reservation.to(),
                        reservation.requiredKbps(),
                        path.edges()));
            }
        }
        // Keep acquisition order even when an older reservation was rerouted after a younger valid path.
        runtime.reservations.entrySet()
            .removeIf(entry -> !retained.containsKey(entry.getKey()));
        runtime.reservations.replaceAll((id, reservation) -> retained.get(id));
    }

    private static boolean fits(BandwidthReservation reservation, SatelliteNetworkState available) {
        Map<SatelliteNetworkGraph.Edge, Integer> costs = SatelliteNetworkCalculator
            .airtimeCosts(available, reservation.path());
        if (costs.size() != reservation.path()
            .size()) return false;
        for (Map.Entry<CelestialObjectKey, Integer> body : SatelliteNetworkCalculator
            .bodyAirtimeCosts(reservation.path(), costs)
            .entrySet()) {
            if (available.capacityKbps(body.getKey()) / body.getValue() < reservation.requiredKbps()) return false;
        }
        for (SatelliteNetworkGraph.Edge edge : reservation.path()) {
            if (available.links()
                .stream()
                .noneMatch(
                    link -> link.asEdge()
                        .equals(edge) && link.capacityKbps() / costs.get(edge) >= reservation.requiredKbps()))
                return false;
        }
        return available.capacityKbps(reservation.from()) >= reservation.requiredKbps();
    }

    private static BandwidthUsage reservationUsage(SatelliteNetworkState network,
        Iterable<BandwidthReservation> reservations) {
        Map<SatelliteNetworkGraph.Edge, Long> edges = new HashMap<>();
        Map<SatelliteNetworkGraph.DirectedEdge, Long> directed = new HashMap<>();
        Map<CelestialObjectKey, Long> bodies = new HashMap<>();
        for (BandwidthReservation reservation : reservations) {
            if (reservation.path()
                .isEmpty()) {
                bodies.merge(reservation.from(), reservation.requiredKbps(), Long::sum);
                continue;
            }
            Map<SatelliteNetworkGraph.Edge, Integer> costs = SatelliteNetworkCalculator
                .airtimeCosts(network, reservation.path());
            if (costs.size() != reservation.path()
                .size()) continue;
            for (SatelliteNetworkGraph.Edge edge : reservation.path())
                edges.merge(edge, reservation.requiredKbps() * costs.get(edge), Long::sum);
            SatelliteNetworkCalculator.bodyAirtimeCosts(reservation.path(), costs)
                .forEach((body, cost) -> bodies.merge(body, reservation.requiredKbps() * cost, Long::sum));
            List<CelestialObjectKey> pathBodies = SatelliteDataTransferPlanner
                .routeBodies(reservation.from(), reservation.path());
            for (int i = 1; i < pathBodies.size(); i++) {
                var edge = new SatelliteNetworkGraph.Edge(pathBodies.get(i - 1), pathBodies.get(i));
                directed.merge(
                    new SatelliteNetworkGraph.DirectedEdge(pathBodies.get(i - 1), pathBodies.get(i)),
                    reservation.requiredKbps() * costs.get(edge),
                    Long::sum);
            }
        }
        return new BandwidthUsage(edges, directed, bodies);
    }

    private static SatelliteNetworkState remainingNetwork(SatelliteNetworkState network, BandwidthUsage reserved) {
        return SatelliteNetworkCalculator.remaining(network, reserved.usedByEdge(), reserved.usedByBody());
    }

    private static BandwidthUsage visibleUsage(TeamRuntime runtime, SatelliteNetworkState network,
        BandwidthUsage reserved, SatelliteDataTransferPlanner.Plan plan) {
        Map<SatelliteNetworkGraph.Edge, Long> dataEdges = mergedUsage(plan.usedByEdge(), activeUsage(runtime));
        Map<SatelliteNetworkGraph.DirectedEdge, Long> dataDirected = mergedUsage(
            plan.directedUsedByEdge(),
            activeDirectionalUsage(runtime));
        Map<SatelliteNetworkGraph.Edge, Long> edges = new HashMap<>();
        Map<SatelliteNetworkGraph.DirectedEdge, Long> directed = new HashMap<>();
        for (SatelliteNetworkState.Link link : network.links()) {
            long reservedKbps = reserved.usedByEdge()
                .getOrDefault(link.asEdge(), 0L);
            long remaining = Math.max(0L, link.capacityKbps() - reservedKbps);
            edges.put(link.asEdge(), reservedKbps + Math.min(remaining, dataEdges.getOrDefault(link.asEdge(), 0L)));
            for (SatelliteNetworkGraph.DirectedEdge direction : List.of(
                new SatelliteNetworkGraph.DirectedEdge(link.from(), link.to()),
                new SatelliteNetworkGraph.DirectedEdge(link.to(), link.from()))) {
                directed.put(
                    direction,
                    reserved.directedUsedByEdge()
                        .getOrDefault(direction, 0L) + Math.min(remaining, dataDirected.getOrDefault(direction, 0L)));
            }
        }
        Map<CelestialObjectKey, Long> bodies = new HashMap<>(reserved.usedByBody());
        plan.usedByBody()
            .forEach((body, used) -> bodies.merge(body, used, Long::sum));
        return new BandwidthUsage(edges, directed, bodies);
    }

    /*
     * Active usage is a visual bridge between the tick that applied a transfer and the next snapshot rebuild. Without
     * it
     * small transfers can complete before the starmap ever receives a colored link.
     */
    private static void tickActiveUsage() {
        for (TeamRuntime runtime : RUNTIMES.values()) {
            tickActiveUsage(runtime.activeUsage);
            tickActiveUsage(runtime.activeDirectionalUsage);
        }
    }

    private static <T> void tickActiveUsage(Map<T, ActiveDataUsage> usageByEdge) {
        usageByEdge.replaceAll((edge, usage) -> usage.tick());
        usageByEdge.entrySet()
            .removeIf(
                entry -> entry.getValue()
                    .expired());
    }

    /*
     * Store both undirected and directed usage. Undirected usage colors the link; directed usage controls packet
     * direction on that same link.
     */
    private static void recordActiveUsage(TeamRuntime runtime, SatelliteDataJobService.Usage usage) {
        if (usage == null) return;
        recordActiveUsage(usage.usedByEdge(), runtime.activeUsage);
        recordActiveUsage(usage.directedUsedByEdge(), runtime.activeDirectionalUsage);
    }

    private static <T> void recordActiveUsage(Map<T, Long> usedByEdge, Map<T, ActiveDataUsage> activeUsage) {
        if (usedByEdge == null || usedByEdge.isEmpty()) return;
        for (Map.Entry<T, Long> entry : usedByEdge.entrySet()) {
            long usedKbps = entry.getValue() == null ? 0L : entry.getValue();
            if (usedKbps <= 0L) continue;
            activeUsage.merge(
                entry.getKey(),
                new ActiveDataUsage(usedKbps, DATA_USAGE_VISIBLE_TICKS),
                (left, right) -> new ActiveDataUsage(Math.max(left.usedKbps(), right.usedKbps()), right.ticksLeft()));
        }
    }

    /*
     * The graph is built from orbital-space positions, not screen-space positions. Zoom/culling should not change the
     * server-side topology or path capacity.
     */
    private static List<SatelliteNetworkGraph.Node> buildNodes(UUID teamId, double orbitalTime,
        Map<CelestialObjectKey, Long> capacityByBody) {
        CelestialObject root = GalaxiaCelestialAPI.root();
        List<CelestialObject> bodies = new ArrayList<>(
            GalaxiaCelestialAPI.getAllBodies()
                .values());
        for (CelestialObjectKey key : minorSatelliteBodyKeys(teamId)) {
            GalaxiaCelestialAPI.get(key)
                .ifPresent(bodies::add);
        }
        List<SatelliteNetworkGraph.Node> nodes = new ArrayList<>();
        for (CelestialObject body : bodies) {
            if (body.objectClass() == CelestialObject.Class.GALAXY || body.objectClass() == CelestialObject.Class.STAR)
                continue;
            long capacity = CelestialAssetStore.SERVER.satelliteBandwidth(teamId, body.key());
            if (capacity <= 0L) continue;
            capacityByBody.put(body.key(), capacity);
            nodes.add(nodeFor(root, body, orbitalTime));
        }
        return nodes;
    }

    private static SatelliteNetworkGraph.Node nodeFor(CelestialObject root, CelestialObject body, double orbitalTime) {
        OrbitalMechanics.OrbitalState state = resolveNodeWorldState(root, body, orbitalTime);
        return new SatelliteNetworkGraph.Node(
            body.key(),
            body.parentKey(),
            orbitalOrder(body),
            state.x(),
            state.y(),
            Math.max(0.0D, body.spriteSize()));
    }

    private static OrbitalMechanics.OrbitalState resolveNodeWorldState(CelestialObject root, CelestialObject body,
        double orbitalTime) {
        OrbitalMechanics.OrbitalState state = OrbitalMechanics.resolveWorldState(root, body, orbitalTime);
        if (state == null) {
            throw new IllegalStateException("Cannot resolve orbital state for satellite network body: " + body.key());
        }
        return state;
    }

    private static double orbitalOrder(CelestialObject body) {
        OrbitalParams params = body.orbitalParams();
        return params == null ? body.key()
            .parentSortOrdinal() : params.semiMajorAxis();
    }

    private static List<SatelliteNetworkGraph.Node> activeNodes(List<SatelliteNetworkGraph.Node> nodes,
        Map<CelestialObjectKey, Long> capacityByBody) {
        Map<CelestialObjectKey, Long> capacities = capacityByBody == null ? Map.of() : capacityByBody;
        return (nodes == null ? List.<SatelliteNetworkGraph.Node>of() : nodes).stream()
            .filter(node -> capacities.getOrDefault(node.bodyKey(), 0L) > 0L)
            .toList();
    }

    /*
     * If the edge set changes, old active-usage pulses would color links that no longer exist. Drop them so topology
     * changes and link colors arrive as one atomic snapshot.
     */
    private static void clearActiveUsageIfTopologyChanged(TeamRuntime runtime, SatelliteNetworkState previous,
        SatelliteNetworkState next) {
        if (runtime.activeUsage.isEmpty() && runtime.activeDirectionalUsage.isEmpty()) return;
        if (!edgeSet(previous).equals(edgeSet(next))) {
            runtime.activeUsage.clear();
            runtime.activeDirectionalUsage.clear();
        }
    }

    private static Set<SatelliteNetworkGraph.Edge> edgeSet(SatelliteNetworkState state) {
        Set<SatelliteNetworkGraph.Edge> edges = new HashSet<>();
        for (SatelliteNetworkState.Link link : state.links()) {
            edges.add(link.asEdge());
        }
        return edges;
    }

    /*
     * Pending data is a read model for clients/tooltips. It reports producer-side backlog and the destinations selected
     * by this tick's transfer plan; it does not mutate buffers.
     */
    private static List<SatelliteNetworkState.PendingData> pendingData(SatelliteDataBufferStore bufferStore,
        List<SatelliteDataTransferPlanner.Transfer> transfers) {
        return bufferStore.producedEntries()
            .stream()
            .map(
                entry -> new SatelliteNetworkState.PendingData(
                    entry.bodyKey(),
                    pendingDestinations(entry, transfers),
                    entry.key(),
                    entry.deciKb()))
            .toList();
    }

    private static List<CelestialObjectKey> pendingDestinations(SatelliteDataBufferStore.Entry entry,
        List<SatelliteDataTransferPlanner.Transfer> transfers) {
        if (entry == null || transfers == null || transfers.isEmpty()) return List.of();
        return transfers.stream()
            .filter(
                transfer -> transfer.sourceBodyKey()
                    .equals(entry.bodyKey()))
            .filter(
                transfer -> transfer.sourceKey()
                    .equals(entry.key()))
            .map(SatelliteDataTransferPlanner.Transfer::destinationBodyKey)
            .distinct()
            .sorted()
            .toList();
    }

    private static Set<CelestialObjectKey> minorSatelliteBodyKeys(UUID teamId) {
        if (teamId == null) return Set.of();
        Set<CelestialObjectKey> keys = new LinkedHashSet<>();
        for (CelestialAsset asset : CelestialAssetStore.getTeamAssets(teamId)
            .values()
            .stream()
            .flatMap(Set::stream)
            .toList()) {
            if (asset instanceof Satellite && asset.celestialObjectKey.isMinorBody())
                keys.add(asset.celestialObjectKey);
        }
        return keys;
    }

    private static Map<SatelliteNetworkGraph.Edge, Long> activeUsage(TeamRuntime runtime) {
        return activeUsage(runtime.activeUsage);
    }

    private static Map<SatelliteNetworkGraph.DirectedEdge, Long> activeDirectionalUsage(TeamRuntime runtime) {
        return activeUsage(runtime.activeDirectionalUsage);
    }

    private static <T> Map<T, Long> activeUsage(Map<T, ActiveDataUsage> activeUsage) {
        if (activeUsage.isEmpty()) return Map.of();
        Map<T, Long> usage = new HashMap<>();
        for (Map.Entry<T, ActiveDataUsage> entry : activeUsage.entrySet()) {
            usage.put(
                entry.getKey(),
                entry.getValue()
                    .usedKbps());
        }
        return usage;
    }

    private static <T> Map<T, Long> mergedUsage(Map<T, Long> left, Map<T, Long> right) {
        Map<T, Long> merged = new HashMap<>();
        mergeUsage(merged, left);
        mergeUsage(merged, right);
        return Map.copyOf(merged);
    }

    private static <T> void mergeUsage(Map<T, Long> target, Map<T, Long> source) {
        if (source == null) return;
        for (Map.Entry<T, Long> entry : source.entrySet()) {
            long usedKbps = entry.getValue() == null ? 0L : entry.getValue();
            if (usedKbps > 0L) target.merge(entry.getKey(), usedKbps, Math::max);
        }
    }

    private static boolean sameContent(SatelliteNetworkState previous, SatelliteNetworkState next) {
        return previous.bodies()
            .equals(next.bodies())
            && previous.links()
                .equals(next.links())
            && previous.pendingData()
                .equals(next.pendingData());
    }

    private static final class TeamRuntime {

        // Data buffers and processing progress are intentionally not saved yet.
        // Current sources generate continuously without consuming finite inputs, and
        // gameplay depends on sustained flow rather than accumulated data. Losing the
        // small transient buffer on reload is acceptable, including EU already spent
        // processing it. Revisit persistence when data generation consumes finite items
        // or other scarce resources.

        private SatelliteNetworkState state;
        private final Map<SatelliteNetworkGraph.Edge, ActiveDataUsage> activeUsage = new HashMap<>();
        private final Map<SatelliteNetworkGraph.DirectedEdge, ActiveDataUsage> activeDirectionalUsage = new HashMap<>();
        private final SatelliteDataBufferStore buffers = new SatelliteDataBufferStore();
        private final SatelliteDataEndpointRegistry endpoints = new SatelliteDataEndpointRegistry();
        private final Map<CelestialObjectKey, Long> prospectingFlow = new HashMap<>();
        private final Map<UUID, BandwidthReservation> reservations = new LinkedHashMap<>();

        private TeamRuntime(UUID teamId) {
            state = SatelliteNetworkState.empty(teamId, 0);
        }
    }

    private record BandwidthReservation(CelestialObjectKey from, CelestialObjectKey to, long requiredKbps,
        List<SatelliteNetworkGraph.Edge> path) {}

    private record BandwidthUsage(Map<SatelliteNetworkGraph.Edge, Long> usedByEdge,
        Map<SatelliteNetworkGraph.DirectedEdge, Long> directedUsedByEdge, Map<CelestialObjectKey, Long> usedByBody) {}

    private record ActiveDataUsage(long usedKbps, int ticksLeft) {

        private ActiveDataUsage {
            usedKbps = Math.max(0L, usedKbps);
            ticksLeft = Math.max(0, ticksLeft);
        }

        private ActiveDataUsage tick() {
            return new ActiveDataUsage(usedKbps, ticksLeft - 1);
        }

        private boolean expired() {
            return ticksLeft <= 0;
        }
    }
}
