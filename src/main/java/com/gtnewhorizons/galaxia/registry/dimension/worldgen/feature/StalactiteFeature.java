package com.gtnewhorizons.galaxia.registry.dimension.worldgen.feature;

import net.minecraft.block.Block;
import net.minecraft.world.World;

import com.gtnewhorizons.galaxia.registry.dimension.worldgen.GalaxiaPlanetGenerator;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.math.WorldgenRandom;

import lombok.Builder;

@Builder
public class StalactiteFeature implements UndergroundFeature {

    @Builder.Default
    private int gridDivisor = 3;
    @Builder.Default
    private int rarity = 4;
    @Builder.Default
    private int tries = 4;
    @Builder.Default
    private int minHeight = Integer.MIN_VALUE;
    @Builder.Default
    private int maxHeight = Integer.MAX_VALUE;
    private final Block stalactiteBlock;
    private final BlockPredicate condition;

    private final WorldgenRandom rand = new WorldgenRandom();

    @Override
    public void generateUndergroundFeature(World world, GalaxiaPlanetGenerator generator, int cx, int cy, int cz) {
        rand.prime(world, cx, cz, 56454, cy);

        int gridWidth = 1 << gridDivisor;

        for (int gX = 0; gX < gridDivisor; gX++) {
            for (int gY = 0; gY < gridDivisor; gY++) {
                for (int gZ = 0; gZ < gridDivisor; gZ++) {
                    if (rand.nextInt(rarity) != 0) continue;

                    for (int i = 0; i < tries; i++) {
                        int wX = (cx << 4) + gridWidth * gX + rand.nextInt(gridWidth);
                        int wY = (cy << 4) + gridWidth * gY + rand.nextInt(gridWidth);
                        int wZ = (cz << 4) + gridWidth * gZ + rand.nextInt(gridWidth);

                        if (wY < minHeight) continue;
                        if (wY > maxHeight) continue;

                        if (!condition.test(world, wX, wY - 1, wZ)) continue;
                        if (!world.isAirBlock(wX, wY, wZ)) continue;

                        int height = rand.nextInt(8) + 1;

                        for (int yOffset = 0; yOffset < height; yOffset++) {
                            if (!world.isAirBlock(wX, wY + yOffset, wZ)) {
                                break;
                            }
                            generator.setBlockSafe(wX, wY + yOffset, wZ, stalactiteBlock, 0);
                        }
                    }
                }
            }
        }
    }
}
