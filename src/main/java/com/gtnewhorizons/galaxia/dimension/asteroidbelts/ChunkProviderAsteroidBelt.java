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

/**
 * A chunk provider implementation specific to Asteroid Belts
 */
public class ChunkProviderAsteroidBelt implements IChunkProvider {

    private final Random rand;
    private final World worldObj;
    private final Asteroid[] asteroids;

    public ChunkProviderAsteroidBelt(World world, long seed, Asteroid[] asteroids) {
        this.worldObj = world;
        this.rand = new Random(seed);
        this.asteroids = asteroids;
    }

    /**
     * Checks that a chunk exists at given coordinates
     * 
     * @param x Checked x coordinate
     * @param z Checked z coordinate
     * @return boolean - Whether chunk exists or not
     */
    @Override
    public boolean chunkExists(int x, int z) {
        return true;
    }

    /**
     * Creates a new basic chunk at given chunk coordinates
     * 
     * @param chunkX The chunk x coordinates
     * @param chunkZ The chunk z coordinates
     * @return The chunk generated
     */
    public Chunk provideChunk(int chunkX, int chunkZ) {
        Chunk chunk = new Chunk(worldObj, chunkX, chunkZ);
        chunk.generateSkylightMap();
        return chunk;
    }

    /**
     * Loads and generates a chunk at given chunk coordinates
     * 
     * @param x The x coordinates to load at
     * @param z The z coordinates to load at
     * @return The chunk generated
     */
    @Override
    public Chunk loadChunk(int x, int z) {
        return this.provideChunk(x, z);
    }

    /**
     * Populates given chunk with asteroids
     * 
     * @param iChunkProvider the chunk provider interface
     * @param chunkX         The chunk x coordinates
     * @param chunkZ         The chunk y coordinates
     */
    @Override
    public void populate(IChunkProvider iChunkProvider, int chunkX, int chunkZ) {
        // Convert chunk coordinates to 'regular' coordinates
        int x = chunkX * 16;
        int z = chunkZ * 16;

        // Create asteroids in locally random points within the chunk
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

    // Unused but needs implementation from interface
    @Override
    public ChunkPosition func_147416_a(World p_147416_1_, String p_147416_2_, int p_147416_3_, int p_147416_4_,
        int p_147416_5_) {
        return null;
    }

    // Unused but needs implementation from interface
    @Override
    public int getLoadedChunkCount() {
        return 0;
    }

    // Unused but needs implementation from interface
    @Override
    public void recreateStructures(int x, int z) {

    }

    // Unused but needs implementation from interface
    @Override
    public void saveExtraData() {

    }
}
