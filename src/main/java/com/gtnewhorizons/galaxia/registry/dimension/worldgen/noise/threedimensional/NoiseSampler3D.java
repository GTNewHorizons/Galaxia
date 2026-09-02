package com.gtnewhorizons.galaxia.registry.dimension.worldgen.noise.threedimensional;

/**
 * Samples values from a 3D Perlin noise
 * <p>
 * Original code made by Daniel Fildán for Tropicraft [StationAPI]
 */
public class NoiseSampler3D {
    private final PerlinNoise3D sampler;

    /**
     * Simple constructor for using a standard Perlin noise
     * @param seed Seed for the noise
     * @param octaves Number of octaves the noise should have
     */
    public NoiseSampler3D(long seed, int octaves) {
        this(seed, octaves, 0.5D, 2.0D);
    }

    /**
     * Advanced constructor with more parameters to configure
     * @param seed Seed for the noise
     * @param octaves Number of octaves the noise should have
     * @param persistence Dominance of the lower octaves
     * @param lacunarity Frequency multiplier for each lower octave
     */
    public NoiseSampler3D(long seed, int octaves, double persistence, double lacunarity) {
        this.sampler = new PerlinNoise3D(seed, octaves, persistence, lacunarity);
    }

    /**
     * Samples the value of a single point
     * @param x x coordinate of the point
     * @param y y coordinate of the point
     * @param z z coordinate of the point
     * @param scaleX Scale for the x coordinate
     * @param scaleY Scale for the y coordinate
     * @param scaleZ Scale for the z coordinate
     * @return Sampled value
     */
    public double samplePoint(double x, double y, double z, double scaleX, double scaleY, double scaleZ) {
        return sampler.sample(x, y, z, scaleX, scaleY, scaleZ);
    }

    // TODO: Implement bulk sampling
    public double[] sample(double[] map, double x, double y, double z, int sizeX, int sizeY, int sizeZ, double scaleX, double scaleY, double scaleZ) {
        return new double[sizeX * sizeY * sizeZ];
    }
}
