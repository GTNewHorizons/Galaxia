package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import java.util.Comparator;

public final class AsteroidFieldScanOrder {

    private AsteroidFieldScanOrder() {}

    public static Comparator<AsteroidFieldNode> byIndex() {
        return Comparator.comparingInt(AsteroidFieldNode::index);
    }

    public static Comparator<AsteroidFieldNode> innerToOuter() {
        return Comparator.comparingDouble(AsteroidFieldNode::orbitalDepth01)
            .thenComparingInt(AsteroidFieldNode::index);
    }
}
