package com.gtnewhorizons.galaxia.registry.dimension.worldgen;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.world.World;

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
        int xStraightness = random.nextInt(4) + 1;
        int zStraightness = random.nextInt(4) + 1;
        int xStraightnessIterator;
        int zStraightnessIterator;
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
                xStraightnessIterator = 0;
                zStraightnessIterator = 0;
                xOffset = 0;
                zOffset = 0;
                for (int yOffset = 0; yOffset < height; yOffset++) {
                    xStraightnessIterator++;
                    zStraightnessIterator++;
                    int combinedX = x + xOffset + xThickness;
                    int combinedY = y + yOffset;
                    int combinedZ = z + zOffset + zThickness;
                    if (xStraightnessIterator >= xStraightness) {
                        xOffset += xTilt;
                        xStraightnessIterator = 0;
                    }
                    if (zStraightnessIterator >= zStraightness) {
                        zOffset += zTilt;
                        zStraightnessIterator = 0;
                    }
                    if (!world.isAirBlock(combinedX, combinedY, combinedZ)
                        && world.getBlock(combinedX, combinedY, combinedZ) != crystalBlock) {
                        break;
                    }
                    setBlockFast(world, combinedX, combinedY, combinedZ, crystalBlock, 0);
                }
            }
        }
    }
}
