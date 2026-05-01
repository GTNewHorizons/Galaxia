package com.gtnewhorizons.galaxia.registry.dimension.cave;

import net.minecraft.world.World;

public interface CaveShape {
    void prepareCaveCache(World world, int chunkX, int chunkZ);

    boolean preparedCaveCache(int chunkX, int chunkZ);

    boolean generateCave(int localX, int localY, int localZ, int height);
}
