package com.gtnewhorizons.galaxia.registry.dimension.worldgen.feature;

import static net.minecraftforge.common.util.ForgeDirection.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.world.World;

import net.minecraftforge.common.util.ForgeDirection;

import org.joml.Vector3i;

import com.gtnewhorizon.structurelib.alignment.IntegerAxisSwap;
import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;
import com.gtnewhorizon.structurelib.util.Vec3Impl;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.GalaxiaPlanetGenerator;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.math.WorldgenRandom;
import lombok.Builder;

@Builder
public class CrystalClusterFeature implements UndergroundFeature {

    @Builder.Default
    private final int rarity = 1;
    @Builder.Default
    private final int tries = 32;
    @Builder.Default
    private int minHeight = Integer.MIN_VALUE;
    @Builder.Default
    private int maxHeight = Integer.MAX_VALUE;
    private final Block baseBlock;
    private final Block crystalBlock;
    private final BlockPredicate condition;

    private final WorldgenRandom rand = new WorldgenRandom();

    private static final ForgeDirection[] SCAN_ORDER = { UP, NORTH, SOUTH, EAST, WEST, DOWN };

    @Override
    public void generateUndergroundFeature(World world, GalaxiaPlanetGenerator generator, int cx, int cy, int cz) {
        rand.prime(world, cx, cz, 123123, cy);

        if (rand.nextInt(rarity) != 0) return;

        for (int i = 0; i < tries; i++) {
            int wx = (cx << 4) + rand.nextInt(16) - 8;
            int wy = (cy << 4) + rand.nextInt(16);
            int wz = (cz << 4) + rand.nextInt(16) - 8;

            if (wy < minHeight) continue;
            if (wy > maxHeight) continue;

            if (!condition.test(world, wx, wy, wz)) continue;

            int air = 0;

            for (ForgeDirection dir : SCAN_ORDER) {
                if (world.isAirBlock(wx + dir.offsetX, wy + dir.offsetY, wz + dir.offsetZ)) air++;
            }

            // There has to be one and only one block of air next to the crystal base
            if (air != 1) continue;

            for (ForgeDirection dir : SCAN_ORDER) {
                if (world.isAirBlock(wx + dir.offsetX, wy + dir.offsetY, wz + dir.offsetZ)) {
                    int height = rand.nextInt(4) + 4;

                    // Generate a random number of arms
                    for (int crystalCount = 0; crystalCount < rand.nextInt(6) + 4; crystalCount++) {
                        generateCrystal(world, generator, wx, wy, wz, height, dir);
                    }

                    // Generate the crystal base
                    for (int dX = -1; dX <= 1; dX++) {
                        for (int dY = -1; dY <= 1; dY++) {
                            for (int dZ = -1; dZ <= 1; dZ++) {
                                if (world.isAirBlock(wx + dX, wy + dY, wz + dZ)) {
                                    generator.setBlockSafe(wx + dX, wy + dY, wz + dZ, crystalBlock, 0);
                                }
                            }
                        }
                    }

                    return;
                }
            }
        }
    }

    private void generateCrystal(World world, GalaxiaPlanetGenerator generator, int x, int y, int z, int heightBias, ForgeDirection dir) {
        int oX = dir.offsetX;
        int oY = dir.offsetY;
        int oZ = dir.offsetZ;

        int targetX = rand.nextInt(7) - 3 + oX * heightBias;
        int targetY = rand.nextInt(7) - 3 + oY * heightBias;
        int targetZ = rand.nextInt(7) - 3 + oZ * heightBias;

        getLineVoxels(oX, oY, oZ, targetX, targetY, targetZ, (x1, y1, z1) -> {
            int wX = x + x1;
            int wY = y + y1;
            int wZ = z + z1;

            if (!ChunkBoundedAccess.isAirBlock(world, wX, wY, wZ)
                && ChunkBoundedAccess.getBlock(world, wX, wY, wZ) != crystalBlock) {
                return false;
            }

            generator.setBlockSafe(wX, wY, wZ, crystalBlock, 0);
            return true;
        });
    }

    interface VoxelConsumer {
        boolean accept(int x, int y, int z);
    }

    private static void getLineVoxels(int x1, int y1, int z1, int x2, int y2, int z2, VoxelConsumer fn) {
        int dx = Math.abs(x1 - x2), dy = Math.abs(y1 - y2), dz = Math.abs(z1 - z2);
        int sx = x1 < x2 ? 1 : -1, sy = y1 < y2 ? 1 : -1, sz = z1 < z2 ? 1 : -1;

        if (!fn.accept(x1, y1, z1)) return;

        if (dx >= dy && dx >= dz) {
            int p1 = 2 * dy - dx;
            int p2 = 2 * dz - dx;

            while (x1 != x2) {
                x1 += sx;

                if (p1 >= 0) {
                    y1 += sy;
                    p1 -= 2 * dx;
                }
                if (p2 >= 0) {
                    z1 += sz;
                    p2 -= 2 * dx;
                }

                p1 += 2 * dy;
                p2 += 2 * dz;

                if (!fn.accept(x1, y1, z1)) return;
            }
        } else if (dy >= dx && dy >= dz) {
            int p1 = 2 * dx - dy;
            int p2 = 2 * dz - dy;

            while (y1 != y2) {
                y1 += sy;

                if (p1 >= 0) {
                    x1 += sx;
                    p1 -= 2 * dy;
                }
                if (p2 >= 0) {
                    z1 += sz;
                    p2 -= 2 * dy;
                }

                p1 += 2 * dx;
                p2 += 2 * dz;

                if (!fn.accept(x1, y1, z1)) return;
            }
        } else {
            int p1 = 2 * dy - dz;
            int p2 = 2 * dx - dz;

            while (z1 != z2) {
                z1 += sz;

                if (p1 >= 0) {
                    y1 += sy;
                    p1 -= 2 * dz;
                }
                if (p2 >= 0) {
                    x1 += sx;
                    p2 -= 2 * dz;
                }

                p1 += 2 * dy;
                p2 += 2 * dx;

                if (!fn.accept(x1, y1, z1)) return;
            }
        }
    }
}
