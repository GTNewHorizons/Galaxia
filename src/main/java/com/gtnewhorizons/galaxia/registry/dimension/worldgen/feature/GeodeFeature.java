package com.gtnewhorizons.galaxia.registry.dimension.worldgen.feature;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;

import com.gtnewhorizons.galaxia.registry.dimension.worldgen.GalaxiaPlanetGenerator;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.math.WorldgenRandom;

import lombok.Builder;

@Builder
public class GeodeFeature implements UndergroundFeature {

    @Builder.Default
    private int rarity = 8;
    @Builder.Default
    private int minHeight = Integer.MIN_VALUE;
    @Builder.Default
    private int maxHeight = Integer.MAX_VALUE;
    private final Block shell;
    private final Block crystal;
    private final BlockPredicate condition;

    private final WorldgenRandom rand = new WorldgenRandom();

    @Override
    public void generateUndergroundFeature(World world, GalaxiaPlanetGenerator generator, int cx, int cy, int cz) {
        rand.prime(world, cx, cz, 78798789, cy);

        if (rand.nextInt(rarity) != 0) return;

        int size = 4 + rand.nextInt(5);
        int squaredSize = size * size;

        int dX = rand.nextInt(16) - 8;
        int dY = rand.nextInt(16);
        int dZ = rand.nextInt(16) - 8;

        if ((cy << 4) + dY < minHeight) return;
        if ((cy << 4) + dY > maxHeight) return;

        if (!condition.test(world, (cx << 4) + dX, (cy << 4) + dY, (cz << 4) + dZ)) return;

        for (int xOffset = -size; xOffset <= size; xOffset++) {
            int combinedX = (cx << 4) + xOffset + dX;

            for (int yOffset = -size; yOffset <= size; yOffset++) {
                int combinedY = (cy << 4) + yOffset + dY;

                for (int zOffset = -size; zOffset <= size; zOffset++) {
                    int squaredRadius = xOffset * xOffset + yOffset * yOffset + zOffset * zOffset + rand.nextInt(16);

                    if (squaredRadius > squaredSize) {
                        continue;
                    }

                    int combinedZ = (cz << 4) + zOffset + dZ;

                    if (!condition.test(world, combinedX, combinedY, combinedZ)) continue;

                    int radiusDifference = squaredSize - squaredRadius;

                    if (radiusDifference < 16) {
                        generator.setBlockSafe(combinedX, combinedY, combinedZ, shell, 0);
                    } else if (radiusDifference < 32) {
                        generator.setBlockSafe(combinedX, combinedY, combinedZ, crystal, 0);
                    } else {
                        generator.setBlockSafe(combinedX, combinedY, combinedZ, Blocks.air, 0);
                    }
                }
            }
        }
    }
}
