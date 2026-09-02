package com.gtnewhorizons.galaxia.registry.dimension.worldgen.mantle;

import com.gtnewhorizon.gtnhlib.hash.Fnv1a64;
import com.gtnewhorizon.gtnhlib.util.StdLCG;
import com.gtnewhorizon.gtnhlib.util.data.ImmutableBlockMeta;
import com.gtnewhorizons.galaxia.registry.dimension.DimensionEnum;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.TerrainConfiguration;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.TerrainFeature;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.TerrainFeatureApplier;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.modifier.ModifierHandler;
import net.minecraft.world.World;
import net.minecraft.world.gen.NoiseGeneratorOctaves;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

/**
 * Caches mantle terrain generation data for 32 columns at a time
 */
public class MantleCache {

    private static final int CACHE_LIMIT = 32;
    private static final int CHUNK_AREA = 256;
    private static final double[] DEFAULT_RELEVANCE = new double[CHUNK_AREA];

    private final World world;
    private final Random random;
    private final DimensionEnum dimension;
    private final NoiseGeneratorOctaves terrainNoise;
    private final ModifierHandler modifierHandler;

    private final List<CacheEntry> cacheEntries = new LinkedList<>();

    /**
     * Creates a mantle cache with all relevant parameters
     * @param world World in which the mantle generates
     * @param random Randomizer for terrain generation
     * @param dimension Dimension the cache is used for
     * @param modifierHandler Handler for terrain modifiers
     */
    public MantleCache(World world, Random random, DimensionEnum dimension, ModifierHandler modifierHandler) {
        this.world = world;
        this.random = random;
        this.dimension = dimension;
        this.terrainNoise = new NoiseGeneratorOctaves(new StdLCG(world.getSeed()), 4);
        this.modifierHandler = modifierHandler;
        Arrays.fill(DEFAULT_RELEVANCE, 1);
    }

    /**
     * Provides data for a specific column. Creates a new cache entry if needed
     * @param columnX x coordinate of the currently generating column
     * @param columnZ z coordinate of the currently generating column
     * @param ceiling Terrain configuration for the ceiling
     * @param floor Terrain configuration for the floor
     * @return Record with all important data
     */
    public MantleCacheData getLocalData(int columnX, int columnZ, TerrainConfiguration ceiling,
        TerrainConfiguration floor) {
        for (CacheEntry entry : cacheEntries) {
            if (entry.isCorrectCache(columnX, columnZ)) {
                return entry.exportData;
            }
        }
        CacheEntry correctEntry = new CacheEntry(columnX, columnZ, ceiling, floor, world, random, dimension, terrainNoise, modifierHandler);
        cacheEntries.add(correctEntry);
        while (cacheEntries.size() > CACHE_LIMIT) {
            cacheEntries.removeFirst();
        }
        return correctEntry.exportData;
    }

    /**
     * Cache entry for the mantle cache
     */
    public static final class CacheEntry {

        private final int columnX;
        private final int columnZ;
        private final World world;
        private final Random random;

        private final MantleCacheData exportData;

        /**
         * Creates a cache entry and calculates all the relevant data
         * @param columnX x coordinate of the currently generating column
         * @param columnZ z coordinate of the currently generating column
         * @param ceiling Terrain configuration for the ceiling
         * @param floor Terrain configuration for the floor
         * @param world World in which the mantle generates
         * @param random Randomizer for terrain generation
         * @param dimension Dimension the cache is used for
         * @param terrainNoise Noise for generating the terrain
         * @param modifierHandler Handler for terrain modifiers
         */
        public CacheEntry(int columnX, int columnZ, TerrainConfiguration ceiling, TerrainConfiguration floor, World world,
                          Random random, DimensionEnum dimension, NoiseGeneratorOctaves terrainNoise, ModifierHandler modifierHandler) {
            this.columnX = columnX;
            this.columnZ = columnZ;
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
                    columnX,
                    columnZ,
                    withSeed(columnX, columnZ, i++),
                    DEFAULT_RELEVANCE,
                    dimension,
                    terrainNoise,
                    modifierHandler);
            }
            i = 0;
            for (TerrainFeature f : ceiling.getMesoFeatures()) {
                TerrainFeatureApplier.applyToHeightmap(
                    f,
                    ceilingHeightmap,
                    ceilingSurfaceBlocks,
                    columnX,
                    columnZ,
                    withSeed(columnX, columnZ, i++),
                    DEFAULT_RELEVANCE,
                    dimension,
                    terrainNoise,
                    modifierHandler);
            }
            i = 0;
            double[] floorHeightmap = new double[CHUNK_AREA];
            ImmutableBlockMeta[] floorSurfaceBlocks = new ImmutableBlockMeta[CHUNK_AREA];
            for (TerrainFeature f : floor.getMacroFeatures()) {
                TerrainFeatureApplier.applyToHeightmap(
                    f,
                    floorHeightmap,
                    floorSurfaceBlocks,
                    columnX,
                    columnZ,
                    withSeed(columnX, columnZ, i++),
                    DEFAULT_RELEVANCE,
                    dimension,
                    terrainNoise,
                    modifierHandler);
            }
            i = 0;
            for (TerrainFeature f : floor.getMesoFeatures()) {
                TerrainFeatureApplier.applyToHeightmap(
                    f,
                    floorHeightmap,
                    floorSurfaceBlocks,
                    columnX,
                    columnZ,
                    withSeed(columnX, columnZ, i++),
                    DEFAULT_RELEVANCE,
                    dimension,
                    terrainNoise,
                    modifierHandler);
            }
            exportData = new MantleCacheData(
                ceilingHeightmap,
                floorHeightmap,
                ceilingSurfaceBlocks,
                floorSurfaceBlocks);
        }

        /**
         * Checks if this is the correct cache for a column
         * @param columnX x coordinate of the currently generating column
         * @param columnZ z coordinate of the currently generating column
         * @return Response of whether this is the correct column
         */
        public boolean isCorrectCache(int columnX, int columnZ) {
            return this.columnX == columnX && this.columnZ == columnZ;
        }

        /**
         * Sets the seed of the randomizer for applying the terrain
         * @param columnX x coordinate of the currently generating column
         * @param columnZ z coordinate of the currently generating column
         * @param index Index of the terrain feature applier
         * @return Randomizer with a new seed
         */
        private Random withSeed(int columnX, int columnZ, int index) {
            long seed = Fnv1a64.initialState();
            seed = Fnv1a64.hashStep(seed, world.getSeed());
            seed = Fnv1a64.hashStep(seed, columnX);
            seed = Fnv1a64.hashStep(seed, columnZ);
            seed = Fnv1a64.hashStep(seed, index);

            random.setSeed(seed);
            return random;
        }
    }
}
