package com.gtnewhorizons.galaxia.registry.dimension;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.util.IProgressUpdate;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;

/**
 * A chunk provider implementation specific to Asteroid Belts
 */
public class ChunkProviderSpaceStation implements IChunkProvider {

    private final World worldObj;

    public ChunkProviderSpaceStation(World world, long seed) {
        this.worldObj = world;
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
        chunk.isModified = true;

        // fill with space air
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 256; y++) {
                for (int z = 0; z < 16; z++) {
                    chunk.func_150807_a(x, y, z, Block.getBlockFromName("galaxia:space_air"), 0);
                }
            }
        }

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

    @Override
    public void populate(IChunkProvider iChunkProvider, int chunkX, int chunkZ) {}

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
        return "SpaceStation";
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
