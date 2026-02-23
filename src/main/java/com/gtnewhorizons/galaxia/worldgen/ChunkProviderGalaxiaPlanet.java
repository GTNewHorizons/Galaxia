package com.gtnewhorizons.galaxia.worldgen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import net.minecraft.entity.EnumCreatureType;
import net.minecraft.init.Blocks;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraft.world.gen.NoiseGeneratorPerlin;

import com.gtnewhorizons.galaxia.dimension.BiomeGenSpace;
import com.gtnewhorizons.galaxia.dimension.WorldChunkManagerSpace;
import com.gtnewhorizons.galaxia.utility.BlockMeta;

/**
 * ChunkProvider implementation for Galaxia Planets
 */
public class ChunkProviderGalaxiaPlanet implements IChunkProvider {

    private final World worldObj;
    private final Random rand;
    private final NoiseGeneratorPerlin baseNoise;
    private final boolean showDebug = false;
    private final BlockMeta bedrock = new BlockMeta(Blocks.bedrock, 0);
    private final BlockMeta grass = new BlockMeta(Blocks.grass, 0);
    private final BlockMeta stone = new BlockMeta(Blocks.stone, 0);
    private final BlockMeta snow = new BlockMeta(Blocks.snow, 0);
    private final BlockMeta water = new BlockMeta(Blocks.water, 0);
    private final BlockMeta sand = new BlockMeta(Blocks.sand, 0);
    private final BlockMeta gravel = new BlockMeta(Blocks.gravel, 0);

