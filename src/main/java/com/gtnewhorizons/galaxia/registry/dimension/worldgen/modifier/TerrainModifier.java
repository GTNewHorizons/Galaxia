package com.gtnewhorizons.galaxia.registry.dimension.worldgen.modifier;

/**
 * Holds all terrain modifiers alongside their range values
 */
public enum TerrainModifier {

    WEIRDNESS(-2, -1.5, -1, 0, 1, 1.5, 2);

    public final double minimum, lowerExtreme, lowerMiddle, middle, upperMiddle, upperExtreme, maximum;

    /**
     * Creates a terrain modifier with all needed parameters
     * @param minimum Lowest possible value
     * @param lowerExtreme Lower margin for extreme values
     * @param lowerMiddle Lower margin for regular values
     * @param middle Middle value
     * @param upperMiddle Upper margin for regular values
     * @param upperExtreme Upper margin for extreme values
     * @param maximum Highest possible value
     */
    TerrainModifier(double minimum, double lowerExtreme, double lowerMiddle, double middle, double upperMiddle,
        double upperExtreme, double maximum) {
        this.minimum = minimum;
        this.lowerExtreme = lowerExtreme;
        this.lowerMiddle = lowerMiddle;
        this.middle = middle;
        this.upperMiddle = upperMiddle;
        this.upperExtreme = upperExtreme;
        this.maximum = maximum;
    }
}
