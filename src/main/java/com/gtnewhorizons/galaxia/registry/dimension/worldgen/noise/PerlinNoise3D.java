package com.gtnewhorizons.galaxia.registry.dimension.worldgen.noise;

import java.util.Random;

/**
 * Original code made by Daniel Fildán for Tropicraft [StationAPI]
 */
public class PerlinNoise3D {
    private final int[] p = new int[512];

    private final int octaves;
    private final double persistence;
    private final double lacunarity;

    public PerlinNoise3D(long seed, int octaves, double persistence, double lacunarity) {
        this.octaves = octaves;
        this.persistence = persistence;
        this.lacunarity = lacunarity;

        Random rand = new Random(seed);
        int[] permutation = new int[256];
        for (int i = 0; i < 256; i++) {
            permutation[i] = i;
        }

        for (int i = 0; i < 256; i++) {
            int swapIndex = rand.nextInt(256 - i) + i;
            int temp = permutation[i];
            permutation[i] = permutation[swapIndex];
            permutation[swapIndex] = temp;
        }

        for (int i = 0; i < 256; i++) {
            p[i] = permutation[i];
            p[i + 256] = permutation[i];
        }
    }

    public double sample(double x, double y, double z, double scaleX, double scaleY, double scaleZ) {
        return sample(x * scaleX, y * scaleY, z * scaleZ);
    }

    public double sample(double x, double y, double z) {
        double value = 0;
        double frequency = 1;
        double amplitude = 1;
        double maxValue = 0;

        for (int i = 0; i < octaves; i++) {
            value += noise(x * frequency, y * frequency, z * frequency) * amplitude;
            maxValue += amplitude;
            frequency *= lacunarity;
            amplitude *= persistence;
        }

        return value / maxValue;
    }

    private double noise(double x, double y, double z) {
        // Find unit cube that contains point
        int X = (int) Math.floor(x) & 255;
        int Y = (int) Math.floor(y) & 255;
        int Z = (int) Math.floor(z) & 255;

        // Find relative x, y, z of point in cube
        x -= Math.floor(x);
        y -= Math.floor(y);
        z -= Math.floor(z);

        // Compute fade curves for each coordinate
        double u = fade(x);
        double v = fade(y);
        double w = fade(z);

        // Hash coordinates of the 8 cube corners
        int A = p[X] + Y;
        int AA = p[A] + Z;
        int AB = p[A + 1] + Z;
        int B = p[X + 1] + Y;
        int BA = p[B] + Z;
        int BB = p[B + 1] + Z;

        // Blend the results from the 8 corners of the cube
        return lerp(w, lerp(v, lerp(u, grad(p[AA], x, y, z),
                    grad(p[BA], x - 1, y, z)),
                lerp(u, grad(p[AB], x, y - 1, z),
                    grad(p[BB], x - 1, y - 1, z))),
            lerp(v, lerp(u, grad(p[AA + 1], x, y, z - 1),
                    grad(p[BA + 1], x - 1, y, z - 1)),
                lerp(u, grad(p[AB + 1], x, y - 1, z - 1),
                    grad(p[BB + 1], x - 1, y - 1, z - 1))));
    }

    private double fade(double t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    private double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }

    private double grad(int hash, double x, double y, double z) {
        // Convert lower 4 bits of hash code into 12 gradient directions
        int h = hash & 15;
        double u = h < 8 ? x : y;
        double v = h < 4 ? y : h == 12 || h == 14 ? x : z;
        return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
    }
}
