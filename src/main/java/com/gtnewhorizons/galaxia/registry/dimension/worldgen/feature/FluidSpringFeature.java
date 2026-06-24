package com.gtnewhorizons.galaxia.registry.dimension.worldgen.feature;

import net.minecraft.block.Block;
import net.minecraft.world.World;

import com.gtnewhorizons.galaxia.registry.dimension.worldgen.GalaxiaPlanetGenerator;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.math.WorldgenRandom;

import lombok.Builder;

@Builder
public class FluidSpringFeature implements UndergroundFeature {

    @Builder.Default
    private int rarity = 16;
    @Builder.Default
    private int tries = 4;
    @Builder.Default
    private int minHeight = Integer.MIN_VALUE;
    @Builder.Default
    private int maxHeight = Integer.MAX_VALUE;
    private final Block fluid;
    private final BlockPredicate condition;

    private final WorldgenRandom rand = new WorldgenRandom();

    @Override
    public void generateUndergroundFeature(World world, GalaxiaPlanetGenerator generator, int cx, int cy, int cz) {
        rand.prime(world, cx, cz, 567456, cy);

        if (rand.nextInt(rarity) != 0) return;

        for (int i = 0; i < tries; i++) {
            int wx = (cx << 4) + rand.nextInt(16) - 8;
            int wy = (cy << 4) + rand.nextInt(16);
            int wz = (cz << 4) + rand.nextInt(16) - 8;

            if (wy < minHeight) continue;
            if (wy > maxHeight) continue;

            if (!condition.test(world, wx, wy + 1, wz)) continue;

            int air = 0;

            if (world.isAirBlock(wx + 1, wy, wz)) air++;
            if (world.isAirBlock(wx - 1, wy, wz)) air++;
            if (world.isAirBlock(wx, wy, wz + 1)) air++;
            if (world.isAirBlock(wx, wy, wz - 1)) air++;

            if (air != 1) continue;

            world.setBlock(wx, wy, wz, fluid, 0, 2);
            return;
        }
    }
}
