package com.gtnewhorizons.galaxia.registry.dimension.provider;

import java.util.Random;

import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.biome.WorldChunkManager;
import net.minecraft.world.gen.NoiseGeneratorOctaves;

/**
 * A specific implementation of the WorldChunkManager to be used on Galaxia planets
 */
public class WorldChunkManagerSpace extends WorldChunkManager {

    private BiomeGenBase[][] biomeGeneratorMatrix;
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
        if (biomeGeneratorMatrix != null) {
            return;
        }
        biomeGeneratorMatrix = biomes;
    }

    /**
     * Returns the BiomeGenBase related to the given x, z coordinates in world
     *
     * @param x The checked x coordinate
     * @param z The checked z coordinate
     * @return The BiomeGenBase at that coordinate point on planet
     */
    public BiomeGenBase getBiomeGenAt(int x, int z) {
        if (!(cacheCreated && x == cacheX && z == cacheZ)) {
            cacheBiomeIndexX = getBiomeIndex(x, z, biomeGeneratorMatrix.length, xBiomeNoise, true);
            cacheBiomeIndexZ = getBiomeIndex(x, z, biomeGeneratorMatrix[0].length, zBiomeNoise, false);
            cacheX = x;
            cacheZ = z;
            cacheCreated = true;
        }
        return biomeGeneratorMatrix[cacheBiomeIndexX][cacheBiomeIndexZ];
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
        double noise = noiseGenerator.generateNoiseOctaves(null, z, x, 1, 1, 0.02, 0.02, 0)[0];
        // normalize
        noise = (noise + 8) / 16;
        noise *= matrixLength;
        if (firstIndex) {
            cacheNoiseX = noise;
        } else {
            cacheNoiseZ = noise;
        }
        return (int) Math.floor(noise);
    }

    /**
     * Gets all contributing biomes for use in smoothing methods
     *
     * @return An array of BiomeGenBases storing neighbouring biomes
     */
    public BiomeGenBase[] getLocalBiomes(int x, int z) {
        BiomeGenBase[] localBiomes = new BiomeGenBase[4];
        localBiomes[0] = this.getBiomeGenAt(x, z);
        int adjacentIndexX = cacheBiomeIndexX + 1 >= biomeGeneratorMatrix.length ? 0 : cacheBiomeIndexX + 1;
        int adjacentIndexZ = cacheBiomeIndexZ + 1 >= biomeGeneratorMatrix[0].length ? 0 : cacheBiomeIndexZ + 1;
        localBiomes[1] = biomeGeneratorMatrix[adjacentIndexX][cacheBiomeIndexZ];
        localBiomes[2] = biomeGeneratorMatrix[cacheBiomeIndexX][adjacentIndexZ];
        localBiomes[3] = biomeGeneratorMatrix[adjacentIndexX][adjacentIndexZ];
        return localBiomes;
    }

    public double[] getLocalBiomeSignificance(double divergence) {
        if (divergence == 0) return new double[] { 1, 0, 0, 0 };
        double d1 = Math.max(0, cacheNoiseX - cacheBiomeIndexX - 1 + divergence) / divergence;
        double d2 = Math.max(0, cacheNoiseZ - cacheBiomeIndexZ - 1 + divergence) / divergence;
        // four ways normalized symmetric blending in the corner
        return new double[] { d1 * d2, (1 - d1) * d2, d1 * (1 - d2), (1 - d1) * (1 - d2) };
    }

    public int getBiomeCount() {
        int matrixLength = biomeGeneratorMatrix.length;
        int matrixWidth = biomeGeneratorMatrix[0].length;
        return matrixLength * matrixWidth;
    }
}
