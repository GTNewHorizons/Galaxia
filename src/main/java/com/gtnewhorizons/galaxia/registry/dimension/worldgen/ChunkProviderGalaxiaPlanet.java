package com.gtnewhorizons.galaxia.registry.dimension.worldgen;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.init.Blocks;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraft.world.gen.NoiseGeneratorOctaves;

import com.gtnewhorizons.galaxia.registry.dimension.DimensionEnum;
import com.gtnewhorizons.galaxia.registry.dimension.biome.BiomeGenSpace;
import com.gtnewhorizons.galaxia.registry.dimension.provider.WorldChunkManagerSpace;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.locationrule.LocationRuleGalaxiaCave;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.locationrule.LocationRuleGalaxiaSurface;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.locationrule.LocationRuleGalaxiaWall;

/**
 * ChunkProvider implementation for Galaxia Planets
 */
public class ChunkProviderGalaxiaPlanet implements IChunkProvider {

    private static final int CHUNK_AREA = 256;
    private static final int CHUNK_WIDTH = 16;
    private static final int HEIGHT_LIMIT = 256;
    private static final double ALLOWED_DIVERGENCE = 0.25;
    private static final double HORIZONTAL_CAVE_STRETCH = 0.1;
    private static final double VERTICAL_CAVE_STRETCH = 0.1;

    private final DimensionEnum dimension;
    private final World worldObj;
    private final Random rand;
    private final NoiseGeneratorOctaves crackNoise;
    private final NoiseGeneratorOctaves baseNoise;
    private final NoiseGeneratorOctaves caveNoise;
    private final boolean showDebug = false;

    private final double[][] caveCache = new double[CHUNK_AREA][HEIGHT_LIMIT];

