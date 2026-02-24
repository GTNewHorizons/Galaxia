package com.gtnewhorizons.galaxia.dimension.asteroidbelts;

import net.minecraft.world.WorldProvider;

import com.gtnewhorizons.galaxia.block.base.BlockVariant;
import com.gtnewhorizons.galaxia.block.base.GalaxiaBlock;
import com.gtnewhorizons.galaxia.dimension.DimensionEnum;
import com.gtnewhorizons.galaxia.dimension.biome.BiomeGenBuilder;
import com.gtnewhorizons.galaxia.dimension.biome.BiomeGenSpace;
import com.gtnewhorizons.galaxia.dimension.builder.DimensionBuilder;
import com.gtnewhorizons.galaxia.dimension.builder.EffectBuilder;
import com.gtnewhorizons.galaxia.dimension.provider.WorldProviderBuilder;
import com.gtnewhorizons.galaxia.dimension.provider.WorldProviderSpace;
import com.gtnewhorizons.galaxia.utility.BlockMeta;
import com.gtnewhorizons.galaxia.worldgen.Asteroid;

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
            Asteroid[] asteroids = new Asteroid[] {
                new Asteroid(
                    12,
                    16,
                    32,
                    new BlockMeta[] { GalaxiaBlock.get(DimensionEnum.FROZEN_BELT, BlockVariant.ANDESITE.suffix()),
                        GalaxiaBlock.get(DimensionEnum.FROZEN_BELT, BlockVariant.ANORTHOSITE.suffix()) },
                    1),
                new Asteroid(
                    16,
                    20,
                    64,
                    new BlockMeta[] { GalaxiaBlock.get(DimensionEnum.FROZEN_BELT, BlockVariant.ICE.suffix()),
                        GalaxiaBlock.get(DimensionEnum.FROZEN_BELT, BlockVariant.BASALT.suffix()) },
                    6),
                new Asteroid(
                    20,
                    32,
                    128,
                    new BlockMeta[] { GalaxiaBlock.get(DimensionEnum.FROZEN_BELT, BlockVariant.GABBRO.suffix()),
                        GalaxiaBlock.get(DimensionEnum.FROZEN_BELT, BlockVariant.BRECCIA.suffix()) },
                    8),
                new Asteroid(
                    32,
                    64,
                    512,
                    new BlockMeta[] { GalaxiaBlock.get(DimensionEnum.FROZEN_BELT, BlockVariant.GABBRO.suffix()),
                        GalaxiaBlock.get(DimensionEnum.FROZEN_BELT, BlockVariant.BASALT.suffix()) },
                    12),
                new Asteroid(
                    32,
                    64,
                    512,
                    new BlockMeta[] { GalaxiaBlock.get(DimensionEnum.FROZEN_BELT, BlockVariant.ICE.suffix()),
                        GalaxiaBlock.get(DimensionEnum.FROZEN_BELT, BlockVariant.BRECCIA.suffix()) },
                    4) };
            // Configure the world provider for this dimension
            WorldProviderBuilder.configure(this)
                .sky(true)
                .skyColor(0, 0.1, 0.3)
                .fog(0, 0.1f, 0.3f)
                .biome(new BiomeGenFrozenBelt(100), 0, 0)
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
                    .rainfall(0)
                    .generateBedrock(false));
        }
    }
}
