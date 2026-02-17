package com.gtnewhorizons.galaxia.structure;

import net.minecraft.block.Block;
import net.minecraft.world.World;
import net.minecraft.world.gen.feature.WorldGenerator;

import java.util.Random;

public class Asteroid extends WorldGenerator {
    private final int minimumSize;
    private final int maximumSize;
    private final int rarity;
    private final Block fillerBlock;
    private final int fillerBlockMeta;

    public Asteroid(int minimumSize, int maximumSize, int rarity, Block fillerBlock, int fillerBlockMeta) {
        this.minimumSize = minimumSize;
        this.maximumSize = maximumSize;
        this.rarity = rarity;
        this.fillerBlock = fillerBlock;
        this.fillerBlockMeta = fillerBlockMeta;
    }

    @Override
    public boolean generate(World world, Random random, int x, int y, int z) {
        if (random.nextInt(rarity) > 0) {
            return false;
        }

        int size = minimumSize;
        int variation = maximumSize - minimumSize;
        if (variation > 0) {
            size += random.nextInt(variation);
        }

        int interpolationComplexity = size / 2 + 1;
        int interpolationRange = size / 4 + 1;
        float[] interpolationValues = new float[interpolationComplexity];
        for (int value = 0; value < interpolationValues.length; value++) {
            interpolationValues[value] = random.nextFloat()/4 + 0.75F;
        }
        int[][] interpolationPositions = new int[interpolationComplexity][];
        interpolationPositions[0] = new int[] {x, y, z};
        for (int index = 1; index < interpolationPositions.length; index++) {
            int xOffset = random.nextInt(interpolationRange) + 1;
            if (random.nextBoolean()) {
                xOffset *= -1;
            }
            int yOffset = random.nextInt(interpolationRange) + 1;
            if (random.nextBoolean()) {
                yOffset *= -1;
            }
            int zOffset = random.nextInt(interpolationRange) + 1;
            if (random.nextBoolean()) {
                zOffset *= -1;
            }
            interpolationPositions[index] = new int[] {x + xOffset, y + yOffset, z + zOffset};
        }
        int radius = size / 2;
        for (int xOffset = -radius; xOffset <= radius; xOffset++) {
            for (int yOffset = -radius; yOffset <= radius; yOffset++) {
                for (int zOffset = -radius; zOffset <= radius; zOffset++) {
                    float fullness = calculateFullness(interpolationPositions, interpolationValues, x + xOffset, y + yOffset, z + zOffset);
                    if (fullness > 1) {
                        world.setBlock(x + xOffset, y + yOffset, z + zOffset, fillerBlock, fillerBlockMeta, 2);
                    }
                }
            }
        }
        return true;
    }

    private float calculateFullness(int[][] interpolationPositions, float[] interpolationValues, int x, int y, int z) {
        float fullness = 0;
        for (int interpolation = 0; interpolation < interpolationValues.length; interpolation++) {
            fullness += interpolationValues[interpolation]
                * calculateInterpolationSignificance(interpolationPositions[interpolation], x, y, z);
            if (fullness > 1) {
                return fullness;
            }
        }
        return fullness;
    }

    private float calculateInterpolationSignificance(int[] interpolationLocation, int x, int y, int z) {
        int xDistance = Math.abs(interpolationLocation[0] - x);
        if (xDistance > 16) {
            return 0;
        }
        int yDistance = Math.abs(interpolationLocation[1] - y);
        if (yDistance > 16) {
            return 0;
        }
        int zDistance = Math.abs(interpolationLocation[2] - z);
        if (zDistance > 16) {
            return 0;
        }
        float totalDistance = (float) Math.sqrt(xDistance*xDistance + yDistance*yDistance + zDistance*zDistance);
        return 1 / (totalDistance + 1);
    }
}