    /**
     * Constructor to initialize the world and noise/random generators
     *
     * @param world     The world to bind the chunk generator to
     * @param dimension Galaxia dimension for agnostic block placement
     */
    public ChunkProviderGalaxiaPlanet(World world, DimensionEnum dimension) {
        this.dimension = dimension;
        this.worldObj = world;

        this.rand = new Random(world.getSeed());
        this.baseNoise = new NoiseGeneratorOctaves(rand, 4);
        this.caveNoise = new NoiseGeneratorOctaves(rand, 4);
        this.crackNoise = new NoiseGeneratorOctaves(rand, 2);
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
        System.out.println("++++++++ START CHUNK GENERATION ++++++++");
        long startTime = System.nanoTime();
        Chunk chunk = new Chunk(worldObj, chunkX, chunkZ);
        ExtendedBlockStorage[] storage = chunk.getBlockStorageArray();
        prepareCaveCache(chunkX, chunkZ);
        long preparationTime = System.nanoTime();
        System.out.println("Time for preparing cave generation: " + (preparationTime - startTime));

        // Get local biomes
        double[] heightMap = generateBaseHeightmap();
        Block[] surfaceReplacementMap = new Block[CHUNK_AREA];
        int biomeCount = ((WorldChunkManagerSpace) worldObj.getWorldChunkManager()).getBiomeCount();
        BiomeGenBase[] chunkBiomes = new BiomeGenBase[CHUNK_AREA];
        double[][] biomeContrib = new double[biomeCount][];
        List<BiomeGenBase> biomeList = new ArrayList<>();
        // Between 0 and 1, smooth range between biome (0 is not smoothed, vertical
        // cliffs, 1 is indistinguishable
        // between biomes)
        for (int x = 0; x < CHUNK_WIDTH; x++) {
            for (int z = 0; z < CHUNK_WIDTH; z++) {
                // Get relevant data for biome blending
                BiomeGenBase[] blockBiomes = ((WorldChunkManagerSpace) worldObj.getWorldChunkManager())
                    .getLocalBiomes(chunkX * CHUNK_WIDTH + x, chunkZ * CHUNK_WIDTH + z);
                double[] blockContrib = ((WorldChunkManagerSpace) worldObj.getWorldChunkManager())
                    .getLocalBiomeSignificance(ALLOWED_DIVERGENCE);
                // Smoothing
                double sum = 0;
                int contribSize = blockContrib.length;
                for (int i = 0; i < contribSize; i++) {
                    final double originalContrib = blockContrib[i];
                    final double squaredContrib = originalContrib * originalContrib;
                    blockContrib[i] = -1 * (squaredContrib * originalContrib * 2) + squaredContrib * 3;
                    sum += blockContrib[i];
                }
                // Renormalizing
                for (int i = 0; i < contribSize; i++) {
                    blockContrib[i] /= sum;
                }
                // Reorganize block contributions into biome order
                double maxContrib = 0;
                for (int i = 0; i < contribSize; i++) {
                    if (!biomeList.contains(blockBiomes[i])) {
                        biomeList.add(blockBiomes[i]);
                        biomeContrib[biomeList.indexOf(blockBiomes[i])] = new double[CHUNK_AREA];
                    }
                    if (blockContrib[i] > maxContrib) {
                        maxContrib = blockContrib[i];
                        chunkBiomes[x + (z << 4)] = blockBiomes[i];
                    }
                    biomeContrib[biomeList.indexOf(blockBiomes[i])][x + (z << 4)] += blockContrib[i];
                }
            }
        }
        long blendingTime = System.nanoTime();
        System.out.println("Time for blending biomes: " + (blendingTime - preparationTime));

        // Calculate terrain features
        for (int biomeIndex = 0; biomeIndex < biomeList.size(); biomeIndex++) {
            BiomeGenBase currentBiome = biomeList.get(biomeIndex);
            if (currentBiome instanceof BiomeGenSpace spaceBiome) {
                double[] terrainRelevance = biomeContrib[biomeIndex];
                TerrainConfiguration terrain = spaceBiome.getTerrain();
                for (TerrainFeature f : terrain.getMacroFeatures()) {
                    TerrainFeatureApplier.applyToHeightmap(
                        f,
                        heightMap,
                        surfaceReplacementMap,
                        chunkX,
                        chunkZ,
                        rand,
                        terrainRelevance,
                        dimension);
                }
                for (TerrainFeature f : terrain.getMesoFeatures()) {
                    TerrainFeatureApplier.applyToHeightmap(
                        f,
                        heightMap,
                        surfaceReplacementMap,
                        chunkX,
                        chunkZ,
                        rand,
                        terrainRelevance,
                        dimension);
                }
            }
        }
        for (int i = 0; i < CHUNK_AREA; i++) {
            heightMap[i] = Math.clamp(heightMap[i], 1, HEIGHT_LIMIT);
        }
        long terrainFeatureTime = System.nanoTime();
        System.out.println("Time for applying terrain features: " + (terrainFeatureTime - blendingTime));

        // Generate blocks
        long defaultVariableStart = System.nanoTime();
        Block topBlock = Blocks.grass;
        StratificationPreset fillerBlocks = new StratificationPreset(Blocks.stone);
        Block snowBlock = Blocks.snow;
        Block oceanFiller = Blocks.water;
        Block oceanSurface = Blocks.sand;
        Block seabed = Blocks.gravel;
        Block oceanCrackBlock = Blocks.lava;
        int surfaceDepth = 1;
        int snowHeight = 512;
        int oceanHeight = 0;
        int seabedHeight = 0;
        int oceanCrackComplexity = 1;
        float oceanCrackThickness = 0.5F;
        boolean generateCaves = false;
        long assignmentTime = 0;
        long oceanTime = 0;
        long caveTime = 0;
        long placementTime = 0;
        long defaultVariableEnd = System.nanoTime();
        long defaultVariableTime = defaultVariableEnd - defaultVariableStart;
        long blockStorageTime = 0;
        System.out.println("Time for creating default variables: " + (defaultVariableTime));
        for (int localX = 0; localX < CHUNK_WIDTH; localX++) {
            for (int localZ = 0; localZ < CHUNK_WIDTH; localZ++) {
                long assignmentTimeStart = System.nanoTime();
                BiomeGenBase localBiome = chunkBiomes[localX + localZ * CHUNK_WIDTH];
                if (localBiome instanceof BiomeGenSpace spaceBiome) {
                    topBlock = getSurfaceBlock(
                        spaceBiome.getTopBlockMetas(),
                        chunkX * CHUNK_WIDTH + localX,
                        chunkZ * CHUNK_WIDTH + localZ);
                    fillerBlocks = spaceBiome.getFillerBlocks();
                    snowHeight = spaceBiome.getSnowHeight();
                    snowBlock = spaceBiome.getSnowBlock();
                    oceanHeight = spaceBiome.getOceanHeight();
                    oceanFiller = spaceBiome.getOceanFiller();
                    oceanSurface = spaceBiome.getOceanSurface();
                    seabed = spaceBiome.getSeabed();
                    seabedHeight = spaceBiome.getSeabedHeight();
                    generateCaves = spaceBiome.generateCaves();
                    surfaceDepth = spaceBiome.getSurfaceThickness();
                    oceanCrackBlock = spaceBiome.getOceanCrackBlock();
                    oceanCrackThickness = spaceBiome.getOceanCrackThickness();
                    oceanCrackComplexity = spaceBiome.getOceanCrackComplexity();
                }
                long assignmentTimeFinish = System.nanoTime();
                assignmentTime += (assignmentTimeFinish - assignmentTimeStart);
                int height = Math.max(1, (int) heightMap[localX + (localZ << 4)]);
                Block replacementBlock = surfaceReplacementMap[localX + (localZ << 4)];
                for (int y = 0; y < Math.max(oceanHeight, height); y++) {
                    long blockStorageStart = System.nanoTime();
                    int sy = y >> 4;
                    if (storage[sy] == null) {
                        storage[sy] = new ExtendedBlockStorage(sy << 4, !worldObj.provider.hasNoSky);
                    }
                    long blockStorageFinish = System.nanoTime();
                    blockStorageTime += (blockStorageFinish - blockStorageStart);
                    Block block;
                    if (y >= height - surfaceDepth) {
                        block = topBlock;
                        if (replacementBlock != null) {
                            block = replacementBlock;
                            storage[sy].func_150818_a(localX, y & 15, localZ, block);
                            continue;
                        }
                    } else {
                        block = fillerBlocks.getStrataBlock(y);
                    }
                    if (block == topBlock && y >= snowHeight) {
                        block = snowBlock;
                    }
                    long oceanTimeStart = System.nanoTime();
                    if (y <= oceanHeight) {
                        if (y > height - 1) {
                            if (y == oceanHeight - 2 && oceanHeight - height >= 2) {
                                block = getOceanSurfaceBlock(
                                    oceanFiller,
                                    oceanCrackBlock,
                                    oceanCrackThickness,
                                    oceanCrackComplexity,
                                    chunkX * CHUNK_WIDTH + localX,
                                    chunkZ * CHUNK_WIDTH + localZ);
                            } else if (y == oceanHeight - 1 && oceanHeight - height >= 2) {
                                block = getOceanSurfaceBlock(
                                    oceanFiller,
                                    oceanCrackBlock,
                                    oceanCrackThickness,
                                    oceanCrackComplexity,
                                    chunkX * CHUNK_WIDTH + localX,
                                    chunkZ * CHUNK_WIDTH + localZ);
                                if (block != oceanFiller) {
                                    block = Blocks.air;
                                }
                            } else {
                                block = oceanFiller;
                            }
                        } else if (y == height - 1) {
                            if (y > seabedHeight) {
                                block = oceanSurface;
                            } else {
                                block = seabed;
                            }
                        }
                    }
                    long oceanTimeFinish = System.nanoTime();
                    oceanTime += (oceanTimeFinish - oceanTimeStart);
                    if (generateCaves
                        && (block == fillerBlocks.getStrataBlock(y) || block == topBlock || block == snowBlock)
                        && generateCave(localX, y, localZ, height)) {
                        block = Blocks.air;
                    }
                    long caveGenerationTime = System.nanoTime();
                    caveTime += (caveGenerationTime - oceanTimeFinish);
                    if (block != null) {
                        storage[sy].func_150818_a(localX, y & 15, localZ, block);
                    }
                    long blockPlacementTime = System.nanoTime();
                    placementTime += (blockPlacementTime - caveGenerationTime);
                }
            }
        }
        System.out.println("Time for assigning biome variables: " + (assignmentTime));
        System.out.println("Time for creating block storage: " + (blockStorageTime));
        System.out.println("Time for generating oceans: " + (oceanTime));
        System.out.println("Time for generating caves: " + (caveTime));
        System.out.println("Time for placing blocks: " + (placementTime));
        System.out.println(
            "Total time for all tracked block placement steps: "
                + (assignmentTime + blockStorageTime + oceanTime + caveTime + placementTime + defaultVariableTime));
        long blockGenerationTime = System.nanoTime();
        System.out.println("Time for generating blocks: " + (blockGenerationTime - terrainFeatureTime));

        chunk.generateSkylightMap();
        long lightGenerationTime = System.nanoTime();
        System.out.println("Time for generating light: " + (lightGenerationTime - blockGenerationTime));

        System.out.println("-------- END CHUNK GENERATION --------");
        return chunk;
    }

