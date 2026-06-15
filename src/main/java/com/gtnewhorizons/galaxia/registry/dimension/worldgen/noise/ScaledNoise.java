package com.gtnewhorizons.galaxia.registry.dimension.worldgen.noise;

public class ScaledNoise implements NoiseSampler {

    private final NoiseSampler base;
    private final double scaleX;
    private final double scaleY;
    private final double scaleZ;

    public ScaledNoise(NoiseSampler base, double scaleX, double scaleY, double scaleZ) {
        this.base = base;
        this.scaleX = scaleX;
        this.scaleY = scaleY;
        this.scaleZ = scaleZ;
    }

    public ScaledNoise(NoiseSampler base, double scale) {
        this(base, scale, scale, scale);
    }

    @Override
    public double sample(double x, double y) {
        return base.sample(x * scaleX, y * scaleY);
    }

    @Override
    public double sample(double x, double y, double z) {
        return base.sample(x * scaleX, y * scaleY, z * scaleZ);
    }
}
