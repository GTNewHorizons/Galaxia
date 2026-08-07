package com.gtnewhorizons.galaxia.registry.dimension.worldgen;

public enum TerrainModifier {
    WEIRDNESS(-2, -1.5, -1, 0, 1, 1.5, 2);

    public final double
        minimum,
        lowerExtreme,
        lowerMiddle,
        middle,
        upperMiddle,
        upperExtreme,
        maximum;

    TerrainModifier(double minimum, double lowerExtreme, double lowerMiddle, double middle, double upperMiddle, double upperExtreme, double maximum) {
        this.minimum = minimum;
        this.lowerExtreme = lowerExtreme;
        this.lowerMiddle = lowerMiddle;
        this.middle = middle;
        this.upperMiddle = upperMiddle;
        this.upperExtreme = upperExtreme;
        this.maximum = maximum;
    }
}