    private void prepareCaveCache(int chunkX, int chunkZ) {
        double[] horizontalLayer = caveNoise.generateNoiseOctaves(
            new double[CHUNK_AREA],
            chunkZ * CHUNK_WIDTH,
            chunkX * CHUNK_WIDTH,
            CHUNK_WIDTH,
            CHUNK_WIDTH,
            HORIZONTAL_CAVE_STRETCH,
            HORIZONTAL_CAVE_STRETCH,
            0);
        for (int i = 0; i < horizontalLayer.length; i++) {
            double noise = horizontalLayer[i];
            noise += 8;
            noise /= 16;
            caveCache[i][0] = noise;
        }
        double[] verticalSlice = caveNoise.generateNoiseOctaves(
            new double[HEIGHT_LIMIT],
            chunkZ,
            chunkX,
            HEIGHT_LIMIT,
            1,
            VERTICAL_CAVE_STRETCH,
            VERTICAL_CAVE_STRETCH,
            0);
        for (int i = 0; i < verticalSlice.length; i++) {
            double noise = verticalSlice[i];
            noise += 8;
            noise /= 16;
            verticalSlice[i] = noise;
        }
        for (int i = 0; i < caveCache.length; i++) {
            double baseNoise = caveCache[i][0];
            for (int j = 1; j < verticalSlice.length; j++) {
                caveCache[i][j] = (baseNoise + verticalSlice[j]) / 2;
            }
        }
    }

