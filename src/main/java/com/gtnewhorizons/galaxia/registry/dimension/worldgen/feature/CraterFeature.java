package com.gtnewhorizons.galaxia.registry.dimension.worldgen.feature;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;

import com.gtnewhorizons.galaxia.registry.dimension.worldgen.GalaxiaPlanetGenerator;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.HeightOracle;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.math.WorldgenRandom;

import lombok.Builder;

@Builder
public class CraterFeature implements SurfaceFeature {

    @Builder.Default
    private int rarity = CraterSize.GENERATION_RARITY;
    private final Block tektite;
    private final BlockPredicate condition;

    /// Seed random. Used for controlling major parameters for craters.
    private final WorldgenRandom seedRand = new WorldgenRandom();
    /// Non-deterministic random. Used for minor details like the rim debris.
    private final WorldgenRandom nondetRand = new WorldgenRandom();

    private record CraterSeed(String id, int x, int y, int z, int diameter, CraterSize size, CraterProfile profile,
        long reserve) {}

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

        CraterSize size = CraterSize.select(seedRand);
        int diameter = size.sampleDiameter(seedRand);
        CraterProfile profile = CraterProfile.select(size, seedRand);
        long reserve = profile.initialReserve(size, diameter, seedRand);
        return new CraterSeed(cx + ":" + cz, wx, surface, wz, diameter, size, profile, reserve);
    }

    @Override
    public void generateSurfaceFeature(World world, GalaxiaPlanetGenerator generator, int cx, int cz) {
        int reach = CraterSize.MAX_DIAMETER / 32 + 1;
        for (int seedX = -reach; seedX <= reach; seedX++) {
            for (int seedZ = -reach; seedZ <= reach; seedZ++) {
                CraterSeed seed = getSeed(world, generator, seedX + cx, seedZ + cz);

                if (seed != null) {
                    int radius = (seed.diameter + 1) / 2;
                    if (seed.x + radius < (cx << 4) || seed.x - radius > (cx << 4) + 15
                        || seed.z + radius < (cz << 4)
                        || seed.z - radius > (cz << 4) + 15) continue;
                    CraterDeposits.get(world)
                        .register(seed.id, seed.x, seed.z, seed.diameter, seed.size, seed.profile, seed.reserve);
                    generateSeed(world, generator, cx, cz, seed);
                }
            }
        }
    }

    private void generateSeed(World world, GalaxiaPlanetGenerator generator, int cx, int cz, CraterSeed seed) {
        int radius = seed.diameter / 2;
        int squaredCraterRadius = radius * radius;
        double rimWidth = Math.max(1.0, radius / 16.0);

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
                int surface = world.getHeightValue(wx, wz);
                double surfaceOffset = surface - y;
                double squaredMouthRadius = squaredCraterRadius - surfaceOffset * surfaceOffset;

                for (int localY = -radius; localY <= radius; localY++) {
                    int wy = y + localY;

                    if (world.isAirBlock(wx, wy, wz)) continue;

                    double squaredDistance = rimDistance + localY * localY;

                    if (squaredDistance < squaredCraterRadius * (1.0 - nondetRand.nextDouble() * 0.1)) {
                        generator.setBlockSafe(wx, wy, wz, Blocks.air, 0);
                    }
                }

                if (squaredMouthRadius > 0) {
                    double mouthRadius = Math.sqrt(squaredMouthRadius);
                    double rimInner = Math.max(0, mouthRadius - rimWidth * nondetRand.nextDouble());
                    double rimOuter = Math.min(radius, mouthRadius + rimWidth * nondetRand.nextDouble());
                    if (rimDistance >= rimInner * rimInner && rimDistance < rimOuter * rimOuter) {
                        // Follow the carved surface and avoid stacking debris from overlapping craters.
                        int rimY = world.getHeightValue(wx, wz);
                        if (world.isAirBlock(wx, rimY, wz) && world.getBlock(wx, rimY - 1, wz) != tektite) {
                            generator.setBlockSafe(wx, rimY, wz, tektite, 0);
                        }
                    }
                }
            }
        }
    }
}
