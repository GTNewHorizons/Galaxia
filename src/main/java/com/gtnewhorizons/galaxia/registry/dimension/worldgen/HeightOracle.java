package com.gtnewhorizons.galaxia.registry.dimension.worldgen;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.biome.BiomeGenBase;

import com.gtnewhorizons.galaxia.registry.dimension.biome.BiomeGenSpace;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;

public final class HeightOracle {

    private static final int CHUNK_AREA = 256;
    private static final int MAX_CACHED_CHUNKS = 256;

    private final ChunkProviderGalaxiaPlanet provider;
    private final Long2ObjectLinkedOpenHashMap<ChunkData> cache = new Long2ObjectLinkedOpenHashMap<>();

    public HeightOracle(ChunkProviderGalaxiaPlanet provider) {
        this.provider = provider;
    }

    private static final class ChunkData {

        final double[] heightmap = new double[CHUNK_AREA];
        final Block[] surfaceBlocks = new Block[CHUNK_AREA];
        final BiomeGenBase[] biomes = new BiomeGenBase[CHUNK_AREA];
    }

    private ChunkData getOrCompute(int cx, int cz) {
        long key = ((long) cx << 32) | (cz & 0xFFFFFFFFL);
        ChunkData data = cache.getAndMoveToLast(key);
        if (data != null) return data;
        data = new ChunkData();
        provider.computeChunkData(cx, cz, data.heightmap, data.surfaceBlocks, data.biomes);
        cache.putAndMoveToLast(key, data);
        while (cache.size() > MAX_CACHED_CHUNKS) {
            cache.removeFirst();
        }
        return data;
    }

    public int getColumnHeight(int worldX, int worldZ) {
        ChunkData data = getOrCompute(worldX >> 4, worldZ >> 4);
        return (int) data.heightmap[(worldX & 15) + ((worldZ & 15) << 4)];
    }

    public boolean isAir(int worldX, int worldY, int worldZ) {
        ChunkData data = getOrCompute(worldX >> 4, worldZ >> 4);
        int local = (worldX & 15) + ((worldZ & 15) << 4);
        int h = (int) data.heightmap[local];
        if (worldY < h) return false;
        BiomeGenBase b = data.biomes[local];
        if (b instanceof BiomeGenSpace bgs) {
            int oceanHeight = bgs.getOceanHeight();
            if (worldY <= oceanHeight) return false;
        }
        return true;
    }

    public Block getPredictedBlock(int worldX, int worldY, int worldZ) {
        ChunkData data = getOrCompute(worldX >> 4, worldZ >> 4);
        int local = (worldX & 15) + ((worldZ & 15) << 4);
        int h = (int) data.heightmap[local];
        BiomeGenBase b = data.biomes[local];
        if (worldY < h) {
            if (worldY == h - 1) {
                Block surf = data.surfaceBlocks[local];
                if (surf != null) return surf;
                if (b instanceof BiomeGenSpace bgs && !bgs.getTopBlockMetas()
                    .isEmpty()) {
                    return bgs.getTopBlockMetas()
                        .getFirst();
                }
                return Blocks.stone;
            }
            if (b instanceof BiomeGenSpace bgs) {
                return bgs.getFillerBlocks()
                    .getStrataBlock(worldY);
            }
            return Blocks.stone;
        }
        if (b instanceof BiomeGenSpace bgs) {
            int oceanHeight = bgs.getOceanHeight();
            if (worldY <= oceanHeight) return bgs.getOceanFiller();
        }
        return Blocks.air;
    }
}
