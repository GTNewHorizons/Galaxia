package com.gtnewhorizons.galaxia.registry.dimension.worldgen.feature;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;

import com.gtnewhorizon.gtnhlib.util.StdLCG;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.GalaxiaPlanetGenerator;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.HeightOracle;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.math.WorldgenRandom;
import lombok.Builder;

@Builder
public class CraterFeature implements SurfaceFeature {

    @Builder.Default
    private int rarity = 16;
    private final Block tektite;
    private final BlockPredicate condition;

    /// Seed random. Used for controlling major parameters for craters.
    private final WorldgenRandom seedRand = new WorldgenRandom();
    /// Non-deterministic random. Used for minor details like the rim debris.
    private final WorldgenRandom nondetRand = new WorldgenRandom();

    private record CraterSeed(int x, int y, int z, int diameter) { }

    private CraterSeed getSeed(World world, GalaxiaPlanetGenerator generator, int cx, int cz) {
        seedRand.prime(world, cx, cz, 123, 5678);

        if (seedRand.nextInt(rarity) != 0) return null;

        int dx = seedRand.nextInt(16);
        int dz = seedRand.nextInt(16);

        int wx = (cx << 4) + dx;
        int wz = (cz << 4) + dz;

        HeightOracle heightOracle = generator.getHeightOracle();

        int surface = heightOracle.getColumnHeight(wx, wz);

        var topBlock = heightOracle.getPredictedBlock(wx, surface - 1, wz);

        if (!condition.test(topBlock)) return null;

        return new CraterSeed(wx, surface, wz, 16 + seedRand.nextInt(16));
    }

    @Override
    public void generateSurfaceFeature(World world, GalaxiaPlanetGenerator generator, int cx, int cz) {
        for (int seedX = -2; seedX <= 2; seedX++) {
            for (int seedZ = -2; seedZ <= 2; seedZ++) {
                CraterSeed seed = getSeed(world, generator, seedX + cx, seedZ + cz);

                if (seed != null) {
                    generateSeed(world, generator, cx, cz, seed);
                }
            }
        }
    }

    private void generateSeed(World world, GalaxiaPlanetGenerator generator, int cx, int cz, CraterSeed seed) {
        int radius = seed.diameter / 2;
        int squaredCraterRadius = radius * radius;

        int x = seed.x;
        int y = seed.y + radius / 2;
        int z = seed.z;

        for (int localX = -radius; localX <= radius; localX++) {
            int wx = x + localX;

            // Don't generate stuff that's outside of the current chunk
            if ((wx >> 4) != cx) continue;

            for (int localZ = -radius; localZ <= radius; localZ++) {
                int wz = z + localZ;

                // Don't generate stuff that's outside of the current chunk
                if ((wz >> 4) != cz) continue;

                double rimDistance = localX * localX + localZ * localZ;

                double rimInner = squaredCraterRadius - nondetRand.nextInt(96);
                double rimOuter = squaredCraterRadius + nondetRand.nextInt(64);

                if (rimDistance >= rimInner && rimDistance < rimOuter) {
                    boolean wasBelowAir = world.isAirBlock(wx, y - 10, wz);

                    for (int rimY = -10; rimY <= 10; rimY++) {
                        if (world.getBlock(wx, y + rimY + 1, wz) == tektite) {
                            // 1 in 3 chance of aborting the scan per tektite already placed, to avoid large walls
                            if (nondetRand.nextInt(3) == 0) break;
                        }

                        boolean belowAir = world.isAirBlock(wx, y + rimY + 1, wz);

                        if (!wasBelowAir && belowAir) {
                            generator.setBlockSafe(wx, y + rimY + 1, wz, tektite, 0);
                            break;
                        }

                        wasBelowAir = belowAir;
                    }
                }

                for (int localY = -radius; localY <= radius; localY++) {
                    int wy = y + localY;

                    if (world.isAirBlock(wx, wy, wz)) continue;

                    double squaredDistance = rimDistance + localY * localY;

                    if (squaredDistance < squaredCraterRadius * (1.0 - nondetRand.nextDouble() * 0.1)) {
                        generator.setBlockSafe(wx, wy, wz, Blocks.air, 0);
                    }
                }
            }
        }
    }
}
