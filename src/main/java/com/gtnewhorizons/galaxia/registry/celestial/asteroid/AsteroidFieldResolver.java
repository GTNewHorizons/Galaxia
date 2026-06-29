package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;

public final class AsteroidFieldResolver {

    private static final double UINT53_TO_UNIT = 1.0 / (1L << 53);

    private AsteroidFieldResolver() {}

    public static List<AsteroidFieldNode> resolveAll(CelestialObjectId beltId, AsteroidFieldProfile profile) {
        Objects.requireNonNull(beltId, "beltId cannot be null");
        Objects.requireNonNull(profile, "profile cannot be null");

        List<AsteroidFieldNode> nodes = new ArrayList<>(
            profile.totalNodes() + profile.nodePresets()
                .size());
        for (AsteroidNodePreset preset : profile.nodePresets()) {
            nodes.add(resolveNode(beltId, profile, preset.index()));
        }
        for (int ordinal = 0; ordinal < profile.totalNodes(); ordinal++) {
            nodes.add(resolveNode(beltId, profile, AsteroidSlotRanges.generatedSlot(ordinal)));
        }
        nodes.sort(Comparator.comparingInt(AsteroidFieldNode::index));
        return List.copyOf(nodes);
    }

    public static AsteroidFieldNode resolveNode(CelestialObjectId beltId, AsteroidFieldProfile profile, int index) {
        Objects.requireNonNull(beltId, "beltId cannot be null");
        Objects.requireNonNull(profile, "profile cannot be null");
        if (!profile.hasNodeIndex(index)) {
            throw new IllegalArgumentException("node index must be within the asteroid field profile");
        }
        return resolveNodeUnchecked(beltId, profile, index);
    }

    public static AsteroidDetectionState initialDetectionState(AsteroidFieldNode node) {
        Objects.requireNonNull(node, "node cannot be null");
        return node.initialDetectionState();
    }

    public static AsteroidOreKnowledgeState initialOreKnowledge(AsteroidFieldNode node) {
        Objects.requireNonNull(node, "node cannot be null");
        if (node.initialOreKnowledgeState() != null) return node.initialOreKnowledgeState();
        if (node.sizeClass() != AsteroidSizeClass.LARGE) return AsteroidOreKnowledgeState.UNKNOWN;
        return rolledOreKnowledge(node, 5L);
    }

    public static AsteroidOreKnowledgeState oreKnowledgeAfterDetection(AsteroidFieldNode node) {
        Objects.requireNonNull(node, "node cannot be null");
        if (node.sizeClass() == AsteroidSizeClass.SMALL) return AsteroidOreKnowledgeState.UNKNOWN;
        return rolledOreKnowledge(node, 6L);
    }

    private static AsteroidFieldNode resolveNodeUnchecked(CelestialObjectId beltId, AsteroidFieldProfile profile,
        int index) {
        long baseSeed = mix(
            beltId.name()
                .hashCode(),
            profile.seedSalt(),
            profile.generationVersion(),
            index);
        MinorCelestialBodyId id = new MinorCelestialBodyId(beltId, index);
        AsteroidNodePreset preset = profile.nodePreset(index)
            .orElse(null);
        AsteroidSizeClass sizeClass = preset == null ? generatedSizeClass(profile, index) : preset.sizeClass();
        return new AsteroidFieldNode(
            id,
            beltId,
            index,
            preset == null ? displayName(beltId, index) : preset.displayName(),
            preset == null ? AsteroidNodeKind.GENERATED : preset.kind(),
            sizeClass,
            preset == null ? defaultInitialDetectionState(sizeClass) : preset.initialDetectionState(),
            preset == null ? null : preset.initialOreKnowledgeState(),
            preset != null && preset.angleOffsetDeg() != null ? preset.angleOffsetDeg()
                : unitDouble(mix(baseSeed, 1L)) * 360.0,
            preset != null && preset.orbitalDepth01() != null ? preset.orbitalDepth01() : unitDouble(mix(baseSeed, 2L)),
            preset != null && preset.oreProfileId() != null ? selectOreProfile(profile, preset.oreProfileId())
                : selectOreProfile(profile, unitDouble(mix(baseSeed, 3L))),
            preset != null && preset.appearance() != null ? preset.appearance()
                : new AsteroidAppearanceProfile("generated_asteroid_tiles", mix(baseSeed, 4L)));
    }

    private static AsteroidDetectionState defaultInitialDetectionState(AsteroidSizeClass sizeClass) {
        return sizeClass == AsteroidSizeClass.LARGE ? AsteroidDetectionState.DETECTED : AsteroidDetectionState.HIDDEN;
    }

    private static AsteroidSizeClass generatedSizeClass(AsteroidFieldProfile profile, int index) {
        int ordinal = AsteroidSlotRanges.generatedOrdinal(index);
        if (ordinal < profile.largeCount()) return AsteroidSizeClass.LARGE;
        if (ordinal < profile.largeCount() + profile.mediumCount()) return AsteroidSizeClass.MEDIUM;
        return AsteroidSizeClass.SMALL;
    }

    private static AsteroidOreProfile selectOreProfile(AsteroidFieldProfile profile, double roll) {
        double totalWeight = 0.0;
        for (AsteroidOreProfile oreProfile : profile.oreProfiles()) {
            totalWeight += oreProfile.weight();
        }
        double cursor = roll * totalWeight;
        for (AsteroidOreProfile oreProfile : profile.oreProfiles()) {
            cursor -= oreProfile.weight();
            if (cursor <= 0.0) return oreProfile;
        }
        return profile.oreProfiles()
            .get(
                profile.oreProfiles()
                    .size() - 1);
    }

    private static AsteroidOreProfile selectOreProfile(AsteroidFieldProfile profile, String oreProfileId) {
        return profile.oreProfiles()
            .stream()
            .filter(
                oreProfile -> oreProfile.id()
                    .equals(oreProfileId))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Unknown asteroid ore profile: " + oreProfileId));
    }

    private static AsteroidOreKnowledgeState rolledOreKnowledge(AsteroidFieldNode node, long salt) {
        double roll = unitDouble(
            mix(
                node.appearance()
                    .variantSeed(),
                salt));
        if (roll < 0.20) return AsteroidOreKnowledgeState.PROFILE;
        if (roll < 0.55) return AsteroidOreKnowledgeState.SIGNATURE;
        return AsteroidOreKnowledgeState.UNKNOWN;
    }

    private static String displayName(CelestialObjectId beltId, int index) {
        int displayNumber = AsteroidSlotRanges.isGeneratedSlot(index) ? AsteroidSlotRanges.generatedOrdinal(index) + 1
            : index + 1;
        return beltId.name() + " " + displayNumber;
    }

    private static double unitDouble(long value) {
        return ((value >>> 11) & ((1L << 53) - 1)) * UINT53_TO_UNIT;
    }

    private static long mix(long first, long... rest) {
        long value = mix64(first);
        for (long next : rest) {
            value = mix64(value ^ next);
        }
        return value;
    }

    private static long mix64(long value) {
        value += 0x9E3779B97F4A7C15L;
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}
