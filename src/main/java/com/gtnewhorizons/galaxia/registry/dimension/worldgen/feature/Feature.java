package com.gtnewhorizons.galaxia.registry.dimension.worldgen.feature;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import com.gtnewhorizons.galaxia.registry.dimension.worldgen.ChunkProviderGalaxiaPlanet;

import com.gtnewhorizons.galaxia.registry.dimension.worldgen.GalaxiaPlanetGenerator;
import it.unimi.dsi.fastutil.longs.LongCollection;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

/**
 * Generates a feature with a defined shape within a chunk.
 * Placed by a location rule.
 */
public abstract class Feature {

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

            GalaxiaPlanetGenerator provider = GalaxiaPlanetGenerator.of(world);
            if (provider != null) {
                provider.queueDeferredWrite(x, y, z, block, meta);
            }
            return;
        }

        world.setBlock(x, y, z, block, meta, 2);
    }
}
