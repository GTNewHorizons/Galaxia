package com.gtnewhorizons.galaxia.registry.dimension.worldgen.feature;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;

import com.gtnewhorizons.galaxia.registry.dimension.worldgen.ChunkProviderGalaxiaPlanet;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.GalaxiaPlanetGenerator;

public final class ChunkBoundedAccess {

    private ChunkBoundedAccess() {}

    public static boolean isLoaded(World world, int x, int z) {
        return world.getChunkProvider()
            .chunkExists(x >> 4, z >> 4);
    }

    public static Block getBlock(World world, int x, int y, int z) {
        if (isLoaded(world, x, z)) return world.getBlock(x, y, z);
        GalaxiaPlanetGenerator provider = GalaxiaPlanetGenerator.of(world);
        if (provider != null) {
            return provider.getHeightOracle().getPredictedBlock(x, y, z).getBlock();
        }
        return Blocks.stone;
    }

    public static boolean isAirBlock(World world, int x, int y, int z) {
        if (isLoaded(world, x, z)) return world.isAirBlock(x, y, z);
        GalaxiaPlanetGenerator provider = GalaxiaPlanetGenerator.of(world);
        if (provider != null) {
            return provider.getHeightOracle().isAir(x, y, z);
        }
        return false;
    }
}
