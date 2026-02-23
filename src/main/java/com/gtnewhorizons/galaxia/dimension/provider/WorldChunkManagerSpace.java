package com.gtnewhorizons.galaxia.dimension.provider;

import java.util.Random;

import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.biome.WorldChunkManager;
import net.minecraft.world.gen.NoiseGeneratorOctaves;

/**
 * A specific implementation of the WorldChunkManager to be used on Galaxia planets
 */
public class WorldChunkManagerSpace extends WorldChunkManager {

    private BiomeGenBase[][] biomeGenerator;
    private NoiseGeneratorOctaves xBiomeNoise;
    private NoiseGeneratorOctaves zBiomeNoise;

    private boolean cacheCreated = false;
    private int cacheX = 0;
    private int cacheZ = 0;
    private int cacheBiomeIndexX = 0;
    private int cacheBiomeIndexZ = 0;
    private double cacheNoiseX = 0;
    private double cacheNoiseZ = 0;

    /**
     * Assigns the seed to generate specific noise outputs
     *
     * @param seed The seed with which to generate
     */
    public void assignSeed(long seed) {
        // Ignore if no required noise
        if (xBiomeNoise != null) {
            return;
        }
        xBiomeNoise = new NoiseGeneratorOctaves(new Random(seed), 4);
        zBiomeNoise = new NoiseGeneratorOctaves(new Random(seed + 1), 4);
    }

    /**
     * Provides the matrix of biomes to the manager
     *
     * @param biomes The matrix of biome gen bases to be used
     */
    public void provideBiomes(BiomeGenBase[][] biomes) {
        if (biomeGenerator != null) {
            return;
        }
        biomeGenerator = biomes;
    }

    /**
     * Returns the BiomeGenBase related to the given x, z coordinates in world
     *
     * @param x The checked x coordinate
     * @param z The checked z coordinate
     * @return The BiomeGenBase at that coordinate point on planet
     */
    public BiomeGenBase getBiomeGenAt(int x, int z) {
        if (cacheCreated && x == cacheX && z == cacheZ) {
            return biomeGenerator[cacheBiomeIndexX][cacheBiomeIndexZ];
        }
        int xIndex = getBiomeIndex(x, z, biomeGenerator.length, xBiomeNoise, true);
        int zIndex = getBiomeIndex(x, z, biomeGenerator[0].length, zBiomeNoise, false);
        cacheCreated = true;
        cacheX = x;
        cacheZ = z;
        cacheBiomeIndexX = xIndex;
        cacheBiomeIndexZ = zIndex;
        return this.biomeGenerator[xIndex][zIndex];
    }

    /**
     * Gets the index of the biome in the matrix given indices to check
     *
     * @param x              The x index of the matrix to check
     * @param z              The z index of the matrix to check
     * @param matrixLength   The size of the matrix (i.e. 3 for a 3x3)
     * @param noiseGenerator The noise generator used for biome distribution
     * @param firstIndex     Whether this biome is the first index or not
     * @return The index of the biome in the matrix
     */
    private int getBiomeIndex(int x, int z, int matrixLength, NoiseGeneratorOctaves noiseGenerator,
        boolean firstIndex) {
        double noise = noiseGenerator.generateNoiseOctaves(new double[1], z, x, 1, 1, 0.02, 0.02, 0)[0];
        noise += 6;
        noise *= matrixLength;
        noise /= 12;
        if (firstIndex) {
            cacheNoiseX = noise;
        } else {
            cacheNoiseZ = noise;
        }
        int pickedBiome = (int) Math.floor(noise);
        if (pickedBiome >= matrixLength) {
            double correctedNoise = noise - pickedBiome;
            pickedBiome = matrixLength - 1;
            correctedNoise += pickedBiome;
            if (firstIndex) {
                cacheNoiseX = correctedNoise;
            } else {
                cacheNoiseZ = correctedNoise;
            }
        } else if (pickedBiome < 0) {
            pickedBiome = 0;
        }
        return pickedBiome;
    }

    /**
     * Gets the adjacent biomes for use in smoothing methods
     *
     * @return An array of BiomeGenBases storing neighbouring biomes
     */
    public BiomeGenBase[] getAdjacentBiomes() {
        int adjacentIndexX = cacheBiomeIndexX + 1;
        int adjacentIndexZ = cacheBiomeIndexZ + 1;
        if (adjacentIndexX >= biomeGenerator.length) {
            adjacentIndexX = 0;
        }
        if (adjacentIndexZ >= biomeGenerator[0].length) {
            adjacentIndexZ = 0;
        }
        BiomeGenBase[] adjacentBiomes = new BiomeGenBase[3];
        adjacentBiomes[0] = biomeGenerator[adjacentIndexX][cacheBiomeIndexZ];
        adjacentBiomes[1] = biomeGenerator[cacheBiomeIndexX][adjacentIndexZ];
        adjacentBiomes[2] = biomeGenerator[adjacentIndexX][adjacentIndexZ];
        return adjacentBiomes;
    }

    public double[] getAdjacentBiomeSignificance() {
        double xDeviation = Math.max(0, cacheNoiseX - cacheBiomeIndexX - 0.95) * 20;
        double zDeviation = Math.max(0, cacheNoiseZ - cacheBiomeIndexZ - 0.95) * 20;
        double diagonalDeviation = Math.sqrt(xDeviation * xDeviation + zDeviation * zDeviation) / 1.25;
        return new double[] { xDeviation, zDeviation, diagonalDeviation };
    }

    public int getBiomeCount() {
        int matrixLength = biomeGenerator.length;
        int matrixWidth = biomeGenerator[0].length;
        return matrixLength * matrixWidth;
    }
}
