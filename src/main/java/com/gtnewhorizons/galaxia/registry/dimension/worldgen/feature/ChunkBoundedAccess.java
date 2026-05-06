package com.gtnewhorizons.galaxia.registry.dimension.worldgen.feature;

import net.minecraft.block.Block;
import net.minecraft.world.World;

public final class ChunkBoundedAccess {

    private ChunkBoundedAccess() {}

    public static boolean isLoaded(World world, int x, int z) {
        return world.getChunkProvider()
            .chunkExists(x >> 4, z >> 4);
    }

    public static Block getBlockOr(World world, int x, int y, int z, Block fallback) {
        if (!isLoaded(world, x, z)) return fallback;
        return world.getBlock(x, y, z);
    }

    public static boolean isAirBlockOr(World world, int x, int y, int z, boolean fallback) {
        if (!isLoaded(world, x, z)) return fallback;
        return world.isAirBlock(x, y, z);
    }
}
