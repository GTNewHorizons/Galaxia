package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;

import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeFacts.DiscoveryState;

public final class AsteroidFieldNodeCatalog {

    private static final Map<CelestialObjectId, AsteroidFieldNodeCatalog> RESTORED = new ConcurrentHashMap<>();

    // Generated catalogs are a pure function of belt id and profile, and are rebuilt per node on
    // discovery ticks and starmap frames. Cache them like AsteroidFieldResolver caches its nodes.
    private static final Map<GeneratedKey, AsteroidFieldNodeCatalog> GENERATED = new ConcurrentHashMap<>();

    private record GeneratedKey(CelestialObjectId beltId, AsteroidFieldProfile profile) {}

    private final CelestialObjectId beltId;
    private final List<AsteroidFieldNode> nodes;
    private final Map<Integer, AsteroidFieldNode> nodesByIndex;

    private AsteroidFieldNodeCatalog(@Nonnull CelestialObjectId beltId, @Nonnull List<AsteroidFieldNode> nodes) {
        this.beltId = beltId;
        this.nodes = List.copyOf(nodes);
        Map<Integer, AsteroidFieldNode> byIndex = new LinkedHashMap<>();
        for (AsteroidFieldNode node : this.nodes) {
            if (node.beltId() != beltId) {
                throw new IllegalArgumentException("catalog node belt does not match catalog belt");
            }
            AsteroidFieldNode previous = byIndex.put(node.index(), node);
            if (previous != null) {
                throw new IllegalStateException("duplicate asteroid node index: " + node.index());
            }
        }
        this.nodesByIndex = Map.copyOf(byIndex);
    }

    public static AsteroidFieldNodeCatalog fromGenerated(@Nonnull CelestialObjectId beltId,
        @Nonnull AsteroidFieldProfile profile) {
        return GENERATED.computeIfAbsent(
            new GeneratedKey(beltId, profile),
            key -> fromSnapshots(key.beltId(), key.profile(), List.of()));
    }

    public static AsteroidFieldNodeCatalog fromSnapshots(@Nonnull CelestialObjectId beltId,
        @Nonnull AsteroidFieldProfile profile, @Nonnull List<AsteroidFieldNodeSnapshot> savedNodeSnapshots) {
        List<AsteroidFieldNodeSnapshot> snapshots = List.copyOf(savedNodeSnapshots);

        Map<Integer, AsteroidFieldNode> merged = new LinkedHashMap<>();
        for (AsteroidFieldNode node : AsteroidFieldResolver.resolveAll(beltId, profile)) {
            merged.put(node.index(), node);
        }

        Set<Integer> savedIndexes = new HashSet<>();
        for (AsteroidFieldNodeSnapshot snapshot : snapshots) {
            if (snapshot == null) {
                throw new IllegalArgumentException("node snapshot cannot be null");
            }
            if (!savedIndexes.add(snapshot.index())) {
                throw new IllegalStateException("duplicate asteroid node snapshot index: " + snapshot.index());
            }
            merged.put(snapshot.index(), snapshot.toNode(beltId));
        }

        List<AsteroidFieldNode> nodes = new ArrayList<>(merged.values());
        nodes.sort(Comparator.comparingInt(AsteroidFieldNode::index));
        return new AsteroidFieldNodeCatalog(beltId, nodes);
    }

    public static AsteroidFieldNodeCatalog restore(@Nonnull CelestialObjectId beltId,
        @Nonnull AsteroidFieldProfile profile, @Nonnull List<AsteroidFieldNodeSnapshot> savedNodeSnapshots) {

        AsteroidFieldNodeCatalog catalog = fromSnapshots(beltId, profile, savedNodeSnapshots);
        RESTORED.put(beltId, catalog);
        return catalog;
    }

    public static Optional<AsteroidFieldNodeCatalog> restored(@Nonnull CelestialObjectId beltId) {
        return Optional.ofNullable(RESTORED.get(beltId));
    }

    public static void clearRestored() {
        RESTORED.clear();
    }

    public static Set<CelestialObjectId> restoredBeltIds() {
        return Set.copyOf(RESTORED.keySet());
    }

    /**
     * Node content snapshots for the belts referenced by {@code minorKeys}.
     * <p>
     * TLDR: for every belt that owns a requested minor key, returns that belt's
     * requested node payloads plus its initial-discovered nodes, resolved from the
     * restored-or-generated catalog. Shared by world persistence and team-filtered
     * catalog sync so both build the same content union from a set of minor keys.
     */
    public static Map<CelestialObjectId, List<AsteroidFieldNodeSnapshot>> catalogSnapshotsForMinors(
        @Nonnull Collection<CelestialObjectKey> minorKeys) {
        Map<CelestialObjectId, Set<Integer>> requestedByBelt = new LinkedHashMap<>();
        for (CelestialObjectKey key : minorKeys) {
            if (key == null || !key.isMinorBody()) continue;
            MinorCelestialBodyId minorId = key.minorBodyId();
            requestedByBelt.computeIfAbsent(minorId.parentBodyId(), belt -> new LinkedHashSet<>())
                .add(minorId.index());
        }

        Map<CelestialObjectId, List<AsteroidFieldNodeSnapshot>> result = new LinkedHashMap<>();
        for (Map.Entry<CelestialObjectId, Set<Integer>> entry : requestedByBelt.entrySet()) {
            CelestialObjectId beltId = entry.getKey();
            AsteroidFieldProfile profile = profile(beltId).orElse(null);
            if (profile == null) continue;
            AsteroidFieldNodeCatalog catalog = restored(beltId).orElseGet(() -> fromGenerated(beltId, profile));
            Map<Integer, AsteroidFieldNodeSnapshot> byIndex = new LinkedHashMap<>();
            for (int index : entry.getValue()) {
                catalog.resolve(new MinorCelestialBodyId(beltId, index))
                    .map(AsteroidFieldNodeSnapshot::fromNode)
                    .ifPresent(snapshot -> byIndex.put(index, snapshot));
            }
            for (AsteroidFieldNode node : catalog.nodes()) {
                if (node.initialDetectionState() == DiscoveryState.DISCOVERED) {
                    byIndex.putIfAbsent(node.index(), AsteroidFieldNodeSnapshot.fromNode(node));
                }
            }
            result.put(beltId, List.copyOf(byIndex.values()));
        }
        return result;
    }

    private static Optional<AsteroidFieldProfile> profile(CelestialObjectId beltId) {
        return GalaxiaCelestialAPI.get(beltId)
            .map(
                body -> body.properties()
                    .asteroidFieldProfile());
    }

    public CelestialObjectId beltId() {
        return beltId;
    }

    public List<AsteroidFieldNode> nodes() {
        return nodes;
    }

    public Optional<AsteroidFieldNode> resolve(@Nonnull MinorCelestialBodyId id) {
        if (id.parentBodyId() != beltId) return Optional.empty();
        return Optional.ofNullable(nodesByIndex.get(id.index()));
    }

    public boolean containsIndex(int index) {
        return nodesByIndex.containsKey(index);
    }

    public List<AsteroidFieldNodeSnapshot> snapshots() {
        return nodes.stream()
            .map(AsteroidFieldNodeSnapshot::fromNode)
            .toList();
    }
}
