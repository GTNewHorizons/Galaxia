package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeFacts.DiscoveryState;

/** Owns the short-lived geometry build for one immutable resolved asteroid field. */
final class AsteroidPlacementGraph {

    private static final int CANDIDATES_PER_NODE = 64;
    private static final int CELL_KEY_ROTATION = 31;

    private static final double FULL_ROTATION_RADIANS = Math.PI * 2.0;
    private static final double ZERO_CONNECTION_RADIUS = 0.0;
    private static final double DENSITY_CONTRAST = 0.9;
    private static final double PRIMARY_DENSITY_WEIGHT = 0.5;
    private static final double SECONDARY_DENSITY_WEIGHT = 0.3;
    private static final double TERTIARY_DENSITY_WEIGHT = 0.2;
    private static final double SECONDARY_ANGULAR_FREQUENCY = 2.0;
    private static final double TERTIARY_ANGULAR_FREQUENCY = 4.0;
    private static final double RADIAL_CENTER = 0.5;
    private static final double SECONDARY_RADIAL_SCALE = 0.5;
    private static final double TERTIARY_RADIAL_SCALE = 1.0;
    private static final double EMPTY_FIELD_CLEARANCE_FACTOR = 2.0;
    private static final double CLEARANCE_SATURATION = 1.0;
    private static final double MINIMUM_POINT_SEPARATION = 0.000_001;
    private static final double MINIMUM_SELECTION_WEIGHT = 0.000_001;
    private static final double MINIMUM_CONNECTION_FRACTION = 0.25;
    private static final double CONNECTION_FRACTION_SPAN = 0.70;

    private static final long PRIMARY_DENSITY_PHASE_SALT = 101L;
    private static final long SECONDARY_DENSITY_PHASE_SALT = 103L;
    private static final long TERTIARY_DENSITY_PHASE_SALT = 107L;
    private static final long ROOT_ANGLE_SALT = 211L;
    private static final long ROOT_DEPTH_SALT = 223L;
    private static final long CONNECTION_ANCHOR_SALT = 307L;
    private static final long CONNECTION_DISTANCE_SALT = 311L;
    private static final long CONNECTION_ANGLE_SALT = 313L;
    private static final long CANDIDATE_SELECTION_SALT = 317L;
    private static final long ROOT_ASSIGNMENT_SALT = 331L;
    private static final long CELL_X_SALT = 0x9E3779B97F4A7C15L;
    private static final long CELL_Y_SALT = 0xC2B2AE3D27D4EB4FL;

    private record PlacementPoint(double angleOffsetDeg, double orbitalDepth01, double x, double y) {}

    private static final class CandidateSelection {

        private final PlacementContext placement;
        private final AsteroidFieldDeterminism nodeSeed;
        private PlacementPoint selectedPoint;
        private double selectedPriority = Double.POSITIVE_INFINITY;

        private CandidateSelection(PlacementContext placement, AsteroidFieldDeterminism nodeSeed) {
            this.placement = placement;
            this.nodeSeed = nodeSeed;
        }

        private void consider(PlacementPoint candidate, int attempt) {
            if (candidate == null) return;
            double clearance = placement.nearestDistance(candidate);
            if (clearance <= MINIMUM_POINT_SEPARATION) return;

            double priority = weightedPriority(candidate, clearance, attempt);
            if (selectedPoint == null || Double.compare(priority, selectedPriority) < 0) {
                selectedPoint = candidate;
                selectedPriority = priority;
            }
        }

        private double weightedPriority(PlacementPoint candidate, double clearance, int attempt) {
            double selectionWeight = placement.selectionWeight(candidate, clearance);
            double draw = Math.max(MINIMUM_POINT_SEPARATION, nodeSeed.unit(CANDIDATE_SELECTION_SALT, attempt));
            return -Math.log(draw) / selectionWeight;
        }

        private PlacementPoint requirePoint(int index) {
            if (selectedPoint == null) {
                throw new IllegalStateException("no valid connected placement candidate for asteroid " + index);
            }
            return selectedPoint;
        }
    }

