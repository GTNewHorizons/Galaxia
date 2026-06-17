package com.gtnewhorizons.galaxia.registry.dimension.worldgen;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.init.Blocks;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import com.falsepattern.endlessids.mixin.helpers.ChunkBiomeHook;
import com.gtnewhorizon.gtnhlib.util.StdLCG;
import com.gtnewhorizon.gtnhlib.util.data.BlockMeta;
import com.gtnewhorizon.gtnhlib.util.data.ImmutableBlockMeta;
import com.gtnewhorizons.galaxia.registry.dimension.DimensionEnum;
import com.gtnewhorizons.galaxia.registry.dimension.biome.BiomeBlockPalette;
import com.gtnewhorizons.galaxia.registry.dimension.biome.BiomeGenSpace;
import com.gtnewhorizons.galaxia.registry.dimension.biome.DefaultBlockPalette;
import com.gtnewhorizons.galaxia.registry.dimension.cave.CaveShape;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.feature.SurfaceFeature;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.feature.UndergroundFeature;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.noise.NoiseSampler;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.noise.NormalizedSampler;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.noise.OctavesSampler;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.noise.ScaledNoise;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import lombok.Getter;

/**
 * ChunkProvider implementation for Galaxia Planets
 */
public class ChunkProviderGalaxiaPlanet implements IChunkProvider, GalaxiaPlanetGenerator {

    private static final int CHUNK_AREA = 256;
    private static final int CHUNK_WIDTH = 16;
    public static final int HEIGHT_LIMIT = 256;

    private static final DefaultBlockPalette DEFAULT_PALETTE = new DefaultBlockPalette();

    private static final ImmutableBlockMeta AIR = new BlockMeta(Blocks.air);

    @Getter
    private final DimensionEnum dimension;
    private final World worldObj;
    private final Random rand;
    private final NoiseSampler crackNoise1, crackNoise2;
    private final boolean showDebug = false;

    private final ImmutableBlockMeta[] surfaceReplacementMap = new ImmutableBlockMeta[CHUNK_AREA];
    private final BiomeGenBase[] chunkBiomes = new BiomeGenBase[CHUNK_AREA];
    private final double[] heightMapBuf = new double[CHUNK_AREA];

    @Getter
    private final HeightOracle heightOracle;

    private final Long2ObjectOpenHashMap<List<DeferredWrite>> deferredWrites = new Long2ObjectOpenHashMap<>();

    public record DeferredWrite(int localX, int y, int localZ, Block block, int meta) {}

    /**
     * Constructor to initialize the world and noise/random generators
     *
     * @param world     The world to bind the chunk generator to
     * @param dimension Galaxia dimension for agnostic block placement
     */
    public ChunkProviderGalaxiaPlanet(World world, DimensionEnum dimension) {
        this.dimension = dimension;
        this.worldObj = world;
        this.heightOracle = new HeightOracle(world, dimension, true);

        this.rand = new StdLCG(world.getSeed());
        this.crackNoise1 = new NormalizedSampler(new ScaledNoise(new OctavesSampler(rand, 2), 0.05));
        this.crackNoise2 = new NormalizedSampler(new ScaledNoise(new OctavesSampler(rand, 2), 0.05));

        if (showDebug) writeDebug();
    }

    @Override
    public void setBlockSafe(int x, int y, int z, Block block, int meta) {
        if (y < 0 || y > 255) return;

        int cx = x >> 4;
        int cz = z >> 4;

        if (!worldObj.getChunkProvider()
            .chunkExists(cx, cz)) {
            this.queueDeferredWrite(x, y, z, block, meta);
        } else {
            worldObj.setBlock(x, y, z, block, meta, 2);
        }
    }

    @Override
    public void queueDeferredWrite(int x, int y, int z, Block block, int meta) {
        int cx = x >> 4;
        int cz = z >> 4;

        long key = ((long) cx << 32) | (cz & 0xFFFFFFFFL);
        deferredWrites.computeIfAbsent(key, k -> new ArrayList<>())
            .add(new DeferredWrite(x & 0xF, y & 0xF, z & 0xF, block, meta));
    }

