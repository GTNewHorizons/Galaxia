package com.gtnewhorizons.galaxia.registry.dimension.worldgen.feature;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import com.gtnewhorizons.galaxia.registry.dimension.worldgen.ChunkProviderGalaxiaPlanet;

import it.unimi.dsi.fastutil.longs.LongCollection;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

/**
 * Generates a feature with a defined shape within a chunk.
 * Placed by a location rule.
 */
public abstract class Feature {

    private final LongOpenHashSet updateCoordinates = new LongOpenHashSet();

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

        int cx = x >> 4;
        int cz = z >> 4;
        if (!world.getChunkProvider()
            .chunkExists(cx, cz)) {

            ChunkProviderGalaxiaPlanet provider = ChunkProviderGalaxiaPlanet.of(world);
            if (provider != null) {
                provider.queueDeferredWrite(cx, cz, x & 15, y, z & 15, block, meta);
            }
            return;
        }

        Chunk chunk = world.getChunkFromChunkCoords(cx, cz);
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

        updateCoordinates.add(((long) cx << 32) | (cz & 0xFFFFFFFFL));
    }

    public void drainUpdateCoordinatesTo(LongCollection sink) {
        sink.addAll(updateCoordinates);
        updateCoordinates.clear();
    }
}