    /** Geometry, density, and indexes discarded after one field build. */
    private static final class PlacementContext {

        private final AsteroidFieldProfile profile;
        private final AsteroidFieldDeterminism fieldSeed;
        private final double baseSpacing;
        private final double emptyFieldClearance;
        private final double growthRadius;
        private final double cellSize;
        private final List<List<PlacementPoint>> anchorsByRoot = new ArrayList<>();
        private final Map<Long, List<PlacementPoint>> pointsByCell = new HashMap<>();
        private long minimumCellX = Long.MAX_VALUE;
        private long maximumCellX = Long.MIN_VALUE;
        private long minimumCellY = Long.MAX_VALUE;
        private long maximumCellY = Long.MIN_VALUE;

        private PlacementContext(CelestialObjectId beltId, AsteroidFieldProfile profile) {
            this.profile = profile;
            fieldSeed = AsteroidFieldDeterminism.forField(beltId, profile);
            double annulusArea = Math.PI * (profile.outerOrbitalRadius() * profile.outerOrbitalRadius()
                - profile.innerOrbitalRadius() * profile.innerOrbitalRadius());
            int pointCount = profile.totalNodes() + profile.authoredAsteroids()
                .size();
            baseSpacing = Math.sqrt(annulusArea / Math.max(1, pointCount));
            emptyFieldClearance = baseSpacing * EMPTY_FIELD_CLEARANCE_FACTOR;
            growthRadius = Math.min(baseSpacing, profile.placementConnectionRadius());
            cellSize = Math.max(baseSpacing, profile.placementConnectionRadius());
        }

        private void addRootNode(AsteroidFieldNode node) {
            List<PlacementPoint> rootAnchors = new ArrayList<>();
            rootAnchors.add(addPoint(node));
            anchorsByRoot.add(rootAnchors);
        }

        private void addUnownedNode(AsteroidFieldNode node) {
            addPoint(node);
        }

        private void addNodeToRoot(AsteroidFieldNode node, int rootIndex) {
            anchorsByRoot.get(rootIndex)
                .add(addPoint(node));
        }

        private PlacementPoint addPoint(AsteroidFieldNode node) {
            PlacementPoint point = point(profile, node);
            long cellX = cellCoordinate(point.x());
            long cellY = cellCoordinate(point.y());
            pointsByCell.computeIfAbsent(cellKey(cellX, cellY), ignored -> new ArrayList<>())
                .add(point);
            minimumCellX = Math.min(minimumCellX, cellX);
            maximumCellX = Math.max(maximumCellX, cellX);
            minimumCellY = Math.min(minimumCellY, cellY);
            maximumCellY = Math.max(maximumCellY, cellY);
            return point;
        }

        private int selectRootIndex(AsteroidFieldDeterminism nodeSeed) {
            if (anchorsByRoot.isEmpty()) {
                throw new IllegalStateException("Asteroid field has hidden generated asteroids but no visible anchor");
            }
            int rootCount = anchorsByRoot.size();
            return Math.min(rootCount - 1, (int) (nodeSeed.unit(ROOT_ASSIGNMENT_SALT) * rootCount));
        }

        private PlacementPoint rootAnchor(int rootIndex, int anchorIndex) {
            return anchorsByRoot.get(rootIndex)
                .get(anchorIndex);
        }

        private int rootAnchorCount(int rootIndex) {
            return anchorsByRoot.get(rootIndex)
                .size();
        }

        private double growthRadius() {
            return growthRadius;
        }

        private double desiredSpacing(PlacementPoint point) {
            return baseSpacing / Math.sqrt(densityAt(point));
        }

        private double selectionWeight(PlacementPoint point, double spatialClearance) {
            double desiredSpacing = desiredSpacing(point);
            double spatialSuitability = saturatedClearance(spatialClearance, desiredSpacing);
            double spatialWeight = spatialSuitability * spatialSuitability * spatialSuitability * spatialSuitability;
            return MINIMUM_SELECTION_WEIGHT + densityAt(point) * spatialWeight;
        }

