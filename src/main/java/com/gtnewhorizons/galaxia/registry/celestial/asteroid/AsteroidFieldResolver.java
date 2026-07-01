package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;

/**
 * Deterministically materializes asteroid definitions from a field profile.
 *
 * The resolver is intentionally pure: the same belt id, profile, and generation
 * version always produce the same nodes. Player-specific discovery state lives
 * in {@link AsteroidFieldKnowledge}, not in these resolved definitions.
 */
public final class AsteroidFieldResolver {

    private static final double UINT53_TO_UNIT = 1.0 / (1L << 53);
    // Field resolution is called by registry, starmap, and scanning code. Cache
    // the immutable result so those callers share one deterministic node list.
    private static final Map<ResolveAllKey, List<AsteroidFieldNode>> RESOLVE_ALL_CACHE = new ConcurrentHashMap<>();

    private record ReachableAnchor(AsteroidFieldNode node, int depth) {}

    private record ResolvedGeneratedNode(AsteroidFieldNode node, int depth) {}

    private record GeneratedCandidate(AsteroidFieldNode node, int depth, double crowdingScore) {}

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
            profile.totalNodes() + profile.nodePresets()
                .size());
        List<ReachableAnchor> reachableAnchors = new ArrayList<>();
        // Visible presets and large generated asteroids are the player's entry
        // points into the graph. Hidden asteroids are placed only after these
        // anchors exist so satellite scanning can always expand from something.
        for (AsteroidNodePreset preset : profile.nodePresets()) {
            AsteroidFieldNode node = resolveNodeUnchecked(beltId, profile, preset.index());
            nodes.add(node);
            if (initialDetectionState(node) == AsteroidDetectionState.DETECTED) {
                reachableAnchors.add(new ReachableAnchor(node, 0));
            }
        }
        for (int ordinal = 0; ordinal < profile.totalNodes(); ordinal++) {
            int index = AsteroidSlotRanges.generatedSlot(ordinal);
            if (generatedSizeClass(profile, index) != AsteroidSizeClass.LARGE) continue;
            AsteroidFieldNode node = resolveNodeUnchecked(beltId, profile, index);
            nodes.add(node);
            reachableAnchors.add(new ReachableAnchor(node, 0));
        }
        // Medium and small generated asteroids are resolved in slot order, but
        // each new node may use any already reachable node as an anchor. This
        // produces scan chains instead of isolated hidden islands.
        for (int ordinal = 0; ordinal < profile.totalNodes(); ordinal++) {
            int index = AsteroidSlotRanges.generatedSlot(ordinal);
            if (generatedSizeClass(profile, index) == AsteroidSizeClass.LARGE) continue;
            ResolvedGeneratedNode resolved = resolveReachableGeneratedNode(beltId, profile, index, reachableAnchors);
            nodes.add(resolved.node());
            reachableAnchors.add(new ReachableAnchor(resolved.node(), resolved.depth()));
        }
        nodes.sort(Comparator.comparingInt(AsteroidFieldNode::index));
        validateReachability(profile, nodes);
        return List.copyOf(nodes);
    }

    public static AsteroidFieldNode resolveNode(@Nonnull CelestialObjectId beltId, @Nonnull AsteroidFieldProfile profile,
        int index) {
        if (!profile.hasNodeIndex(index)) {
            throw new IllegalArgumentException("node index must be within the asteroid field profile");
        }
        return resolveAll(beltId, profile).stream()
            .filter(node -> node.index() == index)
            .findFirst()
            .orElseThrow();
    }

    public static AsteroidFieldNode resolveSavedNode(@Nonnull CelestialObjectId beltId,
        @Nonnull AsteroidFieldProfile profile, int index) {
        if (profile.hasNodeIndex(index)) {
            return resolveNode(beltId, profile, index);
        }
        if (AsteroidSlotRanges.isGeneratedSlot(index)) {
            return resolveNodeUnchecked(beltId, profile, index);
        }
        return resolveUnregisteredSavedNode(beltId, profile, index);
    }

    public static AsteroidDetectionState initialDetectionState(@Nonnull AsteroidFieldNode node) {
        return node.initialDetectionState();
    }

    public static AsteroidOreKnowledgeState initialOreKnowledge(@Nonnull AsteroidFieldNode node) {
        if (node.initialOreKnowledgeState() != null) return node.initialOreKnowledgeState();
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
            preset == null ? generatedAngleOffsetDeg(profile, index, baseSeed, sizeClass)
                : preset.angleOffsetDeg() != null ? preset.angleOffsetDeg() : unitDouble(mix(baseSeed, 1L)) * 360.0,
            preset != null && preset.orbitalDepth01() != null ? preset.orbitalDepth01() : unitDouble(mix(baseSeed, 2L)),
            preset != null && preset.oreProfileId() != null ? selectOreProfile(profile, preset.oreProfileId())
                : selectOreProfile(profile, unitDouble(mix(baseSeed, 3L))),
            preset != null && preset.appearance() != null ? preset.appearance()
                : new AsteroidAppearanceProfile("generated_asteroid_tiles", mix(baseSeed, 4L)));
    }

    private static ResolvedGeneratedNode resolveReachableGeneratedNode(CelestialObjectId beltId,
        AsteroidFieldProfile profile, int index, List<ReachableAnchor> reachableAnchors) {
        if (reachableAnchors.isEmpty()) {
            throw new IllegalStateException("Asteroid field has hidden generated asteroids but no visible anchor");
        }
        long baseSeed = nodeSeed(beltId, profile, index);
        AsteroidFieldNode naturalNode = resolveNodeUnchecked(beltId, profile, index);
        List<GeneratedCandidate> candidates = new ArrayList<>();
        double targetRadius = AsteroidFieldOrbitModel.resolveRadius(profile, naturalNode);
        double targetAngle = Math.toRadians(naturalNode.angleOffsetDeg());
        double targetX = Math.cos(targetAngle) * targetRadius;
        double targetY = Math.sin(targetAngle) * targetRadius;

        // Start from the natural deterministic position. If it is already in
        // range of an anchor we keep it; otherwise we project candidate positions
        // from every reachable anchor toward or around that natural target.
        for (ReachableAnchor anchor : reachableAnchors) {
            if (distance(profile, anchor.node(), naturalNode) <= profile.satelliteScanRadius()) {
                candidates.add(
                    new GeneratedCandidate(
                        naturalNode,
                        anchor.depth() + 1,
                        placementScore(profile, naturalNode, naturalNode, reachableAnchors)));
            }

            double anchorRadius = AsteroidFieldOrbitModel.resolveRadius(profile, anchor.node());
            double anchorAngle = Math.toRadians(
                anchor.node()
                    .angleOffsetDeg());
            double anchorX = Math.cos(anchorAngle) * anchorRadius;
            double anchorY = Math.sin(anchorAngle) * anchorRadius;
            double targetDx = targetX - anchorX;
            double targetDy = targetY - anchorY;
            double targetDistance = Math.sqrt(targetDx * targetDx + targetDy * targetDy);
            if (targetDistance > 0.0001) {
                double candidateDistance = Math.min(profile.satelliteScanRadius() * 0.95, targetDistance);
                double x = anchorX + targetDx / targetDistance * candidateDistance;
                double y = anchorY + targetDy / targetDistance * candidateDistance;
                addGeneratedCandidateAtPosition(
                    candidates,
                    beltId,
                    profile,
                    index,
                    anchor.depth() + 1,
                    x,
                    y,
                    naturalNode,
                    reachableAnchors);
            }

            for (int attempt = 0; attempt < 32; attempt++) {
                double scanRadius = profile.satelliteScanRadius();
                double candidateDistance = scanRadius == 0.0 ? 0.0
                    : scanRadius * (0.2 + unitDouble(
                        mix(
                            baseSeed,
                            anchor.node()
                                .index(),
                            11L + attempt))
                        * 0.75);
                double offsetAngle = unitDouble(
                    mix(
                        baseSeed,
                        anchor.node()
                            .index(),
                        47L + attempt))
                    * Math.PI
                    * 2.0;
                double x = anchorX + Math.cos(offsetAngle) * candidateDistance;
                double y = anchorY + Math.sin(offsetAngle) * candidateDistance;
                addGeneratedCandidateAtPosition(
                    candidates,
                    beltId,
                    profile,
                    index,
                    anchor.depth() + 1,
                    x,
                    y,
                    naturalNode,
                    reachableAnchors);
            }
        }

        GeneratedCandidate selected = selectCandidate(baseSeed, candidates);
        if (selected != null) return new ResolvedGeneratedNode(selected.node(), selected.depth());

        ReachableAnchor anchor = reachableAnchors.stream()
            .min(Comparator.comparingDouble(candidate -> crowdingScore(profile, candidate.node(), reachableAnchors)))
            .orElseThrow();
        return new ResolvedGeneratedNode(
            resolveGeneratedNodeAtPosition(
                beltId,
                profile,
                index,
                anchor.node()
                    .angleOffsetDeg(),
                anchor.node()
                    .orbitalDepth01()),
            anchor.depth() + 1);
    }

    private static GeneratedCandidate selectCandidate(long baseSeed, List<GeneratedCandidate> candidates) {
        if (candidates.isEmpty()) return null;
        return candidates.stream()
            .min(
                Comparator
                    .comparingDouble(candidate -> candidate.crowdingScore() + selectionJitter(baseSeed, candidate)))
            .orElse(null);
    }

    private static double selectionJitter(long baseSeed, GeneratedCandidate candidate) {
        return unitDouble(
            mix(
                baseSeed,
                Double.doubleToLongBits(
                    candidate.node()
                        .angleOffsetDeg()),
                Double.doubleToLongBits(
                    candidate.node()
                        .orbitalDepth01()),
                97L))
            * 0.05;
    }

    private static void addGeneratedCandidateAtPosition(List<GeneratedCandidate> candidates, CelestialObjectId beltId,
        AsteroidFieldProfile profile, int index, int depth, double x, double y, AsteroidFieldNode targetNode,
        List<ReachableAnchor> reachableAnchors) {
        double radius = Math.sqrt(x * x + y * y);
        if (radius < profile.innerOrbitalRadius() || radius > profile.outerOrbitalRadius()) return;

        AsteroidFieldNode candidate = resolveGeneratedNodeAtPosition(
            beltId,
            profile,
            index,
            normalizeDegrees(Math.toDegrees(Math.atan2(y, x))),
            (radius - profile.innerOrbitalRadius()) / (profile.outerOrbitalRadius() - profile.innerOrbitalRadius()));
        candidates.add(
            new GeneratedCandidate(candidate, depth, placementScore(profile, candidate, targetNode, reachableAnchors)));
    }

    private static double placementScore(AsteroidFieldProfile profile, AsteroidFieldNode candidate,
        AsteroidFieldNode targetNode, List<ReachableAnchor> reachableAnchors) {
        double targetDistance = distance(profile, candidate, targetNode);
        double normalizedTargetDistance = profile.satelliteScanRadius() <= 0.0 ? targetDistance
            : targetDistance / profile.satelliteScanRadius();
        // Lower is better. The first term keeps the candidate near its natural
        // deterministic position; the second term pushes it away from crowded
        // local clusters and already busy angular sectors.
        return normalizedTargetDistance * 1.6 + crowdingScore(profile, candidate, reachableAnchors) * 2.4;
    }

    private static double crowdingScore(AsteroidFieldProfile profile, AsteroidFieldNode candidate,
        List<ReachableAnchor> reachableAnchors) {
        double score = 0.0;
        int candidateSector = angularSector(candidate);
        for (ReachableAnchor anchor : reachableAnchors) {
            double distance = distance(profile, candidate, anchor.node());
            if (distance <= profile.satelliteScanRadius()) {
                double proximity = profile.satelliteScanRadius() == 0.0 ? 1.0
                    : 1.0 - distance / profile.satelliteScanRadius();
                score += 1.0 + proximity;
            }
            if (angularSector(anchor.node()) == candidateSector) score += 0.5;
        }
        return score;
    }

    private static int angularSector(AsteroidFieldNode node) {
        return (int) Math.floor(node.angleOffsetDeg() / 45.0);
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
        // Save data may reference a node that no longer exists in the current
        // profile. Keep the body addressable so player assets are not orphaned.
        AsteroidSizeClass sizeClass = AsteroidSizeClass.SMALL;
        return new AsteroidFieldNode(
            new MinorCelestialBodyId(beltId, index),
            beltId,
            index,
            displayName(beltId, index),
            savedNodeKind(index),
            sizeClass,
            defaultInitialDetectionState(sizeClass),
            null,
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
        if (ordinal >= profile.totalNodes()) return AsteroidSizeClass.SMALL;
        return generatedSizeClassAtOrdinal(profile, ordinal);
    }

    private static AsteroidSizeClass generatedSizeClassAtOrdinal(AsteroidFieldProfile profile, int ordinal) {
        int total = profile.totalNodes();
        int[] counts = { profile.largeCount(), profile.mediumCount(), profile.smallCount() };
        int[] emitted = new int[counts.length];
        int[] score = new int[counts.length];
        int selected = 0;
        for (int slot = 0; slot <= ordinal; slot++) {
            selected = nextInterleavedSizeClass(counts, emitted, score, total);
            emitted[selected]++;
            score[selected] -= total;
        }
        return switch (selected) {
            case 0 -> AsteroidSizeClass.LARGE;
            case 1 -> AsteroidSizeClass.MEDIUM;
            default -> AsteroidSizeClass.SMALL;
        };
    }

    private static int nextInterleavedSizeClass(int[] counts, int[] emitted, int[] score, int total) {
        int selected = -1;
        int bestScore = Integer.MIN_VALUE;
        for (int sizeClass = 0; sizeClass < counts.length; sizeClass++) {
            if (emitted[sizeClass] >= counts[sizeClass]) continue;
            score[sizeClass] += counts[sizeClass];
            if (score[sizeClass] > bestScore || score[sizeClass] == bestScore && counts[sizeClass] > counts[selected]) {
                selected = sizeClass;
                bestScore = score[sizeClass];
            }
        }
        if (selected < 0) throw new IllegalStateException("generated asteroid size class allocation exhausted");
        return selected;
    }

    private static double generatedAngleOffsetDeg(AsteroidFieldProfile profile, int index, long baseSeed,
        AsteroidSizeClass sizeClass) {
        int ordinal = AsteroidSlotRanges.generatedOrdinal(index);
        int classOrdinal = generatedSizeClassOrdinal(profile, ordinal, sizeClass);
        int classCount = generatedSizeClassCount(profile, sizeClass);
        if (classCount <= 0) return unitDouble(mix(baseSeed, 1L)) * 360.0;

        double sectorWidth = 360.0 / classCount;
        double phase = unitDouble(
            mix(
                profile.seedSalt(),
                profile.generationVersion(),
                sizeClass.name()
                    .hashCode(),
                19L))
            * sectorWidth;
        double jitterScale = sizeClass == AsteroidSizeClass.LARGE ? 0.18 : 0.55;
        double jitter = (unitDouble(mix(baseSeed, 1L)) - 0.5) * sectorWidth * jitterScale;
        return normalizeDegrees((classOrdinal + 0.5) * sectorWidth + phase + jitter);
    }

    private static int generatedSizeClassOrdinal(AsteroidFieldProfile profile, int ordinal,
        AsteroidSizeClass sizeClass) {
        int count = 0;
        int boundedOrdinal = Math.min(ordinal, profile.totalNodes());
        for (int previous = 0; previous < boundedOrdinal; previous++) {
            if (generatedSizeClassAtOrdinal(profile, previous) == sizeClass) count++;
        }
        if (ordinal >= profile.totalNodes() && sizeClass == AsteroidSizeClass.SMALL) {
            count += ordinal - profile.totalNodes();
        }
        return count;
    }

    private static int generatedSizeClassCount(AsteroidFieldProfile profile, AsteroidSizeClass sizeClass) {
        return switch (sizeClass) {
            case LARGE -> profile.largeCount();
            case MEDIUM -> profile.mediumCount();
            case SMALL -> profile.smallCount();
        };
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
