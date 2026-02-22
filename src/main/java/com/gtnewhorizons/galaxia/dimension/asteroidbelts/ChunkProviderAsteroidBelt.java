package com.gtnewhorizons.galaxia.dimension.asteroidbelts;

import java.util.List;
import java.util.Random;

import net.minecraft.entity.EnumCreatureType;
import net.minecraft.util.IProgressUpdate;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;

import com.gtnewhorizons.galaxia.worldgen.Asteroid;

public class ChunkProviderAsteroidBelt implements IChunkProvider {

    private final Random rand;
    private final World worldObj;
    private final Asteroid[] asteroids;

    public ChunkProviderAsteroidBelt(World world, long seed, Asteroid[] asteroids) {
        this.worldObj = world;
        this.rand = new Random(seed);
        this.asteroids = asteroids;
    }

    @Override
    public boolean chunkExists(int x, int z) {
        return true;
    }

    public Chunk provideChunk(int chunkX, int chunkZ) {
        Chunk chunk = new Chunk(worldObj, chunkX, chunkZ);
        chunk.generateSkylightMap();
        return chunk;
    }

    @Override
    public Chunk loadChunk(int x, int z) {
        return this.provideChunk(x, z);
    }

    @Override
    public void populate(IChunkProvider iChunkProvider, int chunkX, int chunkZ) {
        int x = chunkX * 16;
        int z = chunkZ * 16;

        for (Asteroid asteroid : asteroids) {
            int localX = x + this.rand.nextInt(16) + 8;
            int localY = this.rand.nextInt(176) + 16;
            int localZ = z + this.rand.nextInt(16) + 8;
            asteroid.generate(worldObj, rand, localX, localY, localZ);
        }
    }

    @Override
    public boolean saveChunks(boolean all, IProgressUpdate progressUpdate) {
        return true;
    }

    @Override
    public boolean unloadQueuedChunks() {
        return false;
    }

    @Override
    public boolean canSave() {
        return true;
    }

    @Override
    public String makeString() {
        return "RandomAsteroidSource";
    }

    @Override
    public List<BiomeGenBase.SpawnListEntry> getPossibleCreatures(EnumCreatureType enumCreatureType, int x, int y,
        int z) {
        return null;
    }

    @Override
    public ChunkPosition func_147416_a(World world, String structure, int x, int y, int z) {
        return null;
    }

    @Override
    public int getLoadedChunkCount() {
        return 0;
    }

    @Override
    public void recreateStructures(int x, int z) {

    }

    @Override
    public void saveExtraData() {

    }
}
