package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;

public final class AsteroidFieldNodeCatalog {

    private static final Map<CelestialObjectId, AsteroidFieldNodeCatalog> RESTORED = new ConcurrentHashMap<>();

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
        return fromSnapshots(beltId, profile, List.of());
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
