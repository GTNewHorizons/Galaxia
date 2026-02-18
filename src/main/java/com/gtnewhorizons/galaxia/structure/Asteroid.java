package com.gtnewhorizons.galaxia.structure;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
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
        this.craterRarity = Math.max(1, craterRarity);
    }

    @Override
    public boolean generate(World world, Random random, int baseX, int baseY, int baseZ) {
        long startTotal = System.nanoTime();

        if (random.nextInt(rarity) > 0) return false;

        int size = minimumSize + (maximumSize > minimumSize ? random.nextInt(maximumSize - minimumSize) : 0);
        int radius = size / 2;
        int diameter = size + 4;
        int offset = diameter / 2;

        System.out.printf(
            "[Asteroid] Generating at (%d,%d,%d) | size=%d | radius=%d | diameter=%d%n",
            baseX,
            baseY,
            baseZ,
            size,
            radius,
            diameter);

        long startPrep = System.nanoTime();

        int interpCount = size / 2 + 1;
        interpCount *= Math.max(interpCount / 10, 1);
        interpCount *= Math.max(interpCount / 20, 1);
        int interpRange = size / 4 + 1;

        float[] interpValues = new float[interpCount];
        for (int i = 0; i < interpValues.length; i++) {
            interpValues[i] = random.nextFloat() / 4 + 0.75F;
        }

        int[][] interpPos = new int[interpCount][];
        interpPos[0] = new int[] { baseX, baseY, baseZ };

        for (int i = 1; i < interpPos.length; i++) {
            int ox = random.nextInt(interpRange) + 1;
            if (random.nextBoolean()) ox = -ox;
            int oy = random.nextInt(interpRange) + 1;
            if (random.nextBoolean()) oy = -oy;
            int oz = random.nextInt(interpRange) + 1;
            if (random.nextBoolean()) oz = -oz;
            interpPos[i] = new int[] { baseX + ox, baseY + oy, baseZ + oz };
        }

        long prepMs = (System.nanoTime() - startPrep) / 1_000_000;
        long startMem = System.nanoTime();

        byte[][][] data = new byte[diameter][diameter][diameter];

        for (int lx = 0; lx < diameter; lx++) {
            for (int ly = 0; ly < diameter; ly++) {
                for (int lz = 0; lz < diameter; lz++) {
                    int wx = baseX + lx - offset;
                    int wy = baseY + ly - offset;
                    int wz = baseZ + lz - offset;

                    if (calculateFullness(interpPos, interpValues, wx, wy, wz) > 1) {
                        data[lx][ly][lz] = (byte) (1 + random.nextInt(blockPalette.length));
                    }
                }
            }
        }

        int craterCount = Math.min(8, 2 + size / (craterRarity * 4));
        for (int i = 0; i < craterCount; i++) {
            carveCraterInMemory(data, random, diameter, offset, craterRarity);
        }

        long memMs = (System.nanoTime() - startMem) / 1_000_000;

        long startPlace = System.nanoTime();
        int blocksPlaced = 0;

        for (int lx = 0; lx < diameter; lx++) {
            int wx = baseX + lx - offset;
            for (int ly = 0; ly < diameter; ly++) {
                int wy = baseY + ly - offset;
                for (int lz = 0; lz < diameter; lz++) {
                    byte val = data[lx][ly][lz];
                    if (val > 0) {
                        BlockMeta bm = blockPalette[val - 1];
                        setBlockFast(world, wx, wy, baseZ + lz - offset, bm.block(), bm.meta());
                        blocksPlaced++;
                    }
                }
            }
        }

        long placeMs = (System.nanoTime() - startPlace) / 1_000_000;
        long totalMs = (System.nanoTime() - startTotal) / 1_000_000;

        System.out
            .printf("[Asteroid] Prep: %d ms | Memory gen + craters: %d ms | Craters: %d%n", prepMs, memMs, craterCount);
        System.out.printf("[Asteroid] EBS Placement: %d ms | blocks placed: %d%n", placeMs, blocksPlaced);
        System.out.printf("[Asteroid] TOTAL: %d ms | coord (%d,%d,%d)%n%n", totalMs, baseX, baseY, baseZ);

        return true;
    }

    private void setBlockFast(World world, int x, int y, int z, Block block, int meta) {
        if (y < 0 || y > 255) return;

        Chunk chunk = world.getChunkFromChunkCoords(x >> 4, z >> 4);
        ExtendedBlockStorage[] storage = chunk.getBlockStorageArray();
        int sectionY = y >> 4;

        ExtendedBlockStorage ebs = storage[sectionY];
        if (ebs == null) {
            ebs = storage[sectionY] = new ExtendedBlockStorage(sectionY << 4, !world.provider.hasNoSky);
        }

        int lx = x & 15;
        int ly = y & 15;
        int lz = z & 15;

        ebs.func_150818_a(lx, ly, lz, block);
        ebs.setExtBlockMetadata(lx, ly, lz, meta);
        chunk.isModified = true;
    }

    private void carveCraterInMemory(byte[][][] data, Random rand, int diameter, int offset, int craterRarity) {
        int cx = rand.nextInt(diameter);
        int cy = rand.nextInt(diameter);
        int cz = rand.nextInt(diameter);

        double r = 4 + rand.nextDouble() * (diameter * 0.18 / craterRarity);
        double rSq = r * r;

        for (int x = 0; x < diameter; x++) {
            for (int y = 0; y < diameter; y++) {
                for (int z = 0; z < diameter; z++) {
                    if (data[x][y][z] == 0) continue;
                    double dx = x - cx;
                    double dy = y - cy;
                    double dz = z - cz;
                    double distSq = dx * dx + dy * dy + dz * dz;
                    if (distSq < rSq * (1.0 - rand.nextDouble() * 0.3)) {
                        data[x][y][z] = 0;
                    }
                }
            }
        }
    }

    private float calculateFullness(int[][] positions, float[] values, int x, int y, int z) {
        float fullness = 0;
        for (int i = 0; i < values.length; i++) {
            fullness += values[i] * calculateInterpolationSignificance(positions[i], x, y, z);
            if (fullness > 1) return fullness;
        }
        return fullness;
    }

    private float calculateInterpolationSignificance(int[] loc, int x, int y, int z) {
        int dx = Math.abs(loc[0] - x);
        if (dx > 16) return 0;
        int dy = Math.abs(loc[1] - y);
        if (dy > 16) return 0;
        int dz = Math.abs(loc[2] - z);
        if (dz > 16) return 0;
        float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        return 1 / (dist + 1);
    }
}
