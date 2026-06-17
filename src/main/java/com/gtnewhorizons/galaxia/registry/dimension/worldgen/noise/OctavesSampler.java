package com.gtnewhorizons.galaxia.registry.dimension.worldgen.noise;

import java.util.Random;
import java.util.function.Supplier;

/// Layers several samplers on top of each other.
/// More octaves increase the CPU cost linearly, but increase the complexity and detail of the returned noise.
/// Each octave has an increasing scale (smaller features) and a decreasing amplitude (smaller effect).
public class OctavesSampler implements NoiseSampler {

    private final NoiseSampler[] octaves;
    private final double[] amplitudes, scales;

    public OctavesSampler(Supplier<NoiseSampler> samplers, int octaves) {
        this.octaves = new NoiseSampler[octaves];
        this.amplitudes = new double[octaves];
        this.scales = new double[octaves];

        for (int i = 0; i < octaves; i++) {
            this.octaves[i] = samplers.get();
            this.amplitudes[i] = 1d / Math.pow(2d, i);
            this.scales[i] = Math.pow(2d, i);
        }
    }

    public OctavesSampler(Random rng, int octaves) {
        this(() -> new SimplexNoiseSampler(rng), octaves);
    }

    @Override
    public double sample(double x, double y) {
        double value = 0;

        for (int i = 0, octavesLength = octaves.length; i < octavesLength; i++) {
            NoiseSampler sampler = octaves[i];
            double scale = scales[i];

            value += sampler.sample(x * scale, y * scale) * amplitudes[i];
        }

        return value;
    }

    @Override
    public double sample(double x, double y, double z) {
        double value = 0;

        for (int i = 0, octavesLength = octaves.length; i < octavesLength; i++) {
            NoiseSampler sampler = octaves[i];
            double scale = scales[i];

            value += sampler.sample(x * scale, y * scale, z * scale) * amplitudes[i];
        }

        return value;
    }
}
