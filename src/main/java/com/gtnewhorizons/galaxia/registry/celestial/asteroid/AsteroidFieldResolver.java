package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialResourceKnowledgeState;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.DiscoveryState;

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
    private static final Map<ResolveAllKey, List<AsteroidFieldNode>> RESOLVE_ALL_CACHE = new ConcurrentHashMap<>();

    private record ResolveAllKey(CelestialObjectId beltId, AsteroidFieldProfile profile) {}

    private AsteroidFieldResolver() {}

    public static List<AsteroidFieldNode> resolveAll(@Nonnull CelestialObjectId beltId,
        @Nonnull AsteroidFieldProfile profile) {
        return RESOLVE_ALL_CACHE.computeIfAbsent(
            new ResolveAllKey(beltId, profile),
            key -> resolveAllUncached(key.beltId(), key.profile()));
    }

    private static List<AsteroidFieldNode> resolveAllUncached(CelestialObjectId beltId, AsteroidFieldProfile profile) {
        List<AsteroidFieldNode> nodes = new ArrayList<>(
            profile.totalNodes() + profile.authoredAsteroids()
                .size());
        List<AsteroidPlacementGraph.ReachableAnchor> reachableAnchors = new ArrayList<>();

        for (AuthoredAsteroidDefinition definition : profile.authoredAsteroids()) {
            AsteroidFieldNode node = AsteroidNodeMaterializer.resolveNode(beltId, profile, definition.index());
            nodes.add(node);
            if (initialDetectionState(node) == DiscoveryState.DISCOVERED) {
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
        return resolveAll(beltId, profile).stream()
            .filter(node -> node.index() == index)
            .findFirst()
            .orElseThrow();
    }

    public static DiscoveryState initialDetectionState(@Nonnull AsteroidFieldNode node) {
        return AsteroidInitialKnowledgeRules.initialDetectionState(node);
    }

    public static CelestialResourceKnowledgeState initialOreKnowledge(@Nonnull AsteroidFieldNode node) {
        return AsteroidInitialKnowledgeRules.initialOreKnowledge(node);
    }

    public static CelestialResourceKnowledgeState oreKnowledgeAfterDetection(@Nonnull AsteroidFieldNode node) {
        return AsteroidInitialKnowledgeRules.oreKnowledgeAfterDetection(node);
    }
}
