package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeFacts;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeFacts.CelestialResourceKnowledgeState;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeFacts.DiscoveryState;
import com.gtnewhorizons.galaxia.registry.util.DeterministicHash;

/**
 * Deterministically materializes asteroid definitions from a field profile.
 *
 * The resolver is intentionally pure: the same belt id, profile, and generation
 * version always produce the same nodes. Player-specific discovery state lives in
 * {@link com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeService},
 * not in these resolved definitions.
 */
public final class AsteroidFieldResolver {

    // Field resolution is called by registry, starmap, and scanning code. Cache
    // the immutable result so those callers share one deterministic node list.
    private static final Map<ResolveAllKey, ResolvedField> RESOLVE_ALL_CACHE = new ConcurrentHashMap<>();

    private static final long INITIAL_ORE_KNOWLEDGE_SALT = 5L;
    private static final double INITIAL_PROFILE_CHANCE = 0.20;

    private record ResolveAllKey(CelestialObjectId beltId, AsteroidFieldProfile profile) {}

    /** Nodes of one belt, plus their index, so per-node lookups do not scan the list. */
    private record ResolvedField(List<AsteroidFieldNode> nodes, Map<Integer, AsteroidFieldNode> byIndex) {

        static ResolvedField of(List<AsteroidFieldNode> nodes) {
            Map<Integer, AsteroidFieldNode> byIndex = new LinkedHashMap<>();
            for (AsteroidFieldNode node : nodes) {
                if (byIndex.put(node.index(), node) != null) {
                    throw new IllegalStateException("duplicate asteroid node index: " + node.index());
                }
            }
            return new ResolvedField(nodes, Map.copyOf(byIndex));
        }
    }

    private AsteroidFieldResolver() {}

    public static List<AsteroidFieldNode> resolveAll(@Nonnull CelestialObjectId beltId,
        @Nonnull AsteroidFieldProfile profile) {
        return field(beltId, profile).nodes();
    }

    /** The node in this belt at {@code index}, or empty when the belt has no such slot. */
    public static Optional<AsteroidFieldNode> findNode(@Nonnull CelestialObjectId beltId,
        @Nonnull AsteroidFieldProfile profile, int index) {
        return Optional.ofNullable(
            field(beltId, profile).byIndex()
                .get(index));
    }

    private static ResolvedField field(CelestialObjectId beltId, AsteroidFieldProfile profile) {
        return RESOLVE_ALL_CACHE.computeIfAbsent(
            new ResolveAllKey(beltId, profile),
            key -> ResolvedField.of(resolveAllUncached(key.beltId(), key.profile())));
    }

    private static List<AsteroidFieldNode> resolveAllUncached(CelestialObjectId beltId, AsteroidFieldProfile profile) {
        List<AsteroidFieldNode> nodes = new ArrayList<>(
            profile.totalNodes() + profile.authoredAsteroids()
                .size());
        List<AsteroidPlacementGraph.ReachableAnchor> reachableAnchors = new ArrayList<>();

        for (AuthoredAsteroidDefinition definition : profile.authoredAsteroids()) {
            AsteroidFieldNode node = AsteroidNodeMaterializer.resolveNode(beltId, profile, definition.index());
            nodes.add(node);
            if (node.initialDetectionState() == DiscoveryState.DISCOVERED) {
                reachableAnchors.add(AsteroidPlacementGraph.anchor(node, 0));
            }
        }

        for (int ordinal = 0; ordinal < profile.totalNodes(); ordinal++) {
            int index = AsteroidSlotRanges.generatedSlot(ordinal);
            if (AsteroidGeneratedSlotAllocator.generatedSizeClass(profile, index) != AsteroidSizeClass.LARGE) continue;
            AsteroidFieldNode node = AsteroidNodeMaterializer.resolveNode(beltId, profile, index);
            nodes.add(node);
            reachableAnchors.add(AsteroidPlacementGraph.anchor(node, 0));
        }

        for (int ordinal = 0; ordinal < profile.totalNodes(); ordinal++) {
            int index = AsteroidSlotRanges.generatedSlot(ordinal);
            if (AsteroidGeneratedSlotAllocator.generatedSizeClass(profile, index) == AsteroidSizeClass.LARGE) continue;
            AsteroidPlacementGraph.ResolvedGeneratedNode resolved = AsteroidPlacementGraph
                .resolveReachableGeneratedNode(beltId, profile, index, reachableAnchors);
            nodes.add(resolved.node());
            reachableAnchors.add(AsteroidPlacementGraph.anchor(resolved.node(), resolved.depth()));
        }

        nodes.sort(Comparator.comparingInt(AsteroidFieldNode::index));
        AsteroidPlacementGraph.validateReachability(profile, nodes);
        return List.copyOf(nodes);
    }

    public static AsteroidFieldNode resolveNode(@Nonnull CelestialObjectId beltId,
        @Nonnull AsteroidFieldProfile profile, int index) {
        if (!profile.hasNodeIndex(index)) {
            throw new IllegalArgumentException("node index must be within the asteroid field profile");
        }
        return findNode(beltId, profile, index).orElseThrow();
    }

    public static CelestialKnowledgeFacts initialFacts(@Nonnull AsteroidFieldNode node) {
        return CelestialKnowledgeFacts.of(node.initialDetectionState(), initialOreKnowledge(node));
    }

    /**
     * Ore knowledge a team starts with, before any scan. Authored nodes may pin it;
     * otherwise only LARGE nodes get a deterministic chance at a free profile.
     */
    public static CelestialResourceKnowledgeState initialOreKnowledge(@Nonnull AsteroidFieldNode node) {
        if (node.initialOreKnowledgeState() != null) return node.initialOreKnowledgeState();
        if (node.sizeClass() != AsteroidSizeClass.LARGE) return CelestialResourceKnowledgeState.UNKNOWN;
        double roll = DeterministicHash.unitDouble(
            DeterministicHash.mix(
                node.appearance()
                    .variantSeed(),
                INITIAL_ORE_KNOWLEDGE_SALT));
        return roll < INITIAL_PROFILE_CHANCE ? CelestialResourceKnowledgeState.PROFILE
            : CelestialResourceKnowledgeState.UNKNOWN;
    }

    static DiscoveryState defaultInitialDetectionState(AsteroidSizeClass sizeClass) {
        return sizeClass == AsteroidSizeClass.LARGE ? DiscoveryState.DISCOVERED : DiscoveryState.HIDDEN;
    }
}
