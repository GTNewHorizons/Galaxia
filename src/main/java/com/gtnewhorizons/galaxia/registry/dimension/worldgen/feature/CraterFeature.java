package com.gtnewhorizons.galaxia.registry.dimension.worldgen.feature;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;

public class CraterFeature extends Feature {

    private final Block tektite;

    public CraterFeature(Block tektite) {
        this.tektite = tektite;
    }

    @Override
    public void generateFeature(World world, Random random, int x, int y, int z, Block[] surfaceRequirements) {
        int diameter = 16 + random.nextInt(16);
        int radius = diameter / 2;
        int squaredCraterRadius = radius * radius;
        int heightOffset = radius / 2;

        for (int localX = -radius; localX <= radius; localX++) {
            int wx = x + localX;
            for (int localZ = -radius; localZ <= radius; localZ++) {
                int wz = z + localZ;
                double rimDistance = localX * localX + localZ * localZ;
                double rimLo = squaredCraterRadius - random.nextInt(96);
                double rimHi = squaredCraterRadius + random.nextInt(64);
                if (rimDistance >= rimLo && rimDistance < rimHi) {
                    boolean prevAir = ChunkBoundedAccess.isAirBlock(world, wx, y - 10 + heightOffset, wz);
                    for (int rimY = -10; rimY <= 10; rimY++) {
                        boolean aboveAir = ChunkBoundedAccess.isAirBlock(world, wx, y + rimY + heightOffset + 1, wz);
                        if (!prevAir && aboveAir) {
                            setBlockFast(world, wx, y + rimY + heightOffset + 1, wz, tektite, 0);
                            break;
                        }
                        prevAir = aboveAir;
                    }
                }
                for (int localY = -radius; localY <= radius; localY++) {
                    int wy = y + localY + heightOffset;
                    if (ChunkBoundedAccess.isAirBlock(world, wx, wy, wz)) continue;
                    double squaredDistance = rimDistance + localY * localY;
                    if (squaredDistance < squaredCraterRadius * (1.0 - random.nextDouble() * 0.1)) {
                        setBlockFast(world, wx, wy, wz, Blocks.air, 0);
                    }
                }
            }
        }
    }
}
