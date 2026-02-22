package com.gtnewhorizons.galaxia.dimension;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

import net.minecraft.world.ChunkPosition;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.biome.WorldChunkManager;
import net.minecraft.world.gen.NoiseGeneratorOctaves;

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

    public void assignSeed(long seed) {
        if (xBiomeNoise != null) {
            return;
        }
        xBiomeNoise = new NoiseGeneratorOctaves(new Random(seed), 4);
        zBiomeNoise = new NoiseGeneratorOctaves(new Random(seed + 1), 4);
    }

    public void provideBiomes(BiomeGenBase[][] biomes) {
        if (biomeGenerator != null) {
            return;
        }
        biomeGenerator = biomes;
    }

    /**
     * Returns the BiomeGenBase related to the x, z position on the world.
     */
    public BiomeGenBase getBiomeGenAt(int x, int z) {
        if (cacheCreated && x == cacheX && z == cacheZ) {
            return biomeGenerator[cacheBiomeIndexX][cacheBiomeIndexZ];
        }
        int matrixLength = biomeGenerator[0].length;
        int xIndex = getBiomeIndex(x, z, matrixLength, xBiomeNoise, true);
        int zIndex = getBiomeIndex(x, z, matrixLength, zBiomeNoise, false);
        cacheCreated = true;
        cacheX = x;
        cacheZ = z;
        cacheBiomeIndexX = xIndex;
        cacheBiomeIndexZ = zIndex;
        return this.biomeGenerator[xIndex][zIndex];
    }

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

    public BiomeGenBase[] getAdjacentBiomes() {
        int adjacentIndexX = cacheBiomeIndexX + 1;
        int adjacentIndexZ = cacheBiomeIndexZ + 1;
        if (adjacentIndexX >= biomeGenerator.length) {
            adjacentIndexX = 0;
        }
        if (adjacentIndexZ >= biomeGenerator.length) {
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
        return matrixLength * matrixLength;
    }

    /**
     * Returns biomes to use for the blocks and loads the other data like temperature and humidity onto the
     * WorldChunkManager Args: oldBiomeList, x, z, width, depth
     */
    public BiomeGenBase[] loadBlockGeneratorData(BiomeGenBase[] p_76933_1_, int p_76933_2_, int p_76933_3_,
        int p_76933_4_, int p_76933_5_) {
        // TODO: This code is just a placeholder, I do not know what it is meant to do other than preventing a crash
        if (p_76933_1_ == null || p_76933_1_.length < p_76933_4_ * p_76933_5_) {
            p_76933_1_ = new BiomeGenBase[p_76933_4_ * p_76933_5_];
        }

        Arrays.fill(p_76933_1_, 0, p_76933_4_ * p_76933_5_, this.biomeGenerator[0][0]);
        return p_76933_1_;
    }

    /**
     * Return a list of biomes for the specified blocks. Args: listToReuse, x, y, width, length, cacheFlag (if false,
     * don't check biomeCache to avoid infinite loop in BiomeCacheBlock)
     */
    public BiomeGenBase[] getBiomeGenAt(BiomeGenBase[] p_76931_1_, int p_76931_2_, int p_76931_3_, int p_76931_4_,
        int p_76931_5_, boolean p_76931_6_) {
        // TODO: This code is just a placeholder, I do not know what it is meant to do other than preventing a crash
        return this.loadBlockGeneratorData(p_76931_1_, p_76931_2_, p_76931_3_, p_76931_4_, p_76931_5_);
    }

    public ChunkPosition findBiomePosition(int p_150795_1_, int p_150795_2_, int p_150795_3_,
        List<BiomeGenBase> p_150795_4_, Random p_150795_5_) {
        // TODO: This code is just a placeholder, I do not know what it is meant to do other than preventing a crash
        return doesListContainBiomes(p_150795_4_)
            ? new ChunkPosition(
                p_150795_1_ - p_150795_3_ + p_150795_5_.nextInt(p_150795_3_ * 2 + 1),
                0,
                p_150795_2_ - p_150795_3_ + p_150795_5_.nextInt(p_150795_3_ * 2 + 1))
            : null;
    }

    /**
     * checks given Chunk's Biomes against List of allowed ones
     */
    public boolean areBiomesViable(int p_76940_1_, int p_76940_2_, int p_76940_3_, List<BiomeGenBase> p_76940_4_) {
        // TODO: This code is just a placeholder, I do not know what it is meant to do other than preventing a crash
        return doesListContainBiomes(p_76940_4_);
    }

    private boolean doesListContainBiomes(List<BiomeGenBase> listToCheck) {
        for (int x = 0; x < biomeGenerator.length; x++) {
            for (int z = 0; z < biomeGenerator[x].length; z++) {
                if (listToCheck.contains(biomeGenerator[x][z])) {
                    return true;
                }
            }
        }
        return false;
    }

    public BiomeGenBase[] getBiomesForGeneration(BiomeGenBase[] p_76937_1_, int p_76937_2_, int p_76937_3_,
        int p_76937_4_, int p_76937_5_) {
        // TODO: This code is just a placeholder, I do not know what it is meant to do other than preventing a crash
        if (p_76937_1_ == null || p_76937_1_.length < p_76937_4_ * p_76937_5_) {
            p_76937_1_ = new BiomeGenBase[p_76937_4_ * p_76937_5_];
        }

        Arrays.fill(p_76937_1_, 0, p_76937_4_ * p_76937_5_, this.biomeGenerator[0][0]);
        return p_76937_1_;
    }
}
