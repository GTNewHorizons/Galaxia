package com.gtnewhorizons.galaxia.registry.dimension.worldgen.math;

public class LinearFunction3D {

    private final LinearFunction2D zFunction = new LinearFunction2D();
    private final LinearFunction2D xyFunction = new LinearFunction2D();
    private final LinearFunction2D zyFunction = new LinearFunction2D();

    public void setFunction(int zShift, int xyShift, int zyShift, float zInclination, float xyInclination,
        float zyInclination) {
        zFunction.setFunction(zShift, zInclination);
        xyFunction.setFunction(xyShift, xyInclination);
        zyFunction.setFunction(zyShift, zyInclination);
    }

    public float getDeviation(int x, int y, int z) {
        float functionY = xyFunction.getLocalY(x) + zyFunction.getLocalY(z);
        float yDeviation = Math.abs(y - functionY);
        float zDeviation = Math.abs(z - zFunction.getLocalY(x));
        return yDeviation * 2 + zDeviation;
    }
}
