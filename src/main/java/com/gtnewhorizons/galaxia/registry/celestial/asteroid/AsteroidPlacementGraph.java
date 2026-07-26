package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeFacts.DiscoveryState;

final class AsteroidPlacementGraph {

    record ReachableAnchor(AsteroidFieldNode node, int depth) {}

    record ResolvedGeneratedNode(AsteroidFieldNode node, int depth) {}

    private record GeneratedCandidate(AsteroidFieldNode node, int depth, double crowdingScore) {}

    private AsteroidPlacementGraph() {}

    static ReachableAnchor anchor(AsteroidFieldNode node, int depth) {
        return new ReachableAnchor(node, depth);
    }

    static ResolvedGeneratedNode resolveReachableGeneratedNode(CelestialObjectId beltId, AsteroidFieldProfile profile,
        int index, List<ReachableAnchor> reachableAnchors) {
        if (reachableAnchors.isEmpty()) {
            throw new IllegalStateException("Asteroid field has hidden generated asteroids but no visible anchor");
        }
        AsteroidFieldDeterminism nodeSeed = AsteroidFieldDeterminism.forNode(beltId, profile, index);
        AsteroidFieldNode naturalNode = AsteroidNodeMaterializer.resolveNode(beltId, profile, index);
        List<GeneratedCandidate> candidates = new ArrayList<>();
        double targetRadius = AsteroidFieldOrbitResolver.resolveRadius(profile, naturalNode);
        double targetAngle = Math.toRadians(naturalNode.angleOffsetDeg());
        double targetX = Math.cos(targetAngle) * targetRadius;
        double targetY = Math.sin(targetAngle) * targetRadius;

        // Start from the natural deterministic position. If it is already in
        // range of an anchor we keep it; otherwise we project candidate positions
        // from every reachable anchor toward or around that natural target.
        for (ReachableAnchor anchor : reachableAnchors) {
            if (AsteroidFieldOrbitResolver.separation(profile, anchor.node(), naturalNode)
                <= profile.placementConnectionRadius()) {
                candidates.add(
                    new GeneratedCandidate(
                        naturalNode,
                        anchor.depth() + 1,
                        placementScore(profile, naturalNode, naturalNode, reachableAnchors)));
            }

            double anchorRadius = AsteroidFieldOrbitResolver.resolveRadius(profile, anchor.node());
            double anchorAngle = Math.toRadians(
                anchor.node()
                    .angleOffsetDeg());
            double anchorX = Math.cos(anchorAngle) * anchorRadius;
            double anchorY = Math.sin(anchorAngle) * anchorRadius;
            double targetDx = targetX - anchorX;
            double targetDy = targetY - anchorY;
            double targetDistance = Math.sqrt(targetDx * targetDx + targetDy * targetDy);
            if (targetDistance > 0.0001) {
                double candidateDistance = Math.min(profile.placementConnectionRadius() * 0.95, targetDistance);
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
                double scanRadius = profile.placementConnectionRadius();
                double candidateDistance = scanRadius == 0.0 ? 0.0
                    : scanRadius * (0.2 + nodeSeed.unit(
                        anchor.node()
                            .index(),
                        11L + attempt) * 0.75);
                double offsetAngle = nodeSeed.unit(
                    anchor.node()
                        .index(),
                    47L + attempt) * Math.PI * 2.0;
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

        GeneratedCandidate selected = selectCandidate(nodeSeed, candidates);
        if (selected != null) return new ResolvedGeneratedNode(selected.node(), selected.depth());

        ReachableAnchor anchor = reachableAnchors.stream()
            .min(Comparator.comparingDouble(candidate -> crowdingScore(profile, candidate.node(), reachableAnchors)))
            .orElseThrow();
        return new ResolvedGeneratedNode(
            AsteroidNodeMaterializer.resolveGeneratedNodeAtPosition(
                beltId,
                profile,
                index,
                anchor.node()
                    .angleOffsetDeg(),
                anchor.node()
                    .orbitalDepth01()),
            anchor.depth() + 1);
    }

    static void validateReachability(AsteroidFieldProfile profile, List<AsteroidFieldNode> nodes) {
        Set<MinorCelestialBodyId> visited = new HashSet<>();
        Queue<AsteroidFieldNode> queue = new ArrayDeque<>();
        for (AsteroidFieldNode node : nodes) {
            if (node.initialDetectionState() == DiscoveryState.DISCOVERED) {
                visited.add(node.id());
                queue.add(node);
            }
        }

        while (!queue.isEmpty()) {
            AsteroidFieldNode current = queue.remove();
            for (AsteroidFieldNode candidate : nodes) {
                if (!visited.contains(candidate.id())
                    && AsteroidFieldOrbitResolver.separation(profile, current, candidate)
                        <= profile.placementConnectionRadius()) {
                    visited.add(candidate.id());
                    queue.add(candidate);
                }
            }
        }

        for (AsteroidFieldNode node : nodes) {
            if (node.initialDetectionState() == DiscoveryState.HIDDEN && !visited.contains(node.id())) {
                throw new IllegalStateException("unreachable hidden asteroid in scan graph: " + node.id());
            }
        }
    }

    private static GeneratedCandidate selectCandidate(AsteroidFieldDeterminism nodeSeed,
        List<GeneratedCandidate> candidates) {
        if (candidates.isEmpty()) return null;
        return candidates.stream()
            .min(
                Comparator
                    .comparingDouble(candidate -> candidate.crowdingScore() + selectionJitter(nodeSeed, candidate)))
            .orElse(null);
    }

    private static double selectionJitter(AsteroidFieldDeterminism nodeSeed, GeneratedCandidate candidate) {
        return nodeSeed.unit(
            Double.doubleToLongBits(
                candidate.node()
                    .angleOffsetDeg()),
            Double.doubleToLongBits(
                candidate.node()
                    .orbitalDepth01()),
            97L) * 0.05;
    }

    private static void addGeneratedCandidateAtPosition(List<GeneratedCandidate> candidates, CelestialObjectId beltId,
        AsteroidFieldProfile profile, int index, int depth, double x, double y, AsteroidFieldNode targetNode,
        List<ReachableAnchor> reachableAnchors) {
        double radius = Math.sqrt(x * x + y * y);
        if (radius < profile.innerOrbitalRadius() || radius > profile.outerOrbitalRadius()) return;

        AsteroidFieldNode candidate = AsteroidNodeMaterializer.resolveGeneratedNodeAtPosition(
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
        double targetDistance = AsteroidFieldOrbitResolver.separation(profile, candidate, targetNode);
        double normalizedTargetDistance = profile.placementConnectionRadius() <= 0.0 ? targetDistance
            : targetDistance / profile.placementConnectionRadius();
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
            double distance = AsteroidFieldOrbitResolver.separation(profile, candidate, anchor.node());
            if (distance <= profile.placementConnectionRadius()) {
                double proximity = profile.placementConnectionRadius() == 0.0 ? 1.0
                    : 1.0 - distance / profile.placementConnectionRadius();
                score += 1.0 + proximity;
            }
            if (angularSector(anchor.node()) == candidateSector) score += 0.5;
        }
        return score;
    }

    private static int angularSector(AsteroidFieldNode node) {
        return (int) Math.floor(node.angleOffsetDeg() / 45.0);
    }

}
