package com.gtnewhorizons.galaxia.worldgen;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraft.world.gen.feature.WorldGenerator;

import com.gtnewhorizons.galaxia.utility.BlockMeta;

/**
 * World Generator for Asteroids
 */
public class Asteroid extends WorldGenerator {

    private final int minimumSize;
    private final int maximumSize;
    private final int rarity;
    private final BlockMeta[] blockPalette;
    private final int craterFrequency;

    /**
     * Constructor to create a base world gen
     *
     * @param minimumSize     Minimum asteroid radius
     * @param maximumSize     Maximum asteroid radius
     * @param rarity          Sparsity of asteroids in worldgen
     * @param blockPalette    Block paletter to be used in creation
     * @param craterFrequency Frequency of craters on asteroids
     */
    public Asteroid(int minimumSize, int maximumSize, int rarity, BlockMeta[] blockPalette, int craterFrequency) {
        this.minimumSize = minimumSize;
        this.maximumSize = maximumSize;
        this.rarity = rarity;
        this.blockPalette = blockPalette;
        this.craterFrequency = Math.max(1, craterFrequency);
    }

    /**
     * Generates an asteroid based on coordinates
     *
     * @param world  The world to create in
     * @param random Holds a Random instance
     * @param x      The asteroid's x origin
     * @param y      The asteroid's y origin
     * @param z      The asteroid's z origin
     * @return Boolean : True => Successful generation
     */
    @Override
    public boolean generate(World world, Random random, int x, int y, int z) {
        if (random.nextInt(rarity) > 0) return false;

        // Calculate relevant size values
        int size = minimumSize + (maximumSize > minimumSize ? random.nextInt(maximumSize - minimumSize) : 0);
        int diameter = size + 4;
        int halfDiameter = diameter / 2;

        // Determine number of interpolation points
        int interpolationCount = size / 2 + 1;
        interpolationCount *= Math.max(interpolationCount / 10, 1);
        interpolationCount *= Math.max(interpolationCount / 20, 1);
        int interpolationRange = size / 4 + 1;

        // Set values for interpolation points
        float[] interpolationValues = new float[interpolationCount];
        for (int i = 0; i < interpolationValues.length; i++) {
            interpolationValues[i] = random.nextFloat() / 4 + 0.75F;
        }

        // Set positions for interpolation points
        int[][] interpolationPositions = new int[interpolationCount][];
        interpolationPositions[0] = new int[] { x, y, z };
        for (int i = 1; i < interpolationPositions.length; i++) {
            int offsetX = random.nextInt(interpolationRange) + 1;
            if (random.nextBoolean()) offsetX = -offsetX;
            int offsetY = random.nextInt(interpolationRange) + 1;
            if (random.nextBoolean()) offsetY = -offsetY;
            int offsetZ = random.nextInt(interpolationRange) + 1;
            if (random.nextBoolean()) offsetZ = -offsetZ;
            interpolationPositions[i] = new int[] { x + offsetX, y + offsetY, z + offsetZ };
        }

        // Calculate basic shape
        byte[][][] blockData = new byte[diameter][diameter][diameter];
        for (int localX = 0; localX < diameter; localX++) {
            for (int localY = 0; localY < diameter; localY++) {
                for (int localZ = 0; localZ < diameter; localZ++) {
                    int combinedX = x + localX - halfDiameter;
                    int combinedY = y + localY - halfDiameter;
                    int combinedZ = z + localZ - halfDiameter;

                    if (calculateFullness(interpolationPositions, interpolationValues, combinedX, combinedY, combinedZ)
                        > 1) {
                        blockData[localX][localY][localZ] = (byte) (1 + random.nextInt(blockPalette.length));
                    }
                }
            }
        }

        // Add craters to shape
        int craterCount = Math.max(8, 2 + size * craterFrequency);
        for (int i = 0; i < craterCount; i++) {
            carveCraterInMemory(blockData, random, diameter);
        }

        // Convert block data into placed blocks
        for (int localX = 0; localX < diameter; localX++) {
            int combinedX = x + localX - halfDiameter;
            for (int localY = 0; localY < diameter; localY++) {
                int combinedY = y + localY - halfDiameter;
                for (int localZ = 0; localZ < diameter; localZ++) {
                    int combinedZ = z + localZ - halfDiameter;
                    byte localBlockValue = blockData[localX][localY][localZ];
                    if (localBlockValue > 0) {
                        BlockMeta blockMeta = blockPalette[localBlockValue - 1];
                        setBlockFast(world, combinedX, combinedY, combinedZ, blockMeta.block(), blockMeta.meta());
                    }
                }
            }
        }

        return true;
    }