    private boolean generateCave(int localX, int localY, int localZ, int height) {
        if (localY >= HEIGHT_LIMIT) {
            return false;
        }
        double localNoise = caveCache[localX + localZ * CHUNK_WIDTH][localY];
        double boundTightening;
        int ceilingDistance = height - localY;
        if (ceilingDistance > 0 && ceilingDistance < CHUNK_WIDTH) {
            boundTightening = 0.75 / ceilingDistance;
        } else if (localY > 4) {
            boundTightening = 0;
        } else {
            boundTightening = (double) 1 / (Math.max(localY - 1, 1));
        }
        double lowerBound = 0.45;
        double upperBound = 0.5 - 0.05 * boundTightening;
        return localNoise < upperBound && localNoise > lowerBound;
    }

    private Block getSurfaceBlock(List<Block> blocks, int x, int z) {
        int surfaceBlockCount = blocks.size();
        if (surfaceBlockCount == 1) {
            return blocks.getFirst();
        }
        Block surfaceBlock;
        double noise = baseNoise.generateNoiseOctaves(new double[1], z, x, 1, 1, 0.2, 0.2, 0)[0];
        noise += 8;
        noise *= surfaceBlockCount;
        noise /= 16;
        int pickedSurface = (int) Math.floor(noise);
        if (pickedSurface >= surfaceBlockCount) {
            pickedSurface = surfaceBlockCount - 1;
        } else if (pickedSurface < 0) {
            pickedSurface = 0;
        }
        surfaceBlock = blocks.get(pickedSurface);
        return surfaceBlock;
    }

