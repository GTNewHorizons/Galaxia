package com.gtnewhorizons.galaxia.registry.dimension.worldgen.noise;

/**
 * Original code made by Daniel Fildán for Tropicraft [StationAPI]
 */
public class NoiseSampler3D {
    private final PerlinNoise3D sampler;

    public NoiseSampler3D(long seed, int octaves) {
        this(seed, octaves, 0.5D, 2.0D);
    }

    public NoiseSampler3D(long seed, int octaves, double persistence, double lacunarity) {
        this.sampler = new PerlinNoise3D(seed, octaves, persistence, lacunarity);
    }

    public double samplePoint(double x, double y, double z, double scaleX, double scaleY, double scaleZ) {
        return sampler.sample(x, y, z, scaleX, scaleY, scaleZ);
    }

    public double[] sample(double[] map, double x, double y, double z, int sizeX, int sizeY, int sizeZ, double scaleX, double scaleY, double scaleZ) {
        return new double[sizeX * sizeY * sizeZ];
    }
}
