package com.gtnewhorizons.galaxia.registry.dimension.worldgen.noise;

/// A sampler that remaps the 'boxy' normal distribution of a [SimplexSampler] to an approximately linear distribution.
public class NormalizedSampler implements NoiseSampler {

    private static final double REMAP_EXPONENT = 0.65;

    private static final int LUT_SIZE = 1024;

    private static final float[] REMAP_LOOKUP_TABLE;

    static {
        REMAP_LOOKUP_TABLE = buildLut(REMAP_EXPONENT);
    }

    private static float remap(double v) {
        v = Math.clamp(v, -1.0, 1.0);
        double t = (v + 1.0) * 0.5 * (LUT_SIZE - 1);
        int lo = (int) t;
        int hi = Math.min(lo + 1, LUT_SIZE - 1);
        float frac = (float) (t - lo);
        return REMAP_LOOKUP_TABLE[lo] + frac * (REMAP_LOOKUP_TABLE[hi] - REMAP_LOOKUP_TABLE[lo]);
    }

    private static float[] buildLut(double exponent) {
        float[] lut = new float[LUT_SIZE];
        for (int i = 0; i < LUT_SIZE; i++) {
            double v = i / (double) (LUT_SIZE - 1) * 2.0 - 1.0; // map i → [-1, 1]
            lut[i] = (float) (Math.signum(v) * Math.pow(Math.abs(v), exponent));
        }
        return lut;
    }

    private final NoiseSampler base;

    public NormalizedSampler(NoiseSampler base) {
        this.base = base;
    }

    @Override
    public double sample(double x, double y) {
        return remap(base.sample(x, y));
    }

    @Override
    public double sample(double x, double y, double z) {
        return remap(base.sample(x, y, z));
    }
}