    private void drainDeferredWrites(int cx, int cz, ExtendedBlockStorage[] storage) {
        long key = ((long) cx << 32) | (cz & 0xFFFFFFFFL);
        List<DeferredWrite> writes = deferredWrites.remove(key);
        if (writes == null) return;
        for (DeferredWrite w : writes) {
            if (w.y < 0 || w.y > 255) continue;
            int sy = w.y >> 4;
            if (storage[sy] == null) {
                storage[sy] = new ExtendedBlockStorage(sy << 4, !worldObj.provider.hasNoSky);
            }
            storage[sy].func_150818_a(w.localX, w.y & 15, w.localZ, w.block);
            storage[sy].setExtBlockMetadata(w.localX, w.y & 15, w.localZ, w.meta);
        }
    }

    /**
     * Provides a chunk to be loaded in the future
     *
     * @param chunkX The chunk x coordinate
     * @param chunkZ The chunk z coordinate
     * @return The provided chunk
     */
    @Override
    public Chunk provideChunk(int chunkX, int chunkZ) {
        long startTime = 0;
        if (showDebug) {
            System.out.println("++++++++ START CHUNK GENERATION ++++++++");
            startTime = System.nanoTime();
        }
        Chunk chunk = new Chunk(worldObj, chunkX, chunkZ);
        ExtendedBlockStorage[] storage = chunk.getBlockStorageArray();

        // Get local biomes + compute heightmap (shared with HeightOracle)
        double[] heightMap = heightMapBuf;
        heightOracle.computeChunkData(chunkX, chunkZ, heightMap, surfaceReplacementMap, chunkBiomes);

        short[] biomeOut = ((ChunkBiomeHook) chunk).getBiomeShortArray();

        for (int i = 0; i < 256; i++) {
            biomeOut[i] = (short) chunkBiomes[i].biomeID;
        }

        long terrainFeatureTime = 0;
        if (showDebug) {
            terrainFeatureTime = System.nanoTime();
            System.out.println("Time for biome blending + terrain features: " + (terrainFeatureTime - startTime));
        }

        // Generate blocks
        long defaultVariableStart = 0;
        if (showDebug) defaultVariableStart = System.nanoTime();

        long assignmentTime = 0;
        long oceanTime = 0;
        long caveTime = 0;
        long placementTime = 0;
        long blockStorageTime = 0;
        long defaultVariableTime = 0;

        CaveShape caveShape = null;

        if (showDebug) {
            defaultVariableTime = System.nanoTime() - defaultVariableStart;
            System.out.println("Time for creating default variables: " + (defaultVariableTime));
        }

        for (int localX = 0; localX < CHUNK_WIDTH; localX++) {
            for (int localZ = 0; localZ < CHUNK_WIDTH; localZ++) {
                long assignmentTimeStart = 0;
                if (showDebug) assignmentTimeStart = System.nanoTime();

                BiomeGenBase localBiome = chunkBiomes[localX + localZ * CHUNK_WIDTH];

                BiomeBlockPalette palette = DEFAULT_PALETTE;

                if (localBiome instanceof BiomeGenSpace spaceBiome) {
                    palette = spaceBiome;

                    if (caveShape == null || !caveShape.equals(spaceBiome.getCaveShape())) {
                        caveShape = spaceBiome.getCaveShape();
                    }
                }

                if (showDebug) assignmentTime += System.nanoTime() - assignmentTimeStart;

                int terrainHeight = Math.max(1, (int) heightMap[localX + (localZ << 4)]);

                if (caveShape != null) {
                    if (!caveShape.preparedCaveShape()) {
                        caveShape.prepareCaveShape(rand);
                    }
                    if (!caveShape.preparedCaveCache(chunkX, chunkZ)) {
                        caveShape.prepareCaveCache(chunkX, chunkZ);
                    }
                }

                for (int y = 0; y < Math.max(palette.getOceanHeight(), terrainHeight); y++) {

                    int sy = y >> 4;

                    if (storage[sy] == null) {
                        storage[sy] = new ExtendedBlockStorage(sy << 4, !worldObj.provider.hasNoSky);
                    }

                    // True when voxel is terrain and can be carved (e.g. by caves)
                    boolean isTerrain = true;

                    ImmutableBlockMeta block;

                    if (y >= terrainHeight - palette.getSurfaceThickness()) {
                        ImmutableBlockMeta replacementBlock = surfaceReplacementMap[localX + (localZ << 4)];

                        if (replacementBlock != null) {
                            block = replacementBlock;
                        } else {
                            block = y >= palette.getSnowHeight() ? palette.getSnowBlock() : palette.getTopBlock();
                        }
                    } else {
                        block = palette.getFillerBlocks()
                            .getStrataBlock(y);
                    }

                    long oceanTimeStart = 0;
                    if (showDebug) oceanTimeStart = System.nanoTime();

                    int oceanHeight = palette.getOceanHeight();

                    if (y <= oceanHeight) {
                        if (y == terrainHeight - 1) {
                            if (y > palette.getSeabedHeight()) {
                                block = palette.getOceanSurface();
                                isTerrain = false;
                            } else {
                                block = palette.getSeabed();
                            }
                        } else if (y > terrainHeight - 1) {
                            isTerrain = false;

                            int oceanDepth = oceanHeight - terrainHeight;

                            boolean topTwoLayers = y == oceanHeight - 1 || y == oceanHeight - 2;

                            boolean isCrack = oceanDepth >= 2 && palette.hasCracks()
                                && topTwoLayers
                                && isCrackBlock(
                                    palette.getOceanCrackThickness(),
                                    chunkX * CHUNK_WIDTH + localX,
                                    chunkZ * CHUNK_WIDTH + localZ);

                            if (isCrack) {
                                // The top layer should always be air and the lower blocks should be the crack blocks
                                block = y == oceanHeight - 1 ? AIR : palette.getOceanCrackBlock();
                            } else {
                                if (y == oceanHeight) {
                                    block = palette.getOceanSurface();
                                } else {
                                    block = palette.getOceanFiller();
                                }
                            }
                        }
                    }

                    long oceanTimeFinish = 0;
                    if (showDebug) {
                        oceanTimeFinish = System.nanoTime();
                        oceanTime += oceanTimeFinish - oceanTimeStart;
                    }

                    if (caveShape != null && isTerrain && caveShape.generateCave(localX, y, localZ, terrainHeight)) {
                        block = AIR;
                    }

                    long caveGenerationTime = 0;
                    if (showDebug) {
                        caveGenerationTime = System.nanoTime();
                        caveTime += caveGenerationTime - oceanTimeFinish;
                    }

                    if (block != null) {
                        if (block.getBlock() != Blocks.air) {
                            storage[sy].func_150818_a(localX, y & 15, localZ, block.getBlock());
                        }
                        if (block.getBlockMeta() != 0) {
                            storage[sy].setExtBlockMetadata(localX, y & 15, localZ, block.getBlockMeta());
                        }
                    }

                    if (showDebug) placementTime += System.nanoTime() - caveGenerationTime;
                }
            }
        }

        long blockGenerationTime = 0;
        if (showDebug) {
            System.out.println("Time for assigning biome variables: " + (assignmentTime));
            System.out.println("Time for creating block storage: " + (blockStorageTime));
            System.out.println("Time for generating oceans: " + (oceanTime));
            System.out.println("Time for generating caves: " + (caveTime));
            System.out.println("Time for placing blocks: " + (placementTime));
            System.out.println(
                "Total time for all tracked block placement steps: "
                    + (assignmentTime + blockStorageTime + oceanTime + caveTime + placementTime + defaultVariableTime));
            blockGenerationTime = System.nanoTime();
            System.out.println("Time for generating blocks: " + (blockGenerationTime - terrainFeatureTime));
        }

        drainDeferredWrites(chunkX, chunkZ, storage);

        chunk.generateSkylightMap();

        if (showDebug) {
            long lightGenerationTime = System.nanoTime();
            System.out.println("Time for generating light: " + (lightGenerationTime - blockGenerationTime));
            System.out.println("-------- END CHUNK GENERATION --------");
        }

        return chunk;
    }

