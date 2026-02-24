package com.gtnewhorizons.galaxia.worldgen;

import com.gtnewhorizons.galaxia.utility.BlockMeta;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;

import java.util.Random;

public class WorldGenCrater extends WorldGenGalaxia {
    public WorldGenCrater(int rarity, BlockMeta surfaceRequirement) {
        super(rarity, surfaceRequirement);
    }

    @Override
    public boolean generate(World world, Random random, int x, int y, int z) {
        if (!super.generate(world, random, x, y, z)) {
            return false;
        }
        int diameter = 8 + random.nextInt(8);
        int radius = diameter / 2;
        int squaredCraterRadius = radius*radius;
        for (int localX = -radius; localX <= radius; localX++) {
            for (int localY = -radius; localY <= radius; localY++) {
                for (int localZ = -radius; localZ <= radius; localZ++) {
                    if (world.isAirBlock(x + localX, y + localY, z + localZ)) continue;
                    double squaredDistance = localX * localX + localY * localY + localZ * localZ;
                    if (squaredDistance < squaredCraterRadius * (1.0 - random.nextDouble() * 0.3)) {
                        setBlockFast(world, localX + x, localY + y, localZ + z, Blocks.air, 0);
                    }
                }
            }
        }
        return true;
    }
}