    /**
     * Constructor to initialize the world and noise/random generators
     * 
     * @param world The world to bind the chunk generator to
     */
    public ChunkProviderGalaxiaPlanet(World world) {
        this.worldObj = world;

        this.rand = new Random(world.getSeed());
        this.baseNoise = new NoiseGeneratorPerlin(rand, 4);
        if (showDebug) writeDebug();
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
        Chunk chunk = new Chunk(worldObj, chunkX, chunkZ);
        ExtendedBlockStorage[] storage = chunk.getBlockStorageArray();

        // Get local biomes
        int[] heightMap = generateBaseHeightmap(chunkX, chunkZ);
        BiomeGenBase[] chunkBiomes = new BiomeGenBase[256];
        List<BiomeGenBase> biomeList = new ArrayList<>();
        double[][] biomeSignificances = new double[((WorldChunkManagerSpace) worldObj.getWorldChunkManager())
            .getBiomeCount()][];
        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                // Get relevant data for biome blending
                BiomeGenBase localBiome = worldObj.getWorldChunkManager()
                    .getBiomeGenAt(chunkX * 16 + localX, chunkZ * 16 + localZ);
                chunkBiomes[localX + localZ * 16] = localBiome;
                if (!biomeList.contains(localBiome)) {
                    biomeList.add(localBiome);
                }
                BiomeGenBase[] adjacentBiomes = ((WorldChunkManagerSpace) worldObj.getWorldChunkManager())
                    .getAdjacentBiomes();
                double[] adjacentBiomeSignificance = ((WorldChunkManagerSpace) worldObj.getWorldChunkManager())
                    .getAdjacentBiomeSignificance();
                int dominanceIndex = 0;
                double dominantSignificance = adjacentBiomeSignificance[0];
                for (int index = 1; index < adjacentBiomeSignificance.length; index++) {
                    if (adjacentBiomeSignificance[index] > dominantSignificance) {
                        dominanceIndex = index;
                        dominantSignificance = adjacentBiomeSignificance[index];
                    }
                }
                double localBiomeSignificance = 1 - dominantSignificance;

                // Set blending value for local biome
                int localBiomeIndex = biomeList.indexOf(localBiome);
                double[] localBiomeSignificanceArray;
                if (biomeSignificances[localBiomeIndex] == null) {
                    localBiomeSignificanceArray = new double[256];
                } else {
                    localBiomeSignificanceArray = biomeSignificances[localBiomeIndex];
                }
                localBiomeSignificanceArray[localX + localZ * 16] = localBiomeSignificance;
                biomeSignificances[localBiomeIndex] = localBiomeSignificanceArray;

                // Set blending value for adjacent biome
                BiomeGenBase adjacentBiome = adjacentBiomes[dominanceIndex];
                if (!biomeList.contains(adjacentBiome)) {
                    biomeList.add(adjacentBiome);
                }
                int adjacentBiomeIndex = biomeList.indexOf(adjacentBiome);
                double[] adjacentBiomeSignificanceArray;
                if (biomeSignificances[adjacentBiomeIndex] == null) {
                    adjacentBiomeSignificanceArray = new double[256];
                } else {
                    adjacentBiomeSignificanceArray = biomeSignificances[adjacentBiomeIndex];
                }
                adjacentBiomeSignificanceArray[localX + localZ * 16] = dominantSignificance;
                biomeSignificances[adjacentBiomeIndex] = adjacentBiomeSignificanceArray;
            }
        }

        // Clean up blending values
        for (int biomeSignificanceIndex = 0; biomeSignificanceIndex
            < biomeSignificances.length; biomeSignificanceIndex++) {
            double[] biomeSignificance = biomeSignificances[biomeSignificanceIndex];
            if (biomeSignificance == null) {
                continue;
            }
            for (int index = 0; index < biomeSignificance.length; index++) {
                double biomeSignificanceValue = biomeSignificance[index];
                if (biomeSignificanceValue > 1) {
                    biomeSignificanceValue = 1;
                } else if (biomeSignificanceValue < 0) {
                    biomeSignificanceValue = 0;
                }
                biomeSignificance[index] = biomeSignificanceValue;
            }
        }

        // Calculate terrain features
        for (int biomeIndex = 0; biomeIndex < biomeList.size(); biomeIndex++) {
            BiomeGenBase currentBiome = biomeList.get(biomeIndex);
            if (currentBiome instanceof BiomeGenSpace) {
                BiomeGenSpace spaceBiome = ((BiomeGenSpace) currentBiome);
                double[] terrainRelevance;
                if (biomeSignificances[biomeIndex] == null) {
                    terrainRelevance = new double[256];
                } else {
                    terrainRelevance = biomeSignificances[biomeIndex];
                }
                TerrainConfiguration terrain = spaceBiome.getTerrain();
                for (TerrainFeature f : terrain.getMacroFeatures()) {
                    TerrainFeatureApplier.applyToHeightmap(f, heightMap, chunkX, chunkZ, rand, terrainRelevance);
                }
                for (TerrainFeature f : terrain.getMesoFeatures()) {
                    TerrainFeatureApplier.applyToHeightmap(f, heightMap, chunkX, chunkZ, rand, terrainRelevance);
                }
            }
        }
        for (int i = 0; i < 256; i++) {
            heightMap[i] = Math.max(1, Math.min(256, heightMap[i]));
        }

        // Generate blocks
        BlockMeta topBlock = grass;
        BlockMeta fillerBlock = stone;
        BlockMeta snowBlock = snow;
        BlockMeta oceanFiller = water;
        BlockMeta oceanSurface = sand;
        BlockMeta seabed = gravel;
        int surfaceDepth = 1;
        int snowHeight = 512;
        int oceanHeight = 0;
        int seabedHeight = 0;
        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                BiomeGenBase localBiome = chunkBiomes[localX + localZ * 16];
                boolean generateBedrock = false;
                if (localBiome instanceof BiomeGenSpace) {
                    BiomeGenSpace spaceBiome = ((BiomeGenSpace) localBiome);
                    generateBedrock = spaceBiome.generateBedrock();
                    topBlock = new BlockMeta(spaceBiome.topBlock, spaceBiome.getTopBlockMeta());
                    fillerBlock = new BlockMeta(spaceBiome.fillerBlock, spaceBiome.getFillerBlockMeta());
                    snowHeight = spaceBiome.getSnowHeight();
                    snowBlock = spaceBiome.getSnowBlock();
                    oceanHeight = spaceBiome.getOceanHeight();
                    oceanFiller = spaceBiome.getOceanFiller();
                    oceanSurface = spaceBiome.getOceanSurface();
                    seabed = spaceBiome.getSeabed();
                    seabedHeight = spaceBiome.getSeabedHeight();
                }
                int height = Math.max(1, heightMap[localX + (localZ << 4)]);
                for (int y = 0; y < Math.max(oceanHeight, height); y++) {
                    int sy = y >> 4;
                    if (storage[sy] == null) {
                        storage[sy] = new ExtendedBlockStorage(sy << 4, !worldObj.provider.hasNoSky);
                    }
                    BlockMeta blockMeta = (y >= height - surfaceDepth) ? topBlock : fillerBlock;
                    if (blockMeta == topBlock && y >= snowHeight) {
                        blockMeta = snowBlock;
                    }
                    if (generateBedrock && y == 0) {
                        blockMeta = bedrock;
                    }
                    if (y <= oceanHeight) {
                        if (y > height - 1) {
                            blockMeta = oceanFiller;
                        } else if (y == height - 1) {
                            if (y > seabedHeight) {
                                blockMeta = oceanSurface;
                            } else {
                                blockMeta = seabed;
                            }
                        }
                    }
                    if (blockMeta.block() != null) {
                        storage[sy].func_150818_a(localX, y & 15, localZ, blockMeta.block());
                        storage[sy].setExtBlockMetadata(localX, y & 15, localZ, blockMeta.meta());
                    }
                }
            }
        }

        chunk.generateSkylightMap();
        return chunk;
    }

    private int[] generateBaseHeightmap(int cx, int cz) {
        int[] hm = new int[256];
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                hm[x + (z << 4)] = 42;
            }
        }
        return hm;
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
    }

    /**
     * Checks whether a chunk exists currently at given coordinates
     * 
     * @param x Target x coordinates
     * @param z Target z coordinates
     * @return
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
     * Gets the list of possible spawn creatures at coordinates - Not used in this implementation
     * 
     * @param type Not used in this implementation
     * @param x    Not used in this implementation
     * @param y    Not used in this implementation
     * @param z    Not used in this implementation
     * @return List of possible spawn creatures
     */
    @Override
    public List<BiomeGenBase.SpawnListEntry> getPossibleCreatures(EnumCreatureType type, int x, int y, int z) {
        return Collections.emptyList();
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
