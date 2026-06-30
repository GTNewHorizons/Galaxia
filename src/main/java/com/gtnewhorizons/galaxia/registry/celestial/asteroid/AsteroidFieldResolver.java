package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;

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
        List<AsteroidFieldNode> reachableAnchors = new ArrayList<>();
        for (AsteroidNodePreset preset : profile.nodePresets()) {
            AsteroidFieldNode node = resolveNodeUnchecked(beltId, profile, preset.index());
            nodes.add(node);
            if (initialDetectionState(node) == AsteroidDetectionState.DETECTED) {
                reachableAnchors.add(node);
            }
        }
        for (int ordinal = 0; ordinal < profile.totalNodes(); ordinal++) {
            int index = AsteroidSlotRanges.generatedSlot(ordinal);
            AsteroidFieldNode node = generatedSizeClass(profile, index) == AsteroidSizeClass.LARGE
                ? resolveNodeUnchecked(beltId, profile, index)
                : resolveReachableGeneratedNode(beltId, profile, index, reachableAnchors);
            nodes.add(node);
            reachableAnchors.add(node);
        }
        nodes.sort(Comparator.comparingInt(AsteroidFieldNode::index));
        validateReachability(profile, nodes);
        return List.copyOf(nodes);
    }

    public static AsteroidFieldNode resolveNode(CelestialObjectId beltId, AsteroidFieldProfile profile, int index) {
        Objects.requireNonNull(beltId, "beltId cannot be null");
        Objects.requireNonNull(profile, "profile cannot be null");
        if (!profile.hasNodeIndex(index)) {
            throw new IllegalArgumentException("node index must be within the asteroid field profile");
        }
        return resolveAll(beltId, profile).stream()
            .filter(node -> node.index() == index)
            .findFirst()
            .orElseThrow();
    }

    public static AsteroidFieldNode resolveSavedNode(CelestialObjectId beltId, AsteroidFieldProfile profile,
        int index) {
        Objects.requireNonNull(beltId, "beltId cannot be null");
        Objects.requireNonNull(profile, "profile cannot be null");
        if (profile.hasNodeIndex(index)) {
            return resolveNode(beltId, profile, index);
        }
        if (AsteroidSlotRanges.isGeneratedSlot(index)) {
            return resolveNodeUnchecked(beltId, profile, index);
        }
        return resolveUnregisteredSavedNode(beltId, profile, index);
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

    private static AsteroidFieldNode resolveReachableGeneratedNode(CelestialObjectId beltId,
        AsteroidFieldProfile profile, int index, List<AsteroidFieldNode> reachableAnchors) {
        if (reachableAnchors.isEmpty()) {
            throw new IllegalStateException("Asteroid field has hidden generated asteroids but no visible anchor");
        }
        long baseSeed = nodeSeed(beltId, profile, index);
        AsteroidFieldNode anchor = reachableAnchors
            .get((int) Math.floorMod(mix(baseSeed, 8L), reachableAnchors.size()));
        double anchorRadius = AsteroidFieldOrbitModel.resolveRadius(profile, anchor);
        double anchorAngle = Math.toRadians(anchor.angleOffsetDeg());
        double anchorX = Math.cos(anchorAngle) * anchorRadius;
        double anchorY = Math.sin(anchorAngle) * anchorRadius;

        for (int attempt = 0; attempt < 32; attempt++) {
            double scanRadius = profile.satelliteScanRadius();
            double distance = scanRadius == 0.0 ? 0.0
                : scanRadius * (0.2 + unitDouble(mix(baseSeed, 11L + attempt)) * 0.75);
            double offsetAngle = unitDouble(mix(baseSeed, 47L + attempt)) * Math.PI * 2.0;
            double x = anchorX + Math.cos(offsetAngle) * distance;
            double y = anchorY + Math.sin(offsetAngle) * distance;
            double radius = Math.sqrt(x * x + y * y);
            if (radius >= profile.innerOrbitalRadius() && radius <= profile.outerOrbitalRadius()) {
                return resolveGeneratedNodeAtPosition(
                    beltId,
                    profile,
                    index,
                    normalizeDegrees(Math.toDegrees(Math.atan2(y, x))),
                    (radius - profile.innerOrbitalRadius())
                        / (profile.outerOrbitalRadius() - profile.innerOrbitalRadius()));
            }
        }

        return resolveGeneratedNodeAtPosition(beltId, profile, index, anchor.angleOffsetDeg(), anchor.orbitalDepth01());
    }

    private static AsteroidFieldNode resolveGeneratedNodeAtPosition(CelestialObjectId beltId,
        AsteroidFieldProfile profile, int index, double angleOffsetDeg, double orbitalDepth01) {
        long baseSeed = nodeSeed(beltId, profile, index);
        AsteroidSizeClass sizeClass = generatedSizeClass(profile, index);
        return new AsteroidFieldNode(
            new MinorCelestialBodyId(beltId, index),
            beltId,
            index,
            displayName(beltId, index),
            AsteroidNodeKind.GENERATED,
            sizeClass,
            defaultInitialDetectionState(sizeClass),
            null,
            angleOffsetDeg,
            orbitalDepth01,
            selectOreProfile(profile, unitDouble(mix(baseSeed, 3L))),
            new AsteroidAppearanceProfile("generated_asteroid_tiles", mix(baseSeed, 4L)));
    }

    private static AsteroidFieldNode resolveUnregisteredSavedNode(CelestialObjectId beltId,
        AsteroidFieldProfile profile, int index) {
        long baseSeed = nodeSeed(beltId, profile, index);
        AsteroidSizeClass sizeClass = AsteroidSizeClass.SMALL;
        return new AsteroidFieldNode(
            new MinorCelestialBodyId(beltId, index),
            beltId,
            index,
            displayName(beltId, index),
            savedNodeKind(index),
            sizeClass,
            defaultInitialDetectionState(sizeClass),
            unitDouble(mix(baseSeed, 1L)) * 360.0,
            unitDouble(mix(baseSeed, 2L)),
            selectOreProfile(profile, unitDouble(mix(baseSeed, 3L))),
            new AsteroidAppearanceProfile("generated_asteroid_tiles", mix(baseSeed, 4L)));
    }

    private static AsteroidNodeKind savedNodeKind(int index) {
        if (AsteroidSlotRanges.isLoreSlot(index)) return AsteroidNodeKind.LORE;
        if (AsteroidSlotRanges.isUniqueSlot(index)) return AsteroidNodeKind.UNIQUE;
        return AsteroidNodeKind.GENERATED;
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

    private static void validateReachability(AsteroidFieldProfile profile, List<AsteroidFieldNode> nodes) {
        Set<MinorCelestialBodyId> visited = new HashSet<>();
        Queue<AsteroidFieldNode> queue = new ArrayDeque<>();
        for (AsteroidFieldNode node : nodes) {
            if (initialDetectionState(node) == AsteroidDetectionState.DETECTED) {
                visited.add(node.id());
                queue.add(node);
            }
        }

        while (!queue.isEmpty()) {
            AsteroidFieldNode current = queue.remove();
            for (AsteroidFieldNode candidate : nodes) {
                if (!visited.contains(candidate.id())
                    && distance(profile, current, candidate) <= profile.satelliteScanRadius()) {
                    visited.add(candidate.id());
                    queue.add(candidate);
                }
            }
        }

        for (AsteroidFieldNode node : nodes) {
            if (initialDetectionState(node) == AsteroidDetectionState.HIDDEN && !visited.contains(node.id())) {
                throw new IllegalStateException("unreachable hidden asteroid in scan graph: " + node.id());
            }
        }
    }

    private static double distance(AsteroidFieldProfile profile, AsteroidFieldNode first, AsteroidFieldNode second) {
        double firstRadius = AsteroidFieldOrbitModel.resolveRadius(profile, first);
        double firstAngle = Math.toRadians(first.angleOffsetDeg());
        double secondRadius = AsteroidFieldOrbitModel.resolveRadius(profile, second);
        double secondAngle = Math.toRadians(second.angleOffsetDeg());
        double dx = Math.cos(firstAngle) * firstRadius - Math.cos(secondAngle) * secondRadius;
        double dy = Math.sin(firstAngle) * firstRadius - Math.sin(secondAngle) * secondRadius;
        return Math.sqrt(dx * dx + dy * dy);
    }

    private static long nodeSeed(CelestialObjectId beltId, AsteroidFieldProfile profile, int index) {
        return mix(
            beltId.name()
                .hashCode(),
            profile.seedSalt(),
            profile.generationVersion(),
            index);
    }

    private static double normalizeDegrees(double value) {
        double normalized = value % 360.0;
        return normalized < 0.0 ? normalized + 360.0 : normalized;
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
