package com.gtnewhorizons.galaxia.registry.satellite;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldKnowledgeSnapshot;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldKnowledgeStore;
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
    private static final Map<UUID, SatelliteNetworkState> STATES = new HashMap<>();
    private static final Map<UUID, Map<SatelliteNetworkGraph.Edge, ActiveDataUsage>> ACTIVE_DATA_USAGE = new HashMap<>();
    private static final Map<UUID, Map<SatelliteNetworkGraph.DirectedEdge, ActiveDataUsage>> ACTIVE_DIRECTIONAL_DATA_USAGE = new HashMap<>();
    private static final SatelliteDataBufferStore DATA_BUFFERS = new SatelliteDataBufferStore();
    private static final SatelliteDataEndpointRegistry DATA_ENDPOINTS = new SatelliteDataEndpointRegistry();
    private static final AsteroidFieldKnowledgeStore ASTEROID_KNOWLEDGE = new AsteroidFieldKnowledgeStore();
    private static final AsteroidSatelliteScanService ASTEROID_SCANS = new AsteroidSatelliteScanService(
        ASTEROID_KNOWLEDGE,
        bodyId -> GalaxiaCelestialAPI.get(bodyId)
            .map(
                body -> body.properties()
                    .asteroidFieldProfile()));
    private static final AsteroidProspectingDataHandler ASTEROID_PROSPECTING = AsteroidProspectingDataHandler
        .live(ASTEROID_KNOWLEDGE);

    private SatelliteNetworkService() {}

    public static SatelliteNetworkState current(UUID teamId) {
        return STATES.getOrDefault(teamId, SatelliteNetworkState.empty(teamId, 0));
    }

    /*
     * Normal rebuild entry point for the live server. It samples current orbital positions and satellite counts, then
     * delegates to the snapshot builder that also accounts for buffered data traffic.
     */
    public static SatelliteNetworkState rebuild(UUID teamId, double orbitalTime) {
        List<SatelliteNetworkGraph.Node> nodes = buildNodes(orbitalTime);
        Map<CelestialObjectId, Long> capacityByBody = new HashMap<>();
        for (SatelliteNetworkGraph.Node node : nodes) {
            long capacity = CelestialAssetStore.SERVER.satelliteBandwidth(teamId, node.bodyId());
            if (capacity > 0L) capacityByBody.put(node.bodyId(), capacity);
        }
        return rebuild(teamId, nodes, capacityByBody, DATA_BUFFERS);
    }

    static SatelliteNetworkState rebuild(UUID teamId, List<SatelliteNetworkGraph.Node> nodes,
        Map<CelestialObjectId, Long> capacityByBody, Map<SatelliteNetworkGraph.Edge, Long> usedByEdge) {
        SatelliteNetworkState previous = current(teamId);
        SatelliteNetworkState next = SatelliteNetworkCalculator
            .forTeam(teamId, previous.revision() + 1, nodes, capacityByBody, usedByEdge);
        if (sameContent(previous, next)) return previous;
        STATES.put(teamId, next);
        return next;
    }

    static SatelliteNetworkState rebuild(UUID teamId, List<SatelliteNetworkGraph.Node> nodes,
        Map<CelestialObjectId, Long> capacityByBody, SatelliteDataBufferStore bufferStore) {
        SatelliteNetworkState previous = current(teamId);
        List<SatelliteNetworkGraph.Node> activeNodes = activeNodes(nodes, capacityByBody);
        /*
         * Rebuild is deliberately two-phase. First derive the topology from satellite ownership and capacity; then use
         * that topology to plan buffered data transfers. The final snapshot is what gets synced, so clients receive
         * edges and their colours/usage atomically.
         */
        SatelliteNetworkState unloaded = SatelliteNetworkCalculator
            .forTeam(teamId, previous.revision() + 1, activeNodes, capacityByBody, Map.of());
        clearActiveUsageIfTopologyChanged(teamId, previous, unloaded);
        SatelliteDataTransferPlanner.Plan plan = SatelliteDataTransferPlanner.plan(teamId, unloaded, bufferStore);
        // Planned transfers are real throughput; active usage keeps short visual pulses visible between rebuilds.
        Map<SatelliteNetworkGraph.Edge, Long> usedByEdge = mergedUsage(plan.usedByEdge(), activeUsage(teamId));
        Map<SatelliteNetworkGraph.DirectedEdge, Long> directedUsedByEdge = mergedUsage(
            plan.directedUsedByEdge(),
            activeDirectionalUsage(teamId));
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
            .withPendingData(pendingData(teamId, bufferStore, plan.transfers()));
        if (sameContent(previous, next)) return previous;
        STATES.put(teamId, next);
        return next;
    }

    public static void clear() {
        STATES.clear();
        ACTIVE_DATA_USAGE.clear();
        ACTIVE_DIRECTIONAL_DATA_USAGE.clear();
        DATA_BUFFERS.clear();
        DATA_ENDPOINTS.clear();
        ASTEROID_KNOWLEDGE.clear();
        ASTEROID_SCANS.clear();
    }

    static SatelliteDataBufferStore dataBuffers() {
        return DATA_BUFFERS;
    }

    static AsteroidFieldKnowledgeStore asteroidKnowledge() {
        return ASTEROID_KNOWLEDGE;
    }

    public static List<AsteroidFieldKnowledgeSnapshot> asteroidKnowledgeSnapshots(UUID teamId) {
        return ASTEROID_KNOWLEDGE.snapshots(teamId);
    }

    public static Map<UUID, List<AsteroidFieldKnowledgeSnapshot>> asteroidKnowledgeSnapshotsByTeam() {
        return ASTEROID_KNOWLEDGE.snapshotsByTeam();
    }

    public static List<AsteroidSatelliteScanSnapshot> asteroidScanSnapshots(UUID teamId) {
        return ASTEROID_SCANS.snapshots(teamId);
    }

    public static Map<UUID, List<AsteroidSatelliteScanSnapshot>> asteroidScanSnapshotsByTeam() {
        return ASTEROID_SCANS.snapshotsByTeam();
    }

    public static List<AsteroidSatelliteScanCompletionSnapshot> asteroidScanCompletionSnapshots(UUID teamId) {
        return ASTEROID_SCANS.completionSnapshots(teamId);
    }

    public static Map<UUID, List<AsteroidSatelliteScanCompletionSnapshot>> asteroidScanCompletionSnapshotsByTeam() {
        return ASTEROID_SCANS.completionSnapshotsByTeam();
    }

    public static void restoreAsteroidKnowledge(UUID teamId, List<AsteroidFieldKnowledgeSnapshot> snapshots) {
        ASTEROID_KNOWLEDGE.restore(
            teamId,
            snapshots,
            bodyId -> GalaxiaCelestialAPI.get(bodyId)
                .map(
                    body -> body.properties()
                        .asteroidFieldProfile()));
    }

    public static void restoreAsteroidScans(UUID teamId, List<AsteroidSatelliteScanSnapshot> snapshots) {
        ASTEROID_SCANS.restore(teamId, snapshots);
    }

    public static void restoreAsteroidScanCompletions(UUID teamId,
        List<AsteroidSatelliteScanCompletionSnapshot> snapshots) {
        ASTEROID_SCANS.restoreCompletions(teamId, snapshots);
    }

    public static boolean canStartProcess(UUID teamId, CelestialObjectId bodyId, SatelliteDataKey outputKey) {
        return DATA_BUFFERS.canStart(teamId, bodyId, outputKey, current(teamId).capacityKbps(bodyId));
    }

    /*
     * Called from the server tick. Endpoint modules advance jobs here; the resulting short-lived usage is kept until a
     * rebuild packages it into the synced network snapshot.
     */
    public static void tickDataJobs() {
        tickDataJobs(1);
    }

    static void tickDataJobs(int elapsedTicks) {
        if (elapsedTicks < 0) throw new IllegalArgumentException("elapsedTicks must be non-negative");
        for (int tick = 0; tick < elapsedTicks; tick++) {
            tickDataJobsSingleTick();
        }
    }

    private static void tickDataJobsSingleTick() {
        tickActiveUsage();
        for (UUID teamId : DATA_ENDPOINTS.teamIds()) {
            SatelliteDataJobService.Usage usage = SatelliteDataJobService.tickEndpointsUsage(
                teamId,
                DATA_ENDPOINTS.endpoints(teamId),
                DATA_BUFFERS,
                current(teamId),
                ASTEROID_PROSPECTING);
            recordActiveUsage(teamId, usage);
        }
        tickAsteroidScans();
    }

    private static void tickAsteroidScans() {
        Map<UUID, List<CelestialAsset>> prospectingSatellitesByTeam = new HashMap<>();
        for (CelestialAsset asset : CelestialAssetStore.allAssets()) {
            if (!(asset instanceof Satellite satellite)) continue;
            if (satellite.satelliteKind() != SatelliteKind.PROSPECTING || !satellite.celestialObjectId.isMinorBody()) {
                continue;
            }
            UUID teamId = CelestialAssetStore.getTeamId(satellite.assetId);
            if (teamId == null) continue;
            prospectingSatellitesByTeam.computeIfAbsent(teamId, ignored -> new ArrayList<>())
                .add(satellite);
        }
        for (Map.Entry<UUID, List<CelestialAsset>> entry : prospectingSatellitesByTeam.entrySet()) {
            ASTEROID_SCANS.tick(entry.getKey(), entry.getValue(), 1);
        }
    }

    public static void refreshAssetEndpoints(UUID teamId, CelestialAsset asset) {
        if (asset instanceof AutomatedFacility facility) {
            DATA_ENDPOINTS.refreshFacility(teamId, facility);
        } else if (asset != null) {
            DATA_ENDPOINTS.unregisterAsset(asset.assetId);
        }
    }

    public static void refreshFacilityEndpoints(AutomatedFacility facility) {
        if (facility == null) return;
        refreshAssetEndpoints(CelestialAssetStore.getTeamId(facility.assetId), facility);
    }

    public static void unregisterAssetEndpoints(CelestialAsset.ID assetId) {
        DATA_ENDPOINTS.unregisterAsset(assetId);
    }

    public static void unregisterTeamEndpoints(UUID teamId) {
        DATA_ENDPOINTS.unregisterTeam(teamId);
    }

    /*
     * Used after persisted assets are loaded. Runtime module edits update individual facilities, but world load needs a
     * full pass to repopulate the endpoint registry from saved assets.
     */
    public static void rebuildDataEndpointsFromAssets() {
        DATA_ENDPOINTS.clear();
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
        ACTIVE_DATA_USAGE.entrySet()
            .removeIf(teamEntry -> {
                teamEntry.getValue()
                    .replaceAll((edge, usage) -> usage.tick());
                teamEntry.getValue()
                    .entrySet()
                    .removeIf(
                        edgeEntry -> edgeEntry.getValue()
                            .expired());
                return teamEntry.getValue()
                    .isEmpty();
            });
        ACTIVE_DIRECTIONAL_DATA_USAGE.entrySet()
            .removeIf(teamEntry -> {
                teamEntry.getValue()
                    .replaceAll((edge, usage) -> usage.tick());
                teamEntry.getValue()
                    .entrySet()
                    .removeIf(
                        edgeEntry -> edgeEntry.getValue()
                            .expired());
                return teamEntry.getValue()
                    .isEmpty();
            });
    }

    /*
     * Store both undirected and directed usage. Undirected usage colours the link; directed usage controls packet
     * direction on that same link.
     */
    private static void recordActiveUsage(UUID teamId, SatelliteDataJobService.Usage usage) {
        if (teamId == null || usage == null) return;
        recordActiveUsage(teamId, usage.usedByEdge(), ACTIVE_DATA_USAGE);
        recordActiveUsage(teamId, usage.directedUsedByEdge(), ACTIVE_DIRECTIONAL_DATA_USAGE);
    }

    private static <T> void recordActiveUsage(UUID teamId, Map<T, Long> usedByEdge,
        Map<UUID, Map<T, ActiveDataUsage>> activeUsage) {
        if (teamId == null || usedByEdge == null || usedByEdge.isEmpty()) return;
        Map<T, ActiveDataUsage> teamUsage = activeUsage.computeIfAbsent(teamId, ignored -> new HashMap<>());
        for (Map.Entry<T, Long> entry : usedByEdge.entrySet()) {
            long usedKbps = entry.getValue() == null ? 0L : entry.getValue();
            if (usedKbps <= 0L) continue;
            teamUsage.merge(
                entry.getKey(),
                new ActiveDataUsage(usedKbps, DATA_USAGE_VISIBLE_TICKS),
                (left, right) -> new ActiveDataUsage(Math.max(left.usedKbps(), right.usedKbps()), right.ticksLeft()));
        }
    }

    /*
     * The graph is built from orbital-space positions, not screen-space positions. Zoom/culling should not change the
     * server-side topology or path capacity.
     */
    private static List<SatelliteNetworkGraph.Node> buildNodes(double orbitalTime) {
        CelestialObject root = GalaxiaCelestialAPI.root();
        return GalaxiaCelestialAPI.getAllBodies()
            .values()
            .stream()
            .filter(body -> body.objectClass() != CelestialObject.Class.GALAXY)
            .filter(body -> body.objectClass() != CelestialObject.Class.STAR)
            .map(body -> nodeFor(root, body, orbitalTime))
            .toList();
    }

    private static SatelliteNetworkGraph.Node nodeFor(CelestialObject root, CelestialObject body, double orbitalTime) {
        OrbitalMechanics.OrbitalState state = OrbitalMechanics.resolveWorldState(root, body, orbitalTime);
        return new SatelliteNetworkGraph.Node(
            body.requireRegisteredId(),
            body.parentId() == null ? null
                : body.parentId()
                    .requireRegisteredBodyId(),
            orbitalOrder(body),
            state.x(),
            state.y(),
            Math.max(0.0D, body.spriteSize()));
    }

    private static double orbitalOrder(CelestialObject body) {
        OrbitalParams params = body.orbitalParams();
        return params == null ? body.requireRegisteredId()
            .ordinal() : params.semiMajorAxis();
    }

    private static List<SatelliteNetworkGraph.Node> activeNodes(List<SatelliteNetworkGraph.Node> nodes,
        Map<CelestialObjectId, Long> capacityByBody) {
        Map<CelestialObjectId, Long> capacities = capacityByBody == null ? Map.of() : capacityByBody;
        return (nodes == null ? List.<SatelliteNetworkGraph.Node>of() : nodes).stream()
            .filter(node -> capacities.getOrDefault(node.bodyId(), 0L) > 0L)
            .toList();
    }

    /*
     * If the edge set changes, old active-usage pulses would colour links that no longer exist. Drop them so topology
     * changes and link colours arrive as one atomic snapshot.
     */
    private static void clearActiveUsageIfTopologyChanged(UUID teamId, SatelliteNetworkState previous,
        SatelliteNetworkState next) {
        if (!ACTIVE_DATA_USAGE.containsKey(teamId) && !ACTIVE_DIRECTIONAL_DATA_USAGE.containsKey(teamId)) return;
        if (!edgeSet(previous).equals(edgeSet(next))) {
            ACTIVE_DATA_USAGE.remove(teamId);
            ACTIVE_DIRECTIONAL_DATA_USAGE.remove(teamId);
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
    private static List<SatelliteNetworkState.PendingData> pendingData(UUID teamId,
        SatelliteDataBufferStore bufferStore, List<SatelliteDataTransferPlanner.Transfer> transfers) {
        return bufferStore.producedEntries(teamId)
            .stream()
            .map(
                entry -> new SatelliteNetworkState.PendingData(
                    entry.bodyId(),
                    pendingDestinations(entry, transfers),
                    entry.key(),
                    entry.deciKb()))
            .toList();
    }

    private static List<CelestialObjectId> pendingDestinations(SatelliteDataBufferStore.Entry entry,
        List<SatelliteDataTransferPlanner.Transfer> transfers) {
        if (entry == null || transfers == null || transfers.isEmpty()) return List.of();
        return transfers.stream()
            .filter(transfer -> transfer.sourceBodyId() == entry.bodyId())
            .filter(
                transfer -> transfer.sourceKey()
                    .equals(entry.key()))
            .map(SatelliteDataTransferPlanner.Transfer::destinationBodyId)
            .distinct()
            .sorted()
            .toList();
    }

    private static Map<SatelliteNetworkGraph.Edge, Long> activeUsage(UUID teamId) {
        Map<SatelliteNetworkGraph.Edge, ActiveDataUsage> teamUsage = ACTIVE_DATA_USAGE.get(teamId);
        if (teamUsage == null || teamUsage.isEmpty()) return Map.of();
        Map<SatelliteNetworkGraph.Edge, Long> usage = new HashMap<>();
        for (Map.Entry<SatelliteNetworkGraph.Edge, ActiveDataUsage> entry : teamUsage.entrySet()) {
            usage.put(
                entry.getKey(),
                entry.getValue()
                    .usedKbps());
        }
        return usage;
    }

    private static Map<SatelliteNetworkGraph.DirectedEdge, Long> activeDirectionalUsage(UUID teamId) {
        Map<SatelliteNetworkGraph.DirectedEdge, ActiveDataUsage> teamUsage = ACTIVE_DIRECTIONAL_DATA_USAGE.get(teamId);
        if (teamUsage == null || teamUsage.isEmpty()) return Map.of();
        Map<SatelliteNetworkGraph.DirectedEdge, Long> usage = new HashMap<>();
        for (Map.Entry<SatelliteNetworkGraph.DirectedEdge, ActiveDataUsage> entry : teamUsage.entrySet()) {
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