        private double saturatedClearance(double clearance, double desiredSpacing) {
            return Math.min(CLEARANCE_SATURATION, clearance / desiredSpacing);
        }

        private double densityAt(PlacementPoint point) {
            double angle = Math.toRadians(point.angleOffsetDeg());
            double radialPhase = (point.orbitalDepth01() - RADIAL_CENTER) * Math.PI;
            double signal = PRIMARY_DENSITY_WEIGHT * Math.sin(angle + phase(PRIMARY_DENSITY_PHASE_SALT))
                + SECONDARY_DENSITY_WEIGHT * Math.sin(
                    SECONDARY_ANGULAR_FREQUENCY * angle + phase(SECONDARY_DENSITY_PHASE_SALT)
                        + SECONDARY_RADIAL_SCALE * radialPhase)
                + TERTIARY_DENSITY_WEIGHT * Math.sin(
                    TERTIARY_ANGULAR_FREQUENCY * angle + phase(TERTIARY_DENSITY_PHASE_SALT)
                        - TERTIARY_RADIAL_SCALE * radialPhase);
            return Math.exp(DENSITY_CONTRAST * signal);
        }

        private double phase(long salt) {
            return fieldSeed.unit(salt) * FULL_ROTATION_RADIANS;
        }

        private double nearestDistance(PlacementPoint candidate) {
            if (pointsByCell.isEmpty()) return emptyFieldClearance;

            long candidateCellX = cellCoordinate(candidate.x());
            long candidateCellY = cellCoordinate(candidate.y());
            double nearestSquared = Double.POSITIVE_INFINITY;
            for (long ring = 0;; ring++) {
                nearestSquared = scanCellRing(candidate, candidateCellX, candidateCellY, ring, nearestSquared);
                if (coversAllOccupiedCells(candidateCellX, candidateCellY, ring) || nearestCannotImproveOutsideRing(
                    candidate,
                    candidateCellX,
                    candidateCellY,
                    ring,
                    nearestSquared)) {
                    return Math.sqrt(nearestSquared);
                }
            }
        }

        private double scanCellRing(PlacementPoint candidate, long centerCellX, long centerCellY, long ring,
            double nearestSquared) {
            long minimumX = centerCellX - ring;
            long maximumX = centerCellX + ring;
            long minimumY = centerCellY - ring;
            long maximumY = centerCellY + ring;
            for (long cellX = minimumX; cellX <= maximumX; cellX++) {
                nearestSquared = nearestSquaredInCell(candidate, cellX, minimumY, nearestSquared);
                if (ring > 0) nearestSquared = nearestSquaredInCell(candidate, cellX, maximumY, nearestSquared);
            }
            for (long cellY = minimumY + 1; cellY < maximumY; cellY++) {
                nearestSquared = nearestSquaredInCell(candidate, minimumX, cellY, nearestSquared);
                if (ring > 0) nearestSquared = nearestSquaredInCell(candidate, maximumX, cellY, nearestSquared);
            }
            return nearestSquared;
        }

        private double nearestSquaredInCell(PlacementPoint candidate, long cellX, long cellY, double nearestSquared) {
            List<PlacementPoint> points = pointsByCell.get(cellKey(cellX, cellY));
            if (points == null) return nearestSquared;
            for (PlacementPoint point : points) {
                nearestSquared = Math.min(nearestSquared, separationSquared(candidate, point));
            }
            return nearestSquared;
        }

        private boolean coversAllOccupiedCells(long centerCellX, long centerCellY, long ring) {
            return centerCellX - ring <= minimumCellX && centerCellX + ring >= maximumCellX
                && centerCellY - ring <= minimumCellY
                && centerCellY + ring >= maximumCellY;
        }

