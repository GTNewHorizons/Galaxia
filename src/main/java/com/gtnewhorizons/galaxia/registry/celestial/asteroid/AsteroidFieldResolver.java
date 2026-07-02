package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;

public final class AsteroidFieldResolver {

    private static final double UINT53_TO_UNIT = 1.0 / (1L << 53);

    private AsteroidFieldResolver() {}

    public static List<AsteroidFieldNode> resolveAll(@Nonnull CelestialObjectId beltId,
        @Nonnull AsteroidFieldProfile profile) {
        List<AsteroidFieldNode> nodes = new ArrayList<>(profile.totalNodes());
        for (int index = 0; index < profile.totalNodes(); index++) {
            nodes.add(resolveNode(beltId, profile, index));
        }
        return List.copyOf(nodes);
    }

    public static AsteroidFieldNode resolveNode(@Nonnull CelestialObjectId beltId,
        @Nonnull AsteroidFieldProfile profile, int index) {
        if (index < 0 || index >= profile.totalNodes()) {
            throw new IllegalArgumentException("node index must be within the asteroid field profile");
        }
        return resolveNodeUnchecked(beltId, profile, index);
    }

    public static AsteroidDetectionState initialDetectionState(@Nonnull AsteroidFieldNode node) {
        return node.sizeClass() == AsteroidSizeClass.LARGE ? AsteroidDetectionState.DETECTED
            : AsteroidDetectionState.HIDDEN;
    }

    public static AsteroidOreKnowledgeState initialOreKnowledge(@Nonnull AsteroidFieldNode node) {
        if (node.sizeClass() != AsteroidSizeClass.LARGE) return AsteroidOreKnowledgeState.UNKNOWN;
        return rolledOreKnowledge(node, 5L);
    }

    public static AsteroidOreKnowledgeState oreKnowledgeAfterDetection(@Nonnull AsteroidFieldNode node) {
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
        return new AsteroidFieldNode(
            id,
            beltId,
            index,
            displayName(beltId, index),
            AsteroidNodeKind.GENERATED,
            sizeClass(profile, index),
            unitDouble(mix(baseSeed, 1L)) * 360.0,
            unitDouble(mix(baseSeed, 2L)),
            selectOreProfile(profile, unitDouble(mix(baseSeed, 3L))),
            new AsteroidAppearanceProfile("generated_asteroid_tiles", mix(baseSeed, 4L)));
    }

    private static AsteroidSizeClass sizeClass(AsteroidFieldProfile profile, int index) {
        if (index < profile.largeCount()) return AsteroidSizeClass.LARGE;
        if (index < profile.largeCount() + profile.mediumCount()) return AsteroidSizeClass.MEDIUM;
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
        return beltId.name() + " " + (index + 1);
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