    private boolean isCrackBlock(float crackThickness, int x, int z) {
        double a = crackNoise1.sample(x, z);
        double b = crackNoise2.sample(x, z);

        return a * a + b * b < crackThickness;
    }

    /**
     * Loads a chunk based on world coordinates
     *
     * @param x The target x coordinates
     * @param z The target z coordinates
     * @return The provided chunk at these coordinates
     */
    @Override
    public Chunk loadChunk(int x, int z) {
        return provideChunk(x, z);
    }

    /**
     * Generates a random number generator used for populating chunks with features
     *
     * @param provider The Chunk provider being used
     * @param cx       Chunk x coordinates
     * @param cz       Chunk z coordinates
     */
    @Override
    public void populate(IChunkProvider provider, int cx, int cz) {
        long seed = (cx * 341873128712L + cz * 132897987541L) ^ worldObj.getSeed();
        rand.setSeed(seed);

        // Convert chunk coordinates to 'regular' coordinates
        int x = cx * CHUNK_WIDTH;
        int z = cz * CHUNK_WIDTH;

        // Get local biome
        BiomeGenBase localBiome = worldObj.getWorldChunkManager()
            .getBiomeGenAt(x, z);
        if (localBiome instanceof BiomeGenSpace spaceBiome) {
            int surfaceY = (heightOracle.getColumnHeight(x, z) + 15) >> 4;

            // Generate surface features in locally random points within the chunk
            for (SurfaceFeature feature : spaceBiome.getSurfaceFeatures()) {
                feature.generateSurfaceFeature(worldObj, this, cx, cz);
            }

            // Generate underground features
            for (UndergroundFeature feature : spaceBiome.getUndergroundFeatures()) {
                for (int y = 0; y <= surfaceY; y++) {
                    feature.generateUndergroundFeature(worldObj, this, cx, y, cz);
                }
            }
        }
    }

