package com.gtnewhorizons.galaxia.registry.dimension.worldgen;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

public abstract class Feature {

    private final Set<Chunk> touchedChunks = new HashSet<>();
    private final List<Integer[]> updateCoordinates = new ArrayList<>();

    public abstract void generateFeature(World world, Random random, int x, int y, int z, Block[] surfaceRequirements);

    /**
     * Sets block in the world at set coordinates
     *
     * @param world The world to place the block in
     * @param x     Target x coordinate
     * @param y     Target y coordinate
     * @param z     Target z coordinate
     * @param block The block to place
     * @param meta  Metadata of the block to place
     */
    protected void setBlockFast(World world, int x, int y, int z, net.minecraft.block.Block block, int meta) {
        if (y < 0 || y > 255) return;

        Chunk chunk = world.getChunkFromChunkCoords(x >> 4, z >> 4);
        ExtendedBlockStorage[] storage = chunk.getBlockStorageArray();
        int sectionY = y >> 4;

        ExtendedBlockStorage currentBlockStorage = storage[sectionY];
        if (currentBlockStorage == null) {
            currentBlockStorage = storage[sectionY] = new ExtendedBlockStorage(sectionY << 4, !world.provider.hasNoSky);
        }

        int lx = x & 15;
        int ly = y & 15;
        int lz = z & 15;

        currentBlockStorage.func_150818_a(lx, ly, lz, block);
        currentBlockStorage.setExtBlockMetadata(lx, ly, lz, meta);
        chunk.isModified = true;

        // Add chunks for updating
        int cx = x >> 4;
        int cz = z >> 4;
        if (world.getChunkProvider()
            .chunkExists(cx, cz)) {
            Chunk chunkToAdd = world.getChunkFromChunkCoords(cx, cz);
            if (!touchedChunks.contains(chunkToAdd)) {
                touchedChunks.add(chunkToAdd);
                updateCoordinates.add(new Integer[] { cx << 4, cz << 4 });
            }
        }
    }

    public void finishGeneration() {
        for (Chunk chunk : touchedChunks) {
            chunk.generateSkylightMap();
        }
    }

    public List<Integer[]> getUpdateCoordinates() {
        return updateCoordinates;
    }
}