    /**
     * Sets block in the world at set coordinates
     *
     * @param world The world to place the block in
     * @param x     Target x coordinate
     * @param y     Target y coordinate
     * @param z     Target z coordinate
     * @param block The block to place
     * @param meta  Metadata of the block to place
     */
    private void setBlockFast(World world, int x, int y, int z, Block block, int meta) {
        if (y < 0 || y > 255) return;

        Chunk chunk = world.getChunkFromChunkCoords(x >> 4, z >> 4);
        ExtendedBlockStorage[] storage = chunk.getBlockStorageArray();
        int sectionY = y >> 4;

        ExtendedBlockStorage currentBlockStorage = storage[sectionY];
        if (currentBlockStorage == null) {
            currentBlockStorage = storage[sectionY] = new ExtendedBlockStorage(sectionY << 4, !world.provider.hasNoSky);
        }

        int lx = x & 15;
        int ly = y & 15;
        int lz = z & 15;

        currentBlockStorage.func_150818_a(lx, ly, lz, block);
        currentBlockStorage.setExtBlockMetadata(lx, ly, lz, meta);
        chunk.isModified = true;
    }

    /**
     * Carves craters by generating a sphere used to cut out sections of the asteroid
     * This process happens entirely within the data-generation stage
     *
     * @param blockData Existing asteroid block data
     * @param random    Randomizer used to roughen the crater's outline
     * @param diameter  Diameter of the crater
     */
    private void carveCraterInMemory(byte[][][] blockData, Random random, int diameter) {
        int longAxis = random.nextInt(3);
        int craterX = getCraterDistance(random, diameter, 0, longAxis);
        int craterY = getCraterDistance(random, diameter, 1, longAxis);
        int craterZ = getCraterDistance(random, diameter, 2, longAxis);

        double craterRadius = 4 + random.nextDouble() * ((double) diameter / 8);
        double squaredCraterRadius = craterRadius * craterRadius;

        for (int localX = 0; localX < diameter; localX++) {
            for (int localY = 0; localY < diameter; localY++) {
                for (int localZ = 0; localZ < diameter; localZ++) {
                    if (blockData[localX][localY][localZ] == 0) continue;
                    double combinedX = localX - craterX;
                    double combinedY = localY - craterY;
                    double combinedZ = localZ - craterZ;
                    double squaredDistance = combinedX * combinedX + combinedY * combinedY + combinedZ * combinedZ;
                    if (squaredDistance < squaredCraterRadius * (1.0 - random.nextDouble() * 0.3)) {
                        blockData[localX][localY][localZ] = 0;
                    }
                }
            }
        }
    }

    /**
     * Calculates whether a block should be placed at a specific coordinate
     * Adds all relevant interpolation values together to determine fullness
     * Location counts as valid if fullness reaches one
     *
     * @param positions Coordinates of all interpolation values
     * @param values    Values of interpolation points
     * @param x         Target x coordinate
     * @param y         Target y coordinate
     * @param z         Target z coordinate
     * @return Fullness value ranging between zero and one
     */
    private float calculateFullness(int[][] positions, float[] values, int x, int y, int z) {
        float fullness = 0;
        for (int i = 0; i < values.length; i++) {
            fullness += values[i] * calculateInterpolationSignificance(positions[i], x, y, z);
            if (fullness > 1) return fullness;
        }
        return fullness;
    }

    /**
     * Calculates how relevant an interpolation point is based on distance
     *
     * @param coordinates Location of the interpolation point
     * @param x           Target x coordinate
     * @param y           Target y coordinate
     * @param z           Target z coordinate
     * @return Significance multiplier between zero (exclusive) and one (inclusive)
     */
    private float calculateInterpolationSignificance(int[] coordinates, int x, int y, int z) {
        int xDistance = Math.abs(coordinates[0] - x);
        if (xDistance > 16) return 0;
        int yDistance = Math.abs(coordinates[1] - y);
        if (yDistance > 16) return 0;
        int zDistance = Math.abs(coordinates[2] - z);
        if (zDistance > 16) return 0;
        float distance = (float) Math.sqrt(xDistance * xDistance + yDistance * yDistance + zDistance * zDistance);
        return 1 / (distance + 1);
    }

    /**
     * Calculates a suitable distance value from the center of the asteroid along a single axis
     * Can calculate position variation on the asteroid's surface or distance from the center
     *
     * @param random         Randomizer needed for variation in the results
     * @param craterDistance Distance from the center
     * @param axis           Determines which axis is being calculated (0 = x, 1 = y, 2 = z)
     * @param longAxis       Axis which should determine distance from center
     *                       If the specified axis is not equal to this value, then it will be treated
     *                       as position variation on the asteroid's surface
     * @return Distance from the center
     */
    private int getCraterDistance(Random random, int craterDistance, int axis, int longAxis) {
        if (axis == longAxis) {
            return getLongCraterDistance(random, craterDistance);
        }
        return getShortCraterDistance(random, craterDistance);
    }

    /**
     * Calculates position variation on the asteroid's surface
     *
     * @param random         Randomizer needed for variation in the results
     * @param craterDistance Maximum distance from the center
     * @return Position variation value
     */
    private int getShortCraterDistance(Random random, int craterDistance) {
        return random.nextInt(1 + craterDistance);
    }

    /**
     * Calculates distance from the center of the asteroid
     *
     * @param random         Randomizer needed for variation in the results
     * @param craterDistance Minimum distance from the center
     * @return Distance from the center
     */
    private int getLongCraterDistance(Random random, int craterDistance) {
        if (random.nextBoolean()) {
            return random.nextInt(craterDistance / 16 + 1);
        }
        return craterDistance + random.nextInt(craterDistance / 16 + 1);
    }
}