    private Block getOceanSurfaceBlock(Block mainBlock, Block crackBlock, float crackThickness,
        int oceanCrackComplexity, int x, int z) {
        if (crackBlock == null || crackThickness == 0) {
            return mainBlock;
        }
        double noise = 0;
        for (int octave = 0; octave < oceanCrackComplexity; octave++) {
            double octaveExponent = Math.pow(2, octave);
            noise += Math.abs(
                crackNoise
                    .generateNoiseOctaves(new double[1], z, x, 1, 1, 0.2 / octaveExponent, 0.2 / octaveExponent, 0)[0]
                    / octaveExponent);
        }
        return noise < crackThickness ? crackBlock : mainBlock;
    }

    private double[] generateBaseHeightmap() {
        double[] hm = new double[CHUNK_AREA];
        for (int x = 0; x < CHUNK_WIDTH; x++) {
            for (int z = 0; z < CHUNK_WIDTH; z++) {
                hm[z + (x << 4)] = 8;
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

        // Convert chunk coordinates to 'regular' coordinates
        int x = cx * CHUNK_WIDTH;
        int z = cz * CHUNK_WIDTH;

        // Get local biome
        BiomeGenBase localBiome = worldObj.getWorldChunkManager()
            .getBiomeGenAt(x, z);
        Set<Integer[]> updateCoordinates = new HashSet<>();
        if (localBiome instanceof BiomeGenSpace spaceBiome) {
            if (spaceBiome.getSurfaceFeatures()
                .isEmpty()) {
                return;
            }
            // Generate surface features in locally random points within the chunk
            for (LocationRuleGalaxiaSurface feature : spaceBiome.getSurfaceFeatures()) {
                int localX = x - 8;
                int localZ = z - 8;
                if (!feature.isCentered()) {
                    localX += this.rand.nextInt(CHUNK_WIDTH);
                    localZ += this.rand.nextInt(CHUNK_WIDTH);
                }
                int localY = worldObj.getHeightValue(x, z);
                feature.generate(worldObj, rand, localX, localY, localZ);
                updateCoordinates.addAll(
                    feature.getFeature()
                        .getUpdateCoordinates());
            }
            // Generate cave features
            for (LocationRuleGalaxiaCave feature : spaceBiome.getCaveFeatures()) {
                int maximumHeight = feature.getMaximumHeight();
                int minimumHeight = feature.getMinimumHeight();
                for (int frequency = 0; frequency < feature.getFrequency(); frequency++) {
                    int localX = x - 8;
                    int localZ = z - 8;
                    if (!feature.isCentered()) {
                        localX += this.rand.nextInt(CHUNK_WIDTH);
                        localZ += this.rand.nextInt(CHUNK_WIDTH);
                    }
                    int localY = rand.nextInt(
                        Math.min(worldObj.getHeightValue(x, z), maximumHeight - minimumHeight) + 1) + minimumHeight;
                    feature.generate(worldObj, rand, localX, localY, localZ);
                    updateCoordinates.addAll(
                        feature.getFeature()
                            .getUpdateCoordinates());
                }
            }
            // Generate wall features
            for (LocationRuleGalaxiaWall feature : spaceBiome.getWallFeatures()) {
                int localX = x - 8;
                int localZ = z - 8;
                if (!feature.isCentered()) {
                    localX += this.rand.nextInt(CHUNK_WIDTH);
                    localZ += this.rand.nextInt(CHUNK_WIDTH);
                }
                int minimumHeight = feature.getMinimumHeight();
                int localY = minimumHeight;
                int localHeight = worldObj.getHeightValue(x, z);
                if (localY > localHeight) {
                    continue;
                }
                localY += rand.nextInt(
                    Math.max(1, Math.min(feature.getMaximumHeight() - minimumHeight, localHeight - minimumHeight)));
                feature.generate(worldObj, rand, localX, localY, localZ);
                updateCoordinates.addAll(
                    feature.getFeature()
                        .getUpdateCoordinates());
            }
        }

        // Update affected chunks
        for (Integer[] coordinates : updateCoordinates) {
            int localX = coordinates[0];
            int localZ = coordinates[1];
            Block originalBlock = worldObj.getBlock(localX, 0, localZ);
            int originalMeta = worldObj.getBlockMetadata(localX, 0, localZ);
            worldObj.setBlock(localX, 0, localZ, originalBlock, originalMeta, 3);
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
