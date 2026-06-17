package com.gtnewhorizons.galaxia.registry.dimension.worldgen;

import net.minecraft.block.Block;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.ChunkProviderServer;

public interface GalaxiaPlanetGenerator {

    static GalaxiaPlanetGenerator of(World world) {
        if (world == null) return null;

        IChunkProvider cp = world.getChunkProvider();

        if (cp instanceof ChunkProviderServer cps) {
            if (cps.currentChunkProvider instanceof GalaxiaPlanetGenerator inner) {
                return inner;
            }
        }

        return null;
    }

    void queueDeferredWrite(int x, int y, int z, Block block, int meta);

    HeightOracle getHeightOracle();

    /// Sets the block at the location without causing cascading worldgen.
    /// In vanilla, this will defer the update if the chunk hasn't been generated, or do it immediately if it has.
    /// In cubic chunks, cascading worldgen isn't a thing, so this just sets the block immediately.
    void setBlockSafe(int x, int y, int z, Block block, int meta);
}
