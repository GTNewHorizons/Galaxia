package com.gtnewhorizons.galaxia.registry.dimension.asteroidbelts;

import net.minecraft.block.Block;
import net.minecraft.world.WorldProvider;

import com.gtnewhorizons.galaxia.registry.block.PlanetBlocks;
import com.gtnewhorizons.galaxia.registry.dimension.DimensionEnum;
import com.gtnewhorizons.galaxia.registry.dimension.biome.BiomeGenBuilder;
import com.gtnewhorizons.galaxia.registry.dimension.biome.BiomeGenSpace;
import com.gtnewhorizons.galaxia.registry.dimension.builder.DimensionBuilder;
import com.gtnewhorizons.galaxia.registry.dimension.builder.EffectBuilder;
import com.gtnewhorizons.galaxia.registry.dimension.provider.WorldProviderBuilder;
import com.gtnewhorizons.galaxia.registry.dimension.provider.WorldProviderSpace;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.WorldGenAsteroid;
import com.gtnewhorizons.galaxia.utility.BiomeIdOffsetter;

/**
 * The class holding all data related to the dimension FrozenBelt
 */
public class FrozenBelt extends BaseAsteroidBelt {

    public static final DimensionEnum ENUM = DimensionEnum.FROZEN_BELT;

    /**
     * Returns the ENUM of the dimension
     *
     * @return DimensionEnum of the planet
     */
    @Override
    public DimensionEnum getPlanetEnum() {
        return ENUM;
    }

    /**
     * Overrides the Dimension builder to add effects, and other fields
     *
     * @param builder The DimensionBuilder for this dim
     * @return The same builder with fields added as required
     */
    @Override
    protected DimensionBuilder customizeDimension(DimensionBuilder builder) {
        // super call adds basic asteroid belt stats - add on top as required
        return super.customizeDimension(builder).effects(
            EffectBuilder.builder()
                .baseTemp(67)
                .oxygenPercent(0)
                .pressure(1)
                .build());
    }

    /**
     * Getter for the world provider class
     *
     * @return WorldProvider class
     */
    @Override
    protected Class<? extends WorldProvider> getProviderClass() {
        return WorldProviderFrozenBelt.class;
    }

    /**
     * Static class to hold world provider for frozen belt
     */
    public static class WorldProviderFrozenBelt extends WorldProviderSpace {

        /**
         * Creates the world provider used in generation of this dimension
         */
        public WorldProviderFrozenBelt() {
            // Generates an array of asteroids for use in generation
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
            // Configure the world provider for this dimension
            WorldProviderBuilder.configure(this)
                .sky(true)
                .skyColor(0, 0.1, 0.3)
                .fog(0, 0.1f, 0.3f)
                .biome(new BiomeGenFrozenBelt(BiomeIdOffsetter.getBiomeId()), 0, 0)
                .name(ENUM)
                .cloudHeight(Integer.MIN_VALUE)
                .chunkGen(() -> new ChunkProviderAsteroidBelt(worldObj, worldObj.getSeed(), asteroids))
                .build();
        }
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
