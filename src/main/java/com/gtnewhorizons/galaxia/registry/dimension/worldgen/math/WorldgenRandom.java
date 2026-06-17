package com.gtnewhorizons.galaxia.registry.dimension.worldgen.math;

import net.minecraft.world.World;

import com.gtnewhorizon.gtnhlib.hash.Fnv1a64;
import com.gtnewhorizon.gtnhlib.util.StdLCG;

/// A StdLCG with some worldgen-specific helper methods.
public class WorldgenRandom extends StdLCG {

    public void prime(World world, int chunkX, int chunkZ, int objectShift, int instanceShift) {
        long seed = Fnv1a64.initialState();
        seed = Fnv1a64.hashStep(seed, world.getSeed());
        seed = Fnv1a64.hashStep(seed, chunkX);
        seed = Fnv1a64.hashStep(seed, chunkZ);
        seed = Fnv1a64.hashStep(seed, objectShift);
        seed = Fnv1a64.hashStep(seed, instanceShift);

        setSeed(seed);
    }
}