        private boolean nearestCannotImproveOutsideRing(PlacementPoint candidate, long centerCellX, long centerCellY,
            long ring, double nearestSquared) {
            if (!Double.isFinite(nearestSquared)) return false;
            double minimumX = (centerCellX - ring) * cellSize;
            double maximumX = (centerCellX + ring + 1) * cellSize;
            double minimumY = (centerCellY - ring) * cellSize;
            double maximumY = (centerCellY + ring + 1) * cellSize;
            double distanceToUnscannedCells = Math.min(
                Math.min(candidate.x() - minimumX, maximumX - candidate.x()),
                Math.min(candidate.y() - minimumY, maximumY - candidate.y()));
            return nearestSquared <= distanceToUnscannedCells * distanceToUnscannedCells;
        }

        private long cellCoordinate(double coordinate) {
            return (long) Math.floor(coordinate / cellSize);
        }

        /** Hash collisions add harmless extra distance checks. */
        private static long cellKey(long cellX, long cellY) {
            return cellX * CELL_X_SALT ^ Long.rotateLeft(cellY * CELL_Y_SALT, CELL_KEY_ROTATION);
        }
    }

    private AsteroidPlacementGraph() {}

    static List<AsteroidFieldNode> resolveAll(CelestialObjectId beltId, AsteroidFieldProfile profile) {
        List<AsteroidFieldNode> nodes = new ArrayList<>(
            profile.totalNodes() + profile.authoredAsteroids()
                .size());
        PlacementContext placement = new PlacementContext(beltId, profile);

        for (AuthoredAsteroidDefinition definition : profile.authoredAsteroids()) {
            AsteroidFieldNode node = AsteroidNodeMaterializer.resolveAuthoredNode(beltId, profile, definition);
            nodes.add(node);
            if (node.initialDetectionState() == DiscoveryState.DISCOVERED) {
                placement.addRootNode(node);
            } else {
                placement.addUnownedNode(node);
            }
        }

        for (int ordinal = 0; ordinal < profile.totalNodes(); ordinal++) {
            int index = AsteroidSlotRanges.generatedSlot(ordinal);
            if (AsteroidGeneratedSlotAllocator.generatedSizeClass(profile, index) != AsteroidSizeClass.LARGE) continue;
            AsteroidFieldNode node = resolveGeneratedRoot(beltId, profile, index, placement);
            nodes.add(node);
            placement.addRootNode(node);
        }

        for (int ordinal = 0; ordinal < profile.totalNodes(); ordinal++) {
            int index = AsteroidSlotRanges.generatedSlot(ordinal);
            if (AsteroidGeneratedSlotAllocator.generatedSizeClass(profile, index) == AsteroidSizeClass.LARGE) continue;
            AsteroidFieldDeterminism nodeSeed = AsteroidFieldDeterminism.forNode(beltId, profile, index);
            int rootIndex = placement.selectRootIndex(nodeSeed);
            AsteroidFieldNode node = resolveReachableGeneratedNode(
                beltId,
                profile,
                index,
                placement,
                nodeSeed,
                rootIndex);
            nodes.add(node);
            placement.addNodeToRoot(node, rootIndex);
        }

        nodes.sort((first, second) -> Integer.compare(first.index(), second.index()));
        validateReachability(profile, nodes);
        return List.copyOf(nodes);
    }

    private static AsteroidFieldNode resolveGeneratedRoot(CelestialObjectId beltId, AsteroidFieldProfile profile,
        int index, PlacementContext placement) {
        AsteroidFieldDeterminism nodeSeed = AsteroidFieldDeterminism.forNode(beltId, profile, index);
        CandidateSelection candidates = new CandidateSelection(placement, nodeSeed);
        for (int attempt = 0; attempt < CANDIDATES_PER_NODE; attempt++) {
            candidates.consider(
                point(profile, nodeSeed.degrees(ROOT_ANGLE_SALT, attempt), nodeSeed.unit(ROOT_DEPTH_SALT, attempt)),
                attempt);
        }
        return materializeGeneratedNode(beltId, profile, index, candidates.requirePoint(index));
    }

