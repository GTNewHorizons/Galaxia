package com.gtnewhorizons.galaxia.registry.dimension.asteroidbelts;

import net.minecraft.block.Block;

import com.gtnewhorizons.galaxia.registry.block.PlanetBlocks;
import com.gtnewhorizons.galaxia.registry.dimension.DimensionEnum;
import com.gtnewhorizons.galaxia.registry.dimension.biome.BiomeGenBuilder;
import com.gtnewhorizons.galaxia.registry.dimension.biome.BiomeGenSpace;
import com.gtnewhorizons.galaxia.registry.dimension.biome.BiomeIdOffsetter;
import com.gtnewhorizons.galaxia.registry.dimension.provider.WorldProviderBuilder;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.WorldGenAsteroid;

/**
 * The class holding all data related to the dimension FrozenBelt
 */
public final class FrozenBelt {

    private FrozenBelt() {}

    public static void configureWorldProvider(WorldProviderBuilder builder) {
        WorldGenAsteroid[] asteroids = new WorldGenAsteroid[] {
            new WorldGenAsteroid(
                12,
                16,
                32,
                new Block[] { PlanetBlocks.FROZEN_BELT_ANDESITE, PlanetBlocks.FROZEN_BELT_ANORTHOSITE },
                new Block[] { PlanetBlocks.FROZEN_BELT_ICE, PlanetBlocks.FROZEN_BELT_BRECCIA },
                1),
            new WorldGenAsteroid(
                16,
                20,
                64,
                new Block[] { PlanetBlocks.FROZEN_BELT_ICE, PlanetBlocks.FROZEN_BELT_BASALT },
                new Block[] { PlanetBlocks.FROZEN_BELT_GABBRO, PlanetBlocks.FROZEN_BELT_BRECCIA },
                3),

            new WorldGenAsteroid(
                20,
                32,
                128,
                new Block[] { PlanetBlocks.FROZEN_BELT_GABBRO, PlanetBlocks.FROZEN_BELT_BRECCIA },
                new Block[] { PlanetBlocks.FROZEN_BELT_ICE, PlanetBlocks.FROZEN_BELT_BASALT },
                4),

            new WorldGenAsteroid(
                24,
                48,
                512,
                new Block[] { PlanetBlocks.FROZEN_BELT_GABBRO, PlanetBlocks.FROZEN_BELT_BASALT },
                new Block[] { PlanetBlocks.FROZEN_BELT_BASALT, PlanetBlocks.FROZEN_BELT_BRECCIA },
                6),

            new WorldGenAsteroid(
                24,
                48,
                512,
                new Block[] { PlanetBlocks.FROZEN_BELT_ICE, PlanetBlocks.FROZEN_BELT_BRECCIA },
                new Block[] { PlanetBlocks.FROZEN_BELT_GABBRO, PlanetBlocks.FROZEN_BELT_BASALT },
                2) };

        builder.sky(true)
            .skyColor(0, 0.1, 0.3)
            .fog(0, 0.1f, 0.3f)
            .biome(new BiomeGenFrozenBelt(BiomeIdOffsetter.getBiomeId()), 0, 0)
            .name(DimensionEnum.FROZEN_BELT)
            .cloudHeight(Integer.MIN_VALUE)
            .chunkGen(
                () -> new ChunkProviderAsteroidBelt(
                    builder.provider().worldObj,
                    builder.provider().worldObj.getSeed(),
                    asteroids))
            .build();
    }

    /**
     * Static class to hold the Biome generation
     */
    public static class BiomeGenFrozenBelt extends BiomeGenSpace {

        /**
         * Creates the biome generator for the FrozenBelt for a given biome ID
         *
         * @param id The ID of the biome to generate
         */
        public BiomeGenFrozenBelt(int id) {
            super(
                id,
                new BiomeGenBuilder(id).name("Frozen Belt")
                    .temperature(1.0F)
                    .rainfall(0));
        }
    }
}
