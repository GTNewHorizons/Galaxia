package com.gtnewhorizons.galaxia.registry.dimension.worldgen;

import net.minecraft.block.Block;
import net.minecraft.world.World;

import java.util.Random;

public class CrystalClusterFeature extends Feature {
    private final Block crystalBlock;

    public CrystalClusterFeature(Block crystalBlock) {
        this.crystalBlock = crystalBlock;
    }

    @Override
    public void generateFeature(World world, Random random, int x, int y, int z, Block[] surfaceRequirements) {
        for (int crystalCount = 0; crystalCount < random.nextInt(12) + 1; crystalCount++) {
            generateCrystal(world, random, x, y, z);
        }
    }

    private void generateCrystal(World world, Random random, int x, int y, int z) {
        int height = random.nextInt(16) + 4;
        int straightness = random.nextInt(4) + 1;
        int straightnessIterator;
        int xTilt = random.nextInt(3) - 1;
        int zTilt = random.nextInt(3) - 1;
        int xOffset;
        int zOffset;
        int thickness = 0;
        if (random.nextInt(4) == 0) {
            thickness++;
        }
        if (random.nextInt(8) == 0) {
            thickness++;
        }
        for (int xThickness = -thickness; xThickness <= thickness; xThickness++) {
            for (int zThickness = -thickness; zThickness <= thickness; zThickness++) {
                straightnessIterator = 0;
                xOffset = 0;
                zOffset = 0;
                for (int yOffset = 0; yOffset < height; yOffset++) {
                    straightnessIterator++;
                    int combinedX = x + xOffset + xThickness;
                    int combinedY = y + yOffset;
                    int combinedZ = z + zOffset + zThickness;
                    if (straightnessIterator >= straightness) {
                        xOffset += xTilt;
                        zOffset += zTilt;
                        straightnessIterator = 0;
                    }
                    if (!world.isAirBlock(combinedX, combinedY, combinedZ) && world.getBlock(combinedX, combinedY, combinedZ) != crystalBlock) {
                        break;
                    }
                    setBlockFast(world, combinedX, combinedY, combinedZ, crystalBlock, 0);
                }
            }
        }
    }
}
