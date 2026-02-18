package com.gtnewhorizons.galaxia.structure;

import java.util.Random;

import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.gen.feature.WorldGenerator;

import com.gtnewhorizons.galaxia.utility.BlockMeta;

public class Asteroid extends WorldGenerator {

    private final int minimumSize;
    private final int maximumSize;
    private final int rarity;
    private final BlockMeta[] blockPalette;
    private final int craterRarity;

    public Asteroid(int minimumSize, int maximumSize, int rarity, BlockMeta[] blockPalette, int craterRarity) {
        this.minimumSize = minimumSize;
        this.maximumSize = maximumSize;
        this.rarity = rarity;
        this.blockPalette = blockPalette;
        this.craterRarity = craterRarity;
    }

    @Override
    public boolean generate(World world, Random random, int x, int y, int z) {
        if (random.nextInt(rarity) > 0) {
            return false;
        }

        // Calculate size
        int size = minimumSize;
        int variation = maximumSize - minimumSize;
        if (variation > 0) {
            size += random.nextInt(variation);
        }

        // Generate interpolation points
        int interpolationComplexity = size / 2 + 1;
        interpolationComplexity *= Math.max(interpolationComplexity/10, 1);
        interpolationComplexity *= Math.max(interpolationComplexity/20, 1);
        int interpolationRange = size / 4 + 1;
        float[] interpolationValues = new float[interpolationComplexity];
        for (int value = 0; value < interpolationValues.length; value++) {
            interpolationValues[value] = random.nextFloat() / 4 + 0.75F;
        }
        int[][] interpolationPositions = new int[interpolationComplexity][];
        interpolationPositions[0] = new int[] { x, y, z };
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
            interpolationPositions[index] = new int[] { x + xOffset, y + yOffset, z + zOffset };
        }

        // Generate basic shape
        int radius = size / 2;
        for (int xOffset = -radius; xOffset <= radius; xOffset++) {
            for (int yOffset = -radius; yOffset <= radius; yOffset++) {
                for (int zOffset = -radius; zOffset <= radius; zOffset++) {
                    float fullness = calculateFullness(
                        interpolationPositions,
                        interpolationValues,
                        x + xOffset,
                        y + yOffset,
                        z + zOffset);
                    if (fullness > 1) {
                        world.setBlock(
                            x + xOffset,
                            y + yOffset,
                            z + zOffset,
                            Blocks.stone,
                            0,
                            2);
                    }
                }
            }
        }

        // Carve craters
        int craterDistance = radius - radius/4;
        int maximumCraterSize = size/8 + 1;
        int craterCount = random.nextInt((size*size)/craterRarity + 1);
        for (int crater = 0; crater < craterCount; crater++) {
            int craterSize = random.nextInt(1 + maximumCraterSize) + 1;
            int distantCoordinate = random.nextInt(3);
            int xOffset = getCraterDistance(random, craterDistance, size, 0, distantCoordinate);
            int yOffset = getCraterDistance(random, craterDistance, size, 1, distantCoordinate);
            int zOffset = getCraterDistance(random, craterDistance, size, 2, distantCoordinate);
            for (int xCrater = -craterSize; xCrater <= craterSize; xCrater++) {
                for (int yCrater = -craterSize; yCrater <= craterSize; yCrater++) {
                    for (int zCrater = -craterSize; zCrater <= craterSize; zCrater++) {
                        if (Math.abs(xCrater + xOffset) > radius) {
                            continue;
                        }
                        if (Math.abs(yCrater + yOffset) > radius) {
                            continue;
                        }
                        if (Math.abs(zCrater + zOffset) > radius) {
                            continue;
                        }
                        if (world.getBlock(x + xOffset + xCrater, y + yOffset + yCrater, z + zOffset + zCrater) != Blocks.stone) {
                            continue;
                        }
                        double centerDistance = Math.sqrt(xCrater*xCrater + yCrater*yCrater + zCrater*zCrater);
                        if (centerDistance > craterSize) {
                            continue;
                        }
                        world.setBlock(x + xOffset + xCrater, y + yOffset + yCrater, z + zOffset + zCrater, Blocks.air);
                    }
                }
            }
        }

        // Replace blocks
        for (int xOffset = -radius; xOffset <= radius; xOffset++) {
            for (int yOffset = -radius; yOffset <= radius; yOffset++) {
                for (int zOffset = -radius; zOffset <= radius; zOffset++) {
                    if (world.getBlock(x + xOffset, y + yOffset, z + zOffset) == Blocks.stone) {
                        BlockMeta pickedBlock = blockPalette[random.nextInt(blockPalette.length)];
                        world.setBlock(
                            x + xOffset,
                            y + yOffset,
                            z + zOffset,
                            pickedBlock.block(),
                            pickedBlock.meta(),
                            2);
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
        float totalDistance = (float) Math.sqrt(xDistance * xDistance + yDistance * yDistance + zDistance * zDistance);
        return 1 / (totalDistance + 1);
    }

    private int getCraterDistance(Random random, int craterDistance, int size, int axis, int longAxis) {
        if (axis == longAxis) {
            return getLongCraterDistance(random, craterDistance, size);
        }
        return getShortCraterDistance(random, craterDistance);
    }

    private int getShortCraterDistance(Random random, int craterDistance) {
        int distance = random.nextInt(1 + craterDistance);
        if (random.nextBoolean()) {
            distance *= -1;
        }
        return distance;
    }

    private int getLongCraterDistance(Random random, int craterDistance, int size) {
        int distance = craterDistance + random.nextInt( size/16 + 1);
        if (random.nextBoolean()) {
            distance *= -1;
        }
        return distance;
    }
}