    private static AsteroidFieldNode resolveReachableGeneratedNode(CelestialObjectId beltId,
        AsteroidFieldProfile profile, int index, PlacementContext placement, AsteroidFieldDeterminism nodeSeed,
        int rootIndex) {
        if (profile.placementConnectionRadius() <= ZERO_CONNECTION_RADIUS) {
            throw new IllegalStateException(
                "Asteroid field cannot place hidden asteroids with a zero connection radius");
        }

        CandidateSelection candidates = new CandidateSelection(placement, nodeSeed);
        int anchorCount = placement.rootAnchorCount(rootIndex);
        for (int attempt = 0; attempt < CANDIDATES_PER_NODE; attempt++) {
            int anchorIndex = Math
                .min(anchorCount - 1, (int) (nodeSeed.unit(CONNECTION_ANCHOR_SALT, attempt) * anchorCount));
            PlacementPoint anchor = placement.rootAnchor(rootIndex, anchorIndex);
            double distance = placement.growthRadius() * (MINIMUM_CONNECTION_FRACTION
                + nodeSeed.unit(CONNECTION_DISTANCE_SALT, attempt) * CONNECTION_FRACTION_SPAN);
            double angle = nodeSeed.unit(CONNECTION_ANGLE_SALT, attempt) * FULL_ROTATION_RADIANS;
            candidates.consider(
                pointAtCartesian(
                    profile,
                    anchor.x() + Math.cos(angle) * distance,
                    anchor.y() + Math.sin(angle) * distance),
                attempt);
        }
        return materializeGeneratedNode(beltId, profile, index, candidates.requirePoint(index));
    }

    private static AsteroidFieldNode materializeGeneratedNode(CelestialObjectId beltId, AsteroidFieldProfile profile,
        int index, PlacementPoint point) {
        return AsteroidNodeMaterializer
            .resolveGeneratedNodeAtPosition(beltId, profile, index, point.angleOffsetDeg(), point.orbitalDepth01());
    }

    static void validateReachability(AsteroidFieldProfile profile, List<AsteroidFieldNode> nodes) {
        Set<MinorCelestialBodyId> visited = reachableNodeIds(profile, nodes);
        for (AsteroidFieldNode node : nodes) {
            if (node.initialDetectionState() == DiscoveryState.HIDDEN && !visited.contains(node.id())) {
                throw new IllegalStateException("unreachable hidden asteroid in scan graph: " + node.id());
            }
        }
    }

    private static Set<MinorCelestialBodyId> reachableNodeIds(AsteroidFieldProfile profile,
        List<AsteroidFieldNode> nodes) {
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
        return visited;
    }

    private static PlacementPoint point(AsteroidFieldProfile profile, AsteroidFieldNode node) {
        return point(profile, node.angleOffsetDeg(), node.orbitalDepth01());
    }

    private static PlacementPoint point(AsteroidFieldProfile profile, double angleOffsetDeg, double orbitalDepth01) {
        double radius = profile.innerOrbitalRadius()
            + (profile.outerOrbitalRadius() - profile.innerOrbitalRadius()) * orbitalDepth01;
        double angle = Math.toRadians(angleOffsetDeg);
        return new PlacementPoint(angleOffsetDeg, orbitalDepth01, Math.cos(angle) * radius, Math.sin(angle) * radius);
    }

    private static PlacementPoint pointAtCartesian(AsteroidFieldProfile profile, double x, double y) {
        double radius = Math.sqrt(x * x + y * y);
        if (radius < profile.innerOrbitalRadius() || radius > profile.outerOrbitalRadius()) return null;

        double angleOffsetDeg = AsteroidFieldDeterminism.normalizeDegrees(Math.toDegrees(Math.atan2(y, x)));
        double orbitalDepth01 = (radius - profile.innerOrbitalRadius())
            / (profile.outerOrbitalRadius() - profile.innerOrbitalRadius());
        return new PlacementPoint(angleOffsetDeg, orbitalDepth01, x, y);
    }

    private static double separationSquared(PlacementPoint first, PlacementPoint second) {
        double dx = first.x() - second.x();
        double dy = first.y() - second.y();
        return dx * dx + dy * dy;
    }
}
