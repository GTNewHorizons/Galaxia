package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

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
 * The resolver is intentionally pure: the same belt id and profile always produce
 * the same nodes. Player-specific discovery state lives in
 * {@link com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeService},
 * not in these resolved definitions.
 */
public final class AsteroidFieldResolver {

    // Field resolution is called by registry, starmap, and scanning code. Cache
    // the immutable result so those callers share one deterministic node list.
    private static final Map<ResolveAllKey, ResolvedField> RESOLVE_ALL_CACHE = new ConcurrentHashMap<>();

    private static final long INITIAL_ORE_KNOWLEDGE_SALT = 5L;
    private static final long LAYOUT_REVISION_SALT = 0x004C41594F555452L;
    private static final double INITIAL_PROFILE_CHANCE = 0.20;

    private record ResolveAllKey(CelestialObjectId beltId, AsteroidFieldProfile profile) {}

    /** Nodes of one belt, plus their index, so per-node lookups do not scan the list. */
    private record ResolvedField(List<AsteroidFieldNode> nodes, Map<Integer, AsteroidFieldNode> byIndex,
        long layoutRevision) {

        static ResolvedField of(List<AsteroidFieldNode> nodes) {
            Map<Integer, AsteroidFieldNode> byIndex = new LinkedHashMap<>();
            long layoutRevision = LAYOUT_REVISION_SALT;
            for (AsteroidFieldNode node : nodes) {
                if (byIndex.put(node.index(), node) != null) {
                    throw new IllegalStateException("duplicate asteroid node index: " + node.index());
                }
                layoutRevision = DeterministicHash.mix(
                    layoutRevision,
                    node.index(),
                    node.sizeClass()
                        .ordinal(),
                    node.initialDetectionState()
                        .ordinal(),
                    Double.doubleToLongBits(node.angleOffsetDeg()),
                    Double.doubleToLongBits(node.orbitalDepth01()));
            }
            return new ResolvedField(nodes, Map.copyOf(byIndex), layoutRevision);
        }
    }

    private AsteroidFieldResolver() {}

    public static List<AsteroidFieldNode> resolveAll(@Nonnull CelestialObjectId beltId,
        @Nonnull AsteroidFieldProfile profile) {
        return field(beltId, profile).nodes();
    }

    /** Stable fingerprint of the resolved membership and discovery geometry. */
    public static long layoutRevision(@Nonnull CelestialObjectId beltId, @Nonnull AsteroidFieldProfile profile) {
        return field(beltId, profile).layoutRevision();
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
        return AsteroidPlacementGraph.resolveAll(beltId, profile);
    }

    /** Node at its final position, after the placement graph has moved it. This is what the rest of the mod sees. */
    public static AsteroidFieldNode placedNode(@Nonnull CelestialObjectId beltId, @Nonnull AsteroidFieldProfile profile,
        int index) {
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
