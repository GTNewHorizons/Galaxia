package com.gtnewhorizons.galaxia.registry.dimension.worldgen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.gen.NoiseGeneratorOctaves;

import com.gtnewhorizon.gtnhlib.hash.Fnv1a64;
import com.gtnewhorizon.gtnhlib.util.StdLCG;
import com.gtnewhorizon.gtnhlib.util.data.BlockMeta;
import com.gtnewhorizon.gtnhlib.util.data.ImmutableBlockMeta;
import com.gtnewhorizons.galaxia.registry.dimension.DimensionEnum;
import com.gtnewhorizons.galaxia.registry.dimension.biome.BiomeGenSpace;
import com.gtnewhorizons.galaxia.registry.dimension.provider.WorldChunkManagerSpace;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;

/// Handles biome detection, biome blending, height map calculations, and surface block detection.
/// This is the core of the terrain generator; it provides the rough shape.
/// Note that the height for a given column is the first air block, not the top-most block.
public final class HeightOracle {

    private static final int CHUNK_AREA = 256;
    private static final int MAX_CACHED_CHUNKS = 256;

    private static final int CHUNK_WIDTH = 16;
    public static final int HEIGHT_LIMIT = 256;
    private static final double ALLOWED_DIVERGENCE = 0.25;

    private final World world;
    private final WorldChunkManagerSpace wcm;
    private final DimensionEnum dimension;

    private final boolean clampHeight;

    private final Long2ObjectLinkedOpenHashMap<ChunkData> cache = new Long2ObjectLinkedOpenHashMap<>();

    private final Reference2IntOpenHashMap<BiomeGenBase> pooledBiomeIndices = new Reference2IntOpenHashMap<>();
    private final List<BiomeGenBase> pooledBiomeList = new ArrayList<>();

    private final BiomeGenBase[] pooledBlockBiomes = new BiomeGenBase[4];
    private final double[] pooledBlockContrib = new double[4];
    private final double[][] pooledBiomeContrib;

    private final StdLCG rand = new StdLCG();
    private final NoiseGeneratorOctaves terrainNoise;

    public HeightOracle(World world, DimensionEnum dimension, boolean clampHeight) {
        this.world = world;
        this.wcm = (WorldChunkManagerSpace) world.getWorldChunkManager();
        this.dimension = dimension;
        this.clampHeight = clampHeight;

        this.pooledBiomeContrib = new double[wcm.getBiomeCount()][CHUNK_AREA];
        this.terrainNoise = new NoiseGeneratorOctaves(new StdLCG(world.getSeed()), 4);
    }

    public static final class ChunkData {

        public final double[] heightmap = new double[CHUNK_AREA];
        public double heightMin, heightMax;
        public final ImmutableBlockMeta[] surfaceBlocks = new ImmutableBlockMeta[CHUNK_AREA];
        public final BiomeGenBase[] biomes = new BiomeGenBase[CHUNK_AREA];
    }