    /**
     * Checks whether a chunk exists currently at given coordinates
     *
     * @param x Target x coordinates
     * @param z Target z coordinates
     * @return Boolean : The chunk always exists
     */
    @Override
    public boolean chunkExists(int x, int z) {
        return true;
    }

    /**
     * Sets whether the chunk provider can save chunks
     *
     * @return Boolean : True => Can save
     */
    @Override
    public boolean canSave() {
        return true;
    }

    /**
     * Gives a string form of the class
     *
     * @return The string form of this class
     */
    @Override
    public String makeString() {
        return "GalaxiaPlanetChunkProvider";
    }

    /**
     * Gets the current loaded chunk count - Not used in this implementation
     *
     * @return The amount of currently loaded chunks (0)
     */
    @Override
    public int getLoadedChunkCount() {
        return 0;
    }

    /**
     * Not used in this implementation
     */
    @Override
    public void saveExtraData() {}

    /**
     * Not used in this implementation
     *
     * @param x Target x coordinates
     * @param z Target z coordinates
     */
    @Override
    public void recreateStructures(int x, int z) {}

    /**
     * Saves chunks to the game - Not used in this implementation
     *
     * @param all      Not used in this implementation
     * @param progress Not used in this implementation
     * @return true
     */
    @Override
    public boolean saveChunks(boolean all, net.minecraft.util.IProgressUpdate progress) {
        return true;
    }

    /**
     * Gets whether to unloadQueuedChunks
     *
     * @return Boolean : True => Unloads queued
     */
    @Override
    public boolean unloadQueuedChunks() {
        return false;
    }

    /**
     * Gets the list of possible spawn creatures at coordinates - Not used in this
     * implementation
     *
     * @param type Not used in this implementation
     * @param x    Not used in this implementation
     * @param y    Not used in this implementation
     * @param z    Not used in this implementation
     * @return List of possible spawn creatures
     */
    @Override
    public List<BiomeGenBase.SpawnListEntry> getPossibleCreatures(EnumCreatureType type, int x, int y, int z) {
        return List.of();
    }

    /**
     * Not used in this implementation - required for interface
     */
    @Override
    public ChunkPosition func_147416_a(World world, String structure, int x, int y, int z) {
        return null;
    }

    /**
     * Writes a debug message for testing purposes only
     */
    public void writeDebug() {
        // TODO: Update debug to biome-specific terrain generation
        // System.out.println(
        // "Terrain features TOTAL: " + this.terrain.getAllFeatures()
        // .size());
        // System.out.println(
        // "MACRO features: " + this.terrain.getMacroFeatures()
        // .size());
        // System.out.println(
        // "MESO features: " + this.terrain.getMesoFeatures()
        // .size());
        // System.out.println(
        // "MICRO features: " + this.terrain.getMicroFeatures()
        // .size());
        //
        // if (!this.terrain.getAllFeatures()
        // .isEmpty()) {
        // System.out.println(
        // "First feature: " + this.terrain.getAllFeatures()
        // .get(0));
        // }
        // if (!this.terrain.getMacroFeatures()
        // .isEmpty()) {
        // System.out.println(
        // "First MACRO: " + this.terrain.getMacroFeatures()
        // .get(0));
        // }
    }
}
