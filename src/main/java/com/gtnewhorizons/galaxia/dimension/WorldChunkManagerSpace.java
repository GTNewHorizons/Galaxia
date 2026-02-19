package com.gtnewhorizons.galaxia.dimension;

import net.minecraft.world.ChunkPosition;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.biome.WorldChunkManager;
import net.minecraft.world.gen.NoiseGeneratorOctaves;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class WorldChunkManagerSpace extends WorldChunkManager {
    private final List<BiomeGenBase> biomeGenerator = new ArrayList<>();
    private NoiseGeneratorOctaves biomeNoise;

    public void assignSeed(long seed) {
        if (biomeNoise != null) {
            return;
        }
        biomeNoise = new NoiseGeneratorOctaves(new Random(seed), 4);
    }

    public void provideBiomes(List<BiomeGenBase> biomes) {
        if (!biomeGenerator.isEmpty()) {
            return;
        }
        biomeGenerator.addAll(biomes);
    }

    /**
     * Returns the BiomeGenBase related to the x, z position on the world.
     */
    public BiomeGenBase getBiomeGenAt(int x, int z) {
        int biomeCount = biomeGenerator.size();
        double noise = biomeNoise.generateNoiseOctaves(new double[1], z, x, 1, 1, 0.025, 0.025, 0)[0];
        noise += 6;
        noise *= biomeCount;
        noise /= 12;
        int pickedBiome = (int) Math.floor(noise);
        if (pickedBiome >= biomeCount) {
            pickedBiome = biomeCount - 1;
        } else if (pickedBiome < 0) {
            pickedBiome = 0;
        }
        return this.biomeGenerator.get(pickedBiome);
    }

    /**
     * Returns biomes to use for the blocks and loads the other data like temperature and humidity onto the
     * WorldChunkManager Args: oldBiomeList, x, z, width, depth
     */
    public BiomeGenBase[] loadBlockGeneratorData(BiomeGenBase[] p_76933_1_, int p_76933_2_, int p_76933_3_, int p_76933_4_, int p_76933_5_) {
        if (p_76933_1_ == null || p_76933_1_.length < p_76933_4_ * p_76933_5_) {
            p_76933_1_ = new BiomeGenBase[p_76933_4_ * p_76933_5_];
        }

        Arrays.fill(p_76933_1_, 0, p_76933_4_ * p_76933_5_, this.biomeGenerator);
        return p_76933_1_;
    }

    /**
     * Return a list of biomes for the specified blocks. Args: listToReuse, x, y, width, length, cacheFlag (if false,
     * don't check biomeCache to avoid infinite loop in BiomeCacheBlock)
     */
    public BiomeGenBase[] getBiomeGenAt(BiomeGenBase[] p_76931_1_, int p_76931_2_, int p_76931_3_, int p_76931_4_, int p_76931_5_, boolean p_76931_6_) {
        return this.loadBlockGeneratorData(p_76931_1_, p_76931_2_, p_76931_3_, p_76931_4_, p_76931_5_);
    }

    public ChunkPosition findBiomePosition(int p_150795_1_, int p_150795_2_, int p_150795_3_, List<BiomeGenBase> p_150795_4_, Random p_150795_5_) {
        return doesListContainBiomes(p_150795_4_) ? new ChunkPosition(p_150795_1_ - p_150795_3_ + p_150795_5_.nextInt(p_150795_3_ * 2 + 1), 0, p_150795_2_ - p_150795_3_ + p_150795_5_.nextInt(p_150795_3_ * 2 + 1)) : null;
    }

    /**
     * checks given Chunk's Biomes against List of allowed ones
     */
    public boolean areBiomesViable(int p_76940_1_, int p_76940_2_, int p_76940_3_, List<BiomeGenBase> p_76940_4_) {
        return doesListContainBiomes(p_76940_4_);
    }

    private boolean doesListContainBiomes(List<BiomeGenBase> listToCheck) {
        for (BiomeGenBase biomeGenBase : biomeGenerator) {
            if (listToCheck.contains(biomeGenBase)) {
                return true;
            }
        }
        return false;
    }
}
