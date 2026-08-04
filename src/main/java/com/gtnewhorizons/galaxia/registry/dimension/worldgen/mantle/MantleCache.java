package com.gtnewhorizons.galaxia.registry.dimension.worldgen.mantle;

import com.gtnewhorizon.gtnhlib.hash.Fnv1a64;
import com.gtnewhorizon.gtnhlib.util.StdLCG;
import com.gtnewhorizon.gtnhlib.util.data.ImmutableBlockMeta;
import com.gtnewhorizons.galaxia.registry.dimension.DimensionEnum;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.TerrainConfiguration;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.TerrainFeature;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.TerrainFeatureApplier;
import net.minecraft.world.World;
import net.minecraft.world.gen.NoiseGeneratorOctaves;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

public class MantleCache {
    private static final int CACHE_LIMIT = 32;
    private static final int CHUNK_AREA = 256;
    private static final double[] DEFAULT_RELEVANCE = new double[CHUNK_AREA];

    private final World world;
    private final Random random;
    private final DimensionEnum dimension;
    private final NoiseGeneratorOctaves terrainNoise;

    private final List<CacheEntry> cacheEntries = new LinkedList<>();

    public MantleCache(World world, Random random, DimensionEnum dimension) {
        this.world = world;
        this.random = random;
        this.dimension = dimension;
        this.terrainNoise = new NoiseGeneratorOctaves(new StdLCG(world.getSeed()), 4);
        Arrays.fill(DEFAULT_RELEVANCE, 1);
    }

    public MantleCacheData getLocalData(int cubeX, int cubeZ, TerrainConfiguration ceiling, TerrainConfiguration floor) {
        for (CacheEntry entry : cacheEntries) {
            if (entry.isCorrectCache(cubeX, cubeZ)) {
                return entry.exportData;
            }
        }
        CacheEntry correctEntry = new CacheEntry(cubeX, cubeZ, ceiling, floor, world, random, dimension, terrainNoise);
        cacheEntries.add(correctEntry);
        while (cacheEntries.size() > CACHE_LIMIT) {
            cacheEntries.removeFirst();
        }
        return correctEntry.exportData;
    }

    public static final class CacheEntry {
        private final int cubeX;
        private final int cubeZ;
        private final World world;
        private final Random random;

        private final MantleCacheData exportData;

        public CacheEntry(int cubeX, int cubeZ, TerrainConfiguration ceiling, TerrainConfiguration floor, World world, Random random, DimensionEnum dimension, NoiseGeneratorOctaves terrainNoise) {
            this.cubeX = cubeX;
            this.cubeZ = cubeZ;
            this.world = world;
            this.random = random;

            int i = 0;
            double[] ceilingHeightmap = new double[CHUNK_AREA];
            ImmutableBlockMeta[] ceilingSurfaceBlocks = new ImmutableBlockMeta[CHUNK_AREA];
            for (TerrainFeature f : ceiling.getMacroFeatures()) {
                TerrainFeatureApplier.applyToHeightmap(
                    f,
                    ceilingHeightmap,
                    ceilingSurfaceBlocks,
                    cubeX,
                    cubeZ,
                    withSeed(cubeX, cubeZ, i++),
                    DEFAULT_RELEVANCE,
                    dimension,
                    terrainNoise);
            }
            i = 0;
            for (TerrainFeature f : ceiling.getMesoFeatures()) {
                TerrainFeatureApplier.applyToHeightmap(
                    f,
                    ceilingHeightmap,
                    ceilingSurfaceBlocks,
                    cubeX,
                    cubeZ,
                    withSeed(cubeX, cubeZ, i++),
                    DEFAULT_RELEVANCE,
                    dimension,
                    terrainNoise);
            }
            i = 0;
            double[] floorHeightmap = new double[CHUNK_AREA];
            ImmutableBlockMeta[] floorSurfaceBlocks = new ImmutableBlockMeta[CHUNK_AREA];
            for (TerrainFeature f : floor.getMacroFeatures()) {
                TerrainFeatureApplier.applyToHeightmap(
                    f,
                    floorHeightmap,
                    floorSurfaceBlocks,
                    cubeX,
                    cubeZ,
                    withSeed(cubeX, cubeZ, i++),
                    DEFAULT_RELEVANCE,
                    dimension,
                    terrainNoise);
            }
            i = 0;
            for (TerrainFeature f : floor.getMesoFeatures()) {
                TerrainFeatureApplier.applyToHeightmap(
                    f,
                    floorHeightmap,
                    floorSurfaceBlocks,
                    cubeX,
                    cubeZ,
                    withSeed(cubeX, cubeZ, i++),
                    DEFAULT_RELEVANCE,
                    dimension,
                    terrainNoise);
            }
            exportData = new MantleCacheData(ceilingHeightmap, floorHeightmap, ceilingSurfaceBlocks, floorSurfaceBlocks);
        }

        public boolean isCorrectCache(int cubeX, int cubeZ) {
            return this.cubeX == cubeX && this.cubeZ == cubeZ;
        }

        private Random withSeed(int chunkX, int chunkZ, int index) {
            long seed = Fnv1a64.initialState();
            seed = Fnv1a64.hashStep(seed, world.getSeed());
            seed = Fnv1a64.hashStep(seed, chunkX);
            seed = Fnv1a64.hashStep(seed, chunkZ);
            seed = Fnv1a64.hashStep(seed, index);

            random.setSeed(seed);
            return random;
        }
    }
}
