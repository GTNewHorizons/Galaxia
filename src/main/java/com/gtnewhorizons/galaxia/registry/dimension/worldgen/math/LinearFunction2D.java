package com.gtnewhorizons.galaxia.registry.dimension.worldgen.math;

/**
 * Mutable linear function which returns a y-value for a given x-vale
 */
public class LinearFunction2D {

    private int yShift;
    private float inclination;

    /**
     *
     * @param yShift      y-shift applied to the whole function
     * @param inclination inclination of the linear function
     */
    public void setFunction(int yShift, float inclination) {
        this.yShift = yShift;
        this.inclination = inclination;
    }

    /**
     * Calculates the local y for a given x
     * 
     * @param x coordinate to get the corresponding y value for
     * @return y coordinate
     */
    public float getLocalY(int x) {
        return x * inclination + yShift;
    }
}
