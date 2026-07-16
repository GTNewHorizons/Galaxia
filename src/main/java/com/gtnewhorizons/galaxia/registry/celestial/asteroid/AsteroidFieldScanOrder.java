package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import java.util.Comparator;

/**
 * Shared comparators for asteroid scanning passes.
 *
 * Satellite scans use inner-to-outer order so a satellite parked on one asteroid
 * reveals nearby belt depth consistently instead of jumping around by slot id.
 */
public final class AsteroidFieldScanOrder {

    private AsteroidFieldScanOrder() {}

    public static Comparator<AsteroidFieldNode> byIndex() {
        return Comparator.comparingInt(AsteroidFieldNode::index);
    }

    public static Comparator<AsteroidFieldNode> innerToOuter() {
        return Comparator.comparingDouble(AsteroidFieldNode::orbitalDepth01)
            .thenComparingInt(AsteroidFieldNode::index);
    }

    public static Comparator<AsteroidFieldNode> discoveryOrder() {
        return Comparator.comparingInt(AsteroidFieldScanOrder::importance)
            .reversed()
            .thenComparingDouble(AsteroidFieldNode::orbitalDepth01)
            .thenComparingInt(AsteroidFieldNode::index);
    }

    private static int importance(AsteroidFieldNode node) {
        return switch (node.sizeClass()) {
            case LARGE -> 30;
            case MEDIUM -> 20;
            case SMALL -> 10;
        };
    }
}
