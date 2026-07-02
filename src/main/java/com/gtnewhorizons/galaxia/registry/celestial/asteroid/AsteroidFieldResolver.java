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
            if (AsteroidGeneratedSlotAllocator.generatedSizeClass(profile, index) != AsteroidSizeClass.LARGE) continue;
            AsteroidFieldNode node = resolveNodeUnchecked(beltId, profile, index);
            nodes.add(node);
            reachableAnchors.add(new ReachableAnchor(node, 0));
        }
        // Medium and small generated asteroids are resolved in slot order, but
        // each new node may use any already reachable node as an anchor. This
        // produces scan chains instead of isolated hidden islands.
        for (int ordinal = 0; ordinal < profile.totalNodes(); ordinal++) {
            int index = AsteroidSlotRanges.generatedSlot(ordinal);
            if (AsteroidGeneratedSlotAllocator.generatedSizeClass(profile, index) == AsteroidSizeClass.LARGE) continue;
            ResolvedGeneratedNode resolved = resolveReachableGeneratedNode(beltId, profile, index, reachableAnchors);
            nodes.add(resolved.node());
            reachableAnchors.add(new ReachableAnchor(resolved.node(), resolved.depth()));
        }
        nodes.sort(Comparator.comparingInt(AsteroidFieldNode::index));
        validateReachability(profile, nodes);
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
        return AsteroidInitialKnowledgeRules.initialDetectionState(node);
    }

    public static AsteroidOreKnowledgeState initialOreKnowledge(@Nonnull AsteroidFieldNode node) {
        return AsteroidInitialKnowledgeRules.initialOreKnowledge(node);
    }

    public static AsteroidOreKnowledgeState oreKnowledgeAfterDetection(@Nonnull AsteroidFieldNode node) {
        return AsteroidInitialKnowledgeRules.oreKnowledgeAfterDetection(node);
    }

    private static AsteroidFieldNode resolveNodeUnchecked(CelestialObjectId beltId, AsteroidFieldProfile profile,
        int index) {
        long baseSeed = AsteroidFieldDeterminism.nodeSeed(beltId, profile, index);
        MinorCelestialBodyId id = new MinorCelestialBodyId(beltId, index);
        AsteroidNodePreset preset = profile.nodePreset(index)
            .orElse(null);
        AsteroidSizeClass sizeClass = preset == null ? AsteroidGeneratedSlotAllocator.generatedSizeClass(profile, index)
            : preset.sizeClass();
        return new AsteroidFieldNode(
            id,
            beltId,
            index,
            preset == null ? displayName(beltId, index) : preset.displayName(),
            preset == null ? AsteroidNodeKind.GENERATED : preset.kind(),
            sizeClass,
            preset == null ? AsteroidInitialKnowledgeRules.defaultInitialDetectionState(sizeClass)
                : preset.initialDetectionState(),
            preset == null ? null : preset.initialOreKnowledgeState(),
            preset == null ? AsteroidGeneratedSlotAllocator.generatedAngleOffsetDeg(profile, index, baseSeed, sizeClass)
                : preset.angleOffsetDeg() != null ? preset.angleOffsetDeg()
                    : AsteroidFieldDeterminism.unitDouble(AsteroidFieldDeterminism.mix(baseSeed, 1L)) * 360.0,
            preset != null && preset.orbitalDepth01() != null ? preset.orbitalDepth01()
                : AsteroidFieldDeterminism.unitDouble(AsteroidFieldDeterminism.mix(baseSeed, 2L)),
            preset != null && preset.oreProfileId() != null ? selectOreProfile(profile, preset.oreProfileId())
                : selectOreProfile(profile, AsteroidFieldDeterminism.unitDouble(AsteroidFieldDeterminism.mix(baseSeed, 3L))),
            preset != null && preset.appearance() != null ? preset.appearance()
                : new AsteroidAppearanceProfile("generated_asteroid_tiles", AsteroidFieldDeterminism.mix(baseSeed, 4L)));
    }

    private static ResolvedGeneratedNode resolveReachableGeneratedNode(CelestialObjectId beltId,
        AsteroidFieldProfile profile, int index, List<ReachableAnchor> reachableAnchors) {
        if (reachableAnchors.isEmpty()) {
            throw new IllegalStateException("Asteroid field has hidden generated asteroids but no visible anchor");
        }
        long baseSeed = AsteroidFieldDeterminism.nodeSeed(beltId, profile, index);
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
                    : scanRadius * (0.2 + AsteroidFieldDeterminism.unitDouble(
                        AsteroidFieldDeterminism.mix(
                            baseSeed,
                            anchor.node()
                                .index(),
                            11L + attempt))
                        * 0.75);
                double offsetAngle = AsteroidFieldDeterminism.unitDouble(
                    AsteroidFieldDeterminism.mix(
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
        return AsteroidFieldDeterminism.unitDouble(
            AsteroidFieldDeterminism.mix(
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
            AsteroidFieldDeterminism.normalizeDegrees(Math.toDegrees(Math.atan2(y, x))),
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
        long baseSeed = AsteroidFieldDeterminism.nodeSeed(beltId, profile, index);
        AsteroidSizeClass sizeClass = AsteroidGeneratedSlotAllocator.generatedSizeClass(profile, index);
        return new AsteroidFieldNode(
            new MinorCelestialBodyId(beltId, index),
            beltId,
            index,
            displayName(beltId, index),
            AsteroidNodeKind.GENERATED,
            sizeClass,
            AsteroidInitialKnowledgeRules.defaultInitialDetectionState(sizeClass),
            null,
            angleOffsetDeg,
            orbitalDepth01,
            selectOreProfile(profile, AsteroidFieldDeterminism.unitDouble(AsteroidFieldDeterminism.mix(baseSeed, 3L))),
            new AsteroidAppearanceProfile("generated_asteroid_tiles", AsteroidFieldDeterminism.mix(baseSeed, 4L)));
    }

    private static AsteroidFieldNode resolveUnregisteredSavedNode(CelestialObjectId beltId,
        AsteroidFieldProfile profile, int index) {
        long baseSeed = AsteroidFieldDeterminism.nodeSeed(beltId, profile, index);
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
            AsteroidInitialKnowledgeRules.defaultInitialDetectionState(sizeClass),
            null,
            AsteroidFieldDeterminism.unitDouble(AsteroidFieldDeterminism.mix(baseSeed, 1L)) * 360.0,
            AsteroidFieldDeterminism.unitDouble(AsteroidFieldDeterminism.mix(baseSeed, 2L)),
            selectOreProfile(profile, AsteroidFieldDeterminism.unitDouble(AsteroidFieldDeterminism.mix(baseSeed, 3L))),
            new AsteroidAppearanceProfile("generated_asteroid_tiles", AsteroidFieldDeterminism.mix(baseSeed, 4L)));
    }

    private static AsteroidNodeKind savedNodeKind(int index) {
        if (AsteroidSlotRanges.isLoreSlot(index)) return AsteroidNodeKind.LORE;
        if (AsteroidSlotRanges.isUniqueSlot(index)) return AsteroidNodeKind.UNIQUE;
        return AsteroidNodeKind.GENERATED;
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

    private static String displayName(CelestialObjectId beltId, int index) {
        int displayNumber = AsteroidSlotRanges.isGeneratedSlot(index) ? AsteroidSlotRanges.generatedOrdinal(index) + 1
            : index + 1;
        return beltId.name() + " " + displayNumber;
    }

}
