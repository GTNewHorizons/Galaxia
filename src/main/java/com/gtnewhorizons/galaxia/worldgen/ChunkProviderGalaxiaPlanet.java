package com.gtnewhorizons.galaxia.worldgen;

import java.util.Collections;
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
import net.minecraft.world.gen.NoiseGeneratorPerlin;

public class ChunkProviderGalaxiaPlanet implements IChunkProvider {

    private final World worldObj;
    private final TerrainConfiguration terrain;
    private final Random rand;
    private final NoiseGeneratorPerlin baseNoise;
    private final boolean showDebug = false;

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

        Block topBlock = Blocks.grass;
        Block fillerBlock = Blocks.stone;
        int surfaceDepth = 1;

        for (TerrainFeature f : terrain.getAllFeatures()) {
            if (f.getTopBlock() != null) {
                topBlock = f.getTopBlock();
            }
            if (f.getFillerBlock() != null) {
                fillerBlock = f.getFillerBlock();
            }
            if (f.getDepth() > 0) {
                surfaceDepth = f.getDepth();
            }
        }

        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int h = Math.max(1, heightMap[lx + (lz << 4)]);
                for (int y = 0; y < h; y++) {
                    int sy = y >> 4;
                    if (storage[sy] == null) {
                        storage[sy] = new ExtendedBlockStorage(sy << 4, !worldObj.provider.hasNoSky);
                    }
                    Block block = (y >= h - surfaceDepth) ? topBlock : fillerBlock;
                    storage[sy].func_150818_a(lx, y & 15, lz, block);
                    storage[sy].setExtBlockMetadata(lx, y & 15, lz, 0);
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