    public ChunkData getOrCompute(int cx, int cz) {
        long key = ((long) cx << 32) | (cz & 0xFFFFFFFFL);
        ChunkData data = cache.getAndMoveToLast(key);
        if (data != null) return data;

        data = new ChunkData();

        this.computeChunkData(cx, cz, data.heightmap, data.surfaceBlocks, data.biomes);

        data.heightMin = Double.MAX_VALUE;
        data.heightMax = Double.MIN_VALUE;

        for (double height : data.heightmap) {
            data.heightMin = Math.min(data.heightMin, height);
            data.heightMax = Math.max(data.heightMax, height);
        }

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

    private static final ImmutableBlockMeta STONE = new BlockMeta(Blocks.stone);
    private static final ImmutableBlockMeta AIR = new BlockMeta(Blocks.air);

    public ImmutableBlockMeta getPredictedBlock(int worldX, int worldY, int worldZ) {
        ChunkData data = getOrCompute(worldX >> 4, worldZ >> 4);
        int local = (worldX & 15) + ((worldZ & 15) << 4);
        int h = (int) data.heightmap[local];
        BiomeGenBase b = data.biomes[local];
        if (worldY < h) {
            if (worldY == h - 1) {
                ImmutableBlockMeta surf = data.surfaceBlocks[local];
                if (surf != null) return surf;

                if (b instanceof BiomeGenSpace bgs) {
                    return bgs.getTopBlock();
                }
                return STONE;
            }
            if (b instanceof BiomeGenSpace bgs) {
                return bgs.getFillerBlocks()
                    .getStrataBlock(worldY);
            }
            return STONE;
        }
        if (b instanceof BiomeGenSpace bgs) {
            int oceanHeight = bgs.getOceanHeight();
            if (worldY <= oceanHeight) return bgs.getOceanFiller();
        }
        return AIR;
    }

    private Random withSeed(int chunkX, int chunkZ, int index, int nonce) {
        long seed = Fnv1a64.initialState();
        seed = Fnv1a64.hashStep(seed, world.getSeed());
        seed = Fnv1a64.hashStep(seed, chunkX);
        seed = Fnv1a64.hashStep(seed, chunkZ);
        seed = Fnv1a64.hashStep(seed, index);
        seed = Fnv1a64.hashStep(seed, nonce);

        rand.setSeed(seed);
        return rand;
    }

    public void computeChunkData(int cx, int cz, double[] outHeightMap, ImmutableBlockMeta[] outSurfaceBlocks,
        BiomeGenBase[] outBiomes) {
        Arrays.fill(outHeightMap, 64.0);
        Arrays.fill(outSurfaceBlocks, null);
        Arrays.fill(outBiomes, null);

        Reference2IntOpenHashMap<BiomeGenBase> biomeIdxOf = this.pooledBiomeIndices;
        List<BiomeGenBase> biomeList = this.pooledBiomeList;

        biomeIdxOf.clear();
        biomeIdxOf.defaultReturnValue(-1);
        biomeList.clear();

        // [biome index][column index] -> biome contribution for column
        double[][] biomeContrib = this.pooledBiomeContrib;
        // adjacent biomes for the current column
        BiomeGenBase[] blockBiomes = this.pooledBlockBiomes;
        // the biome contributions for the current column
        double[] blockContrib = this.pooledBlockContrib;

        for (int x = 0; x < CHUNK_WIDTH; x++) {
            for (int z = 0; z < CHUNK_WIDTH; z++) {
                wcm.getLocalBiomes(cx * CHUNK_WIDTH + x, cz * CHUNK_WIDTH + z, blockBiomes);
                wcm.getLocalBiomeSignificance(ALLOWED_DIVERGENCE, blockContrib);

                double sum = 0;
                int contribSize = blockContrib.length;
                for (int i = 0; i < contribSize; i++) {
                    final double originalContrib = blockContrib[i];
                    final double squaredContrib = originalContrib * originalContrib;
                    blockContrib[i] = -1 * (squaredContrib * originalContrib * 2) + squaredContrib * 3;
                    sum += blockContrib[i];
                }

                double sumInv = 1d / sum;

                for (int i = 0; i < contribSize; i++) {
                    blockContrib[i] *= sumInv;
                }

                double maxContrib = 0;

                for (int i = 0; i < contribSize; i++) {
                    final BiomeGenBase biome = blockBiomes[i];

                    int biomeIndex = biomeIdxOf.getInt(biome);
                    if (biomeIndex < 0) {
                        biomeIndex = biomeList.size();
                        biomeList.add(biome);
                        biomeIdxOf.put(biome, biomeIndex);
                        Arrays.fill(biomeContrib[biomeIndex], 0);
                    }

                    if (blockContrib[i] > maxContrib) {
                        maxContrib = blockContrib[i];
                        outBiomes[x + (z << 4)] = biome;
                    }

                    biomeContrib[biomeIndex][x + (z << 4)] += blockContrib[i];
                }
            }
        }

        for (int biomeIndex = 0; biomeIndex < biomeList.size(); biomeIndex++) {
            BiomeGenBase currentBiome = biomeList.get(biomeIndex);

            if (!(currentBiome instanceof BiomeGenSpace spaceBiome)) {
                continue;
            }

            double[] terrainRelevance = biomeContrib[biomeIndex];

            TerrainConfiguration terrain = spaceBiome.getTerrain();

            int i = 0;

            for (TerrainFeature f : terrain.getMacroFeatures()) {
                TerrainFeatureApplier.applyToHeightmap(
                    f,
                    outHeightMap,
                    outSurfaceBlocks,
                    cx,
                    cz,
                    withSeed(cx, cz, i++, 5),
                    terrainRelevance,
                    dimension,
                    terrainNoise);
            }

            i = 0;

            for (TerrainFeature f : terrain.getMesoFeatures()) {
                TerrainFeatureApplier.applyToHeightmap(
                    f,
                    outHeightMap,
                    outSurfaceBlocks,
                    cx,
                    cz,
                    withSeed(cx, cz, i++, 10),
                    terrainRelevance,
                    dimension,
                    terrainNoise);
            }
        }

        if (clampHeight) {
            for (int i = 0; i < CHUNK_AREA; i++) {
                outHeightMap[i] = Math.clamp(outHeightMap[i], 1, HEIGHT_LIMIT);
            }
        }
    }
}
