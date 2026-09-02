package com.gtnewhorizons.galaxia.registry.dimension.worldgen.math;

public class Smoothstep {

    /**
     * Applies smoothstep to a given value. Range must be between 0 and 1
     * 
     * @param value Value to apply smoothstep on
     * @return Smoothed value
     */
    public static double apply(double value) {
        return value * value * (3 - 2 * value);
    }
}
