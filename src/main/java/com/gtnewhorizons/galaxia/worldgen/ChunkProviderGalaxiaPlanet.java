package com.gtnewhorizons.galaxia.worldgen;

import java.util.Collections;
import java.util.List;
import java.util.Random;

import com.gtnewhorizons.galaxia.dimension.BiomeGenSpace;
import com.gtnewhorizons.galaxia.utility.BlockMeta;
import net.minecraft.block.Block;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.init.Blocks;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraft.world.gen.NoiseGeneratorPerlin;

public class ChunkProviderGalaxiaPlanet implements IChunkProvider {

    private final World worldObj;
    private final TerrainConfiguration terrain;
    private final Random rand;
    private final NoiseGeneratorPerlin baseNoise;
    private final boolean showDebug = false;
    private final BlockMeta bedrock = new BlockMeta(Blocks.bedrock, 0);
    private final BlockMeta grass = new BlockMeta(Blocks.grass, 0);
    private final BlockMeta stone = new BlockMeta(Blocks.stone, 0);

    public ChunkProviderGalaxiaPlanet(World world, TerrainConfiguration terrainConfig) {
        this.worldObj = world;
        this.terrain = terrainConfig != null ? terrainConfig
            : TerrainConfiguration.builder()
                .build();
        this.rand = new Random(world.getSeed());
        this.baseNoise = new NoiseGeneratorPerlin(rand, 4);
        if (showDebug) writeDebug();
    }

    @Override
    public Chunk provideChunk(int chunkX, int chunkZ) {
        Chunk chunk = new Chunk(worldObj, chunkX, chunkZ);
        ExtendedBlockStorage[] storage = chunk.getBlockStorageArray();

        int[] heightMap = generateBaseHeightmap(chunkX, chunkZ);

        for (TerrainFeature f : terrain.getMacroFeatures()) {
            TerrainFeatureApplier.applyToHeightmap(f, heightMap, chunkX, chunkZ, rand);
        }
        for (TerrainFeature f : terrain.getMesoFeatures()) {
            TerrainFeatureApplier.applyToHeightmap(f, heightMap, chunkX, chunkZ, rand);
        }

        for (int i = 0; i < 256; i++) {
            heightMap[i] = Math.max(1, Math.min(256, heightMap[i]));
        }

        BlockMeta topBlock = grass;
        BlockMeta fillerBlock = stone;
        int surfaceDepth = 1;

        for (TerrainFeature terrainFeature : terrain.getAllFeatures()) {
            if (terrainFeature.getTopBlock() != null) {
                topBlock = terrainFeature.getTopBlock();
            }
            if (terrainFeature.getFillerBlock() != null) {
                fillerBlock = terrainFeature.getFillerBlock();
            }
            if (terrainFeature.getDepth() > 0) {
                surfaceDepth = terrainFeature.getDepth();
            }
        }

        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                BiomeGenBase localBiome = worldObj.getWorldChunkManager().getBiomeGenAt(chunkX * 16 + localX, chunkZ * 16 + localZ);
                boolean generateBedrock = false;
                if (localBiome instanceof BiomeGenSpace) {
                    generateBedrock = ((BiomeGenSpace)localBiome).generateBedrock();
                }
                int height = Math.max(1, heightMap[localX + (localZ << 4)]);
                for (int y = 0; y < height; y++) {
                    int sy = y >> 4;
                    if (storage[sy] == null) {
                        storage[sy] = new ExtendedBlockStorage(sy << 4, !worldObj.provider.hasNoSky);
                    }
                    BlockMeta blockMeta = (y >= height - surfaceDepth) ? topBlock : fillerBlock;
                    if (generateBedrock && y == 0) {
                        blockMeta = bedrock;
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

    @Override
    public Chunk loadChunk(int x, int z) {
        return provideChunk(x, z);
    }

    @Override
    public void populate(IChunkProvider provider, int cx, int cz) {
        long seed = (cx * 341873128712L + cz * 132897987541L) ^ worldObj.getSeed();
        rand.setSeed(seed);
    }

    @Override
    public boolean chunkExists(int x, int z) {
        return true;
    }

    @Override
    public boolean canSave() {
        return true;
    }

    @Override
    public String makeString() {
        return "GalaxiaPlanetChunkProvider";
    }

    @Override
    public int getLoadedChunkCount() {
        return 0;
    }

    @Override
    public void saveExtraData() {}

    @Override
    public void recreateStructures(int x, int z) {}

    @Override
    public boolean saveChunks(boolean all, net.minecraft.util.IProgressUpdate progress) {
        return true;
    }

    @Override
    public boolean unloadQueuedChunks() {
        return false;
    }

    @Override
    public List<BiomeGenBase.SpawnListEntry> getPossibleCreatures(EnumCreatureType type, int x, int y, int z) {
        return Collections.emptyList();
    }

    @Override
    public ChunkPosition func_147416_a(World world, String structure, int x, int y, int z) {
        return null;
    }

    public void writeDebug() {
        System.out.println(
            "Terrain features TOTAL: " + this.terrain.getAllFeatures()
                .size());
        System.out.println(
            "MACRO features: " + this.terrain.getMacroFeatures()
                .size());
        System.out.println(
            "MESO  features: " + this.terrain.getMesoFeatures()
                .size());
        System.out.println(
            "MICRO features: " + this.terrain.getMicroFeatures()
                .size());

        if (!this.terrain.getAllFeatures()
            .isEmpty()) {
            System.out.println(
                "First feature: " + this.terrain.getAllFeatures()
                    .get(0));
        }
        if (!this.terrain.getMacroFeatures()
            .isEmpty()) {
            System.out.println(
                "First MACRO: " + this.terrain.getMacroFeatures()
                    .get(0));
        }
    }
}
