package com.gtnewhorizons.galaxia.registry.satellite;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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

    private SatelliteNetworkService() {}

    private static TeamRuntime runtime(UUID teamId) {
        return RUNTIMES.computeIfAbsent(teamId, TeamRuntime::new);
    }

    public static SatelliteNetworkState current(UUID teamId) {
        TeamRuntime runtime = RUNTIMES.get(teamId);
        return runtime == null ? SatelliteNetworkState.empty(teamId, 0) : runtime.state;
    }

    /*
     * Normal rebuild entry point for the live server. It samples current orbital positions and satellite counts, then
     * delegates to the snapshot builder that also accounts for buffered data traffic.
     */
    public static SatelliteNetworkState rebuild(UUID teamId, double orbitalTime) {
        List<SatelliteNetworkGraph.Node> nodes = buildNodes(teamId, orbitalTime);
        Map<CelestialObjectKey, Long> capacityByBody = new HashMap<>();
        for (SatelliteNetworkGraph.Node node : nodes) {
            long capacity = CelestialAssetStore.SERVER.satelliteBandwidth(teamId, node.bodyKey());
            if (capacity > 0L) capacityByBody.put(node.bodyKey(), capacity);
        }
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
         * edges and their colours/usage atomically.
         */
        SatelliteNetworkState unloaded = SatelliteNetworkCalculator
            .forTeam(teamId, previous.revision() + 1, activeNodes, capacityByBody, Map.of());
        clearActiveUsageIfTopologyChanged(runtime, previous, unloaded);
        SatelliteDataTransferPlanner.Plan plan = SatelliteDataTransferPlanner
            .plan(teamId, unloaded, bufferStore, runtime.endpoints.demands());
        // Planned transfers are real throughput; active usage keeps short visual pulses visible between rebuilds.
        Map<SatelliteNetworkGraph.Edge, Long> usedByEdge = mergedUsage(plan.usedByEdge(), activeUsage(runtime));
        Map<SatelliteNetworkGraph.DirectedEdge, Long> directedUsedByEdge = mergedUsage(
            plan.directedUsedByEdge(),
            activeDirectionalUsage(runtime));
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
                usedByEdge,
                directedUsedByEdge,
                plan.usedByBody())
            .withPendingData(pendingData(bufferStore, plan.transfers()));
        if (sameContent(previous, next)) return previous;
        runtime.state = next;
        return next;
    }

    public static void clear() {
        RUNTIMES.clear();
    }

    static SatelliteDataBufferStore dataBuffers(UUID teamId) {
        return runtime(teamId).buffers;
    }

    public static boolean canStartProcess(UUID teamId, CelestialObjectKey bodyKey, SatelliteDataKey outputKey) {
        return runtime(teamId).buffers.canStart(bodyKey, outputKey, current(teamId).capacityKbps(bodyKey));
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
        for (Map.Entry<UUID, TeamRuntime> entry : RUNTIMES.entrySet()) {
            TeamRuntime runtime = entry.getValue();
            if (runtime.endpoints.isEmpty()) continue;
            SatelliteDataJobService.Usage usage = SatelliteDataJobService
                .tickEndpointsUsage(entry.getKey(), runtime.endpoints.endpoints(), runtime.buffers, runtime.state);
            recordActiveUsage(runtime, usage);
        }
    }

    public static void refreshAssetEndpoints(UUID teamId, CelestialAsset asset) {
        if (teamId == null) return;
        if (asset instanceof AutomatedFacility facility) {
            runtime(teamId).endpoints.refreshFacility(facility);
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

    public static void unregisterTeamEndpoints(UUID teamId) {
        RUNTIMES.remove(teamId);
    }

    /*
     * Used after persisted assets are loaded. Runtime module edits update individual facilities, but world load needs a
     * full pass to repopulate the endpoint registry from saved assets.
     */
    public static void rebuildDataEndpointsFromAssets() {
        for (TeamRuntime runtime : RUNTIMES.values()) runtime.endpoints.clear();
        for (CelestialAsset asset : CelestialAssetStore.allAssets()) {
            refreshAssetEndpoints(CelestialAssetStore.getTeamId(asset.assetId), asset);
        }
    }

    /*
     * Active usage is a visual bridge between the tick that applied a transfer and the next snapshot rebuild. Without
     * it
     * small transfers can complete before the starmap ever receives a coloured link.
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
     * Store both undirected and directed usage. Undirected usage colours the link; directed usage controls packet
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
    private static List<SatelliteNetworkGraph.Node> buildNodes(UUID teamId, double orbitalTime) {
        CelestialObject root = GalaxiaCelestialAPI.root();
        List<SatelliteNetworkGraph.Node> nodes = GalaxiaCelestialAPI.getAllBodies()
            .values()
            .stream()
            .filter(body -> body.objectClass() != CelestialObject.Class.GALAXY)
            .filter(body -> body.objectClass() != CelestialObject.Class.STAR)
            .map(body -> nodeFor(root, body, orbitalTime))
            .toList();
        Set<CelestialObjectKey> minorBodyKeys = minorSatelliteBodyKeys(teamId);
        if (minorBodyKeys.isEmpty()) return nodes;
        List<SatelliteNetworkGraph.Node> withMinorBodies = new ArrayList<>(nodes);
        for (CelestialObjectKey key : minorBodyKeys) {
            GalaxiaCelestialAPI.get(key)
                .map(body -> nodeFor(root, body, orbitalTime))
                .ifPresent(withMinorBodies::add);
        }
        return List.copyOf(withMinorBodies);
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
     * If the edge set changes, old active-usage pulses would colour links that no longer exist. Drop them so topology
     * changes and link colours arrive as one atomic snapshot.
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

        private SatelliteNetworkState state;
        private final Map<SatelliteNetworkGraph.Edge, ActiveDataUsage> activeUsage = new HashMap<>();
        private final Map<SatelliteNetworkGraph.DirectedEdge, ActiveDataUsage> activeDirectionalUsage = new HashMap<>();
        private final SatelliteDataBufferStore buffers = new SatelliteDataBufferStore();
        private final SatelliteDataEndpointRegistry endpoints = new SatelliteDataEndpointRegistry();

        private TeamRuntime(UUID teamId) {
            state = SatelliteNetworkState.empty(teamId, 0);
        }
    }

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
