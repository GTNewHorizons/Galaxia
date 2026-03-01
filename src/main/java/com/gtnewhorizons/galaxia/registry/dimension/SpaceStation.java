package com.gtnewhorizons.galaxia.registry.dimension;

import net.minecraft.world.WorldProvider;

import com.gtnewhorizons.galaxia.registry.dimension.biome.BiomeGenBuilder;
import com.gtnewhorizons.galaxia.registry.dimension.biome.BiomeGenSpace;
import com.gtnewhorizons.galaxia.registry.dimension.builder.DimensionBuilder;
import com.gtnewhorizons.galaxia.registry.dimension.builder.EffectBuilder;
import com.gtnewhorizons.galaxia.registry.dimension.planets.BasePlanet;
import com.gtnewhorizons.galaxia.registry.dimension.provider.WorldProviderBuilder;
import com.gtnewhorizons.galaxia.registry.dimension.provider.WorldProviderSpace;
import com.gtnewhorizons.galaxia.utility.BiomeIdOffsetter;

public class SpaceStation extends BasePlanet {

    public static final DimensionEnum ENUM = DimensionEnum.VITRIS_SPACE;

    @Override
    protected DimensionBuilder customizeDimension(DimensionBuilder builder) {
        return builder.provider(getProviderClass())
            .airResistance(0)
            .gravity(0)
            .effects(
                EffectBuilder.builder()
                    .baseTemp(67)
                    .oxygenPercent(0)
                    .pressure(1)
                    .build());
    }

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
     * Getter for the world provider class
     *
     * @return WorldProvider class
     */
    protected Class<? extends WorldProvider> getProviderClass() {
        return SpaceStation.WorldProviderSpaceStation.class;
    }

    /**
     * Static class to hold world provider for frozen belt
     */
    public static class WorldProviderSpaceStation extends WorldProviderSpace {

        /**
         * Creates the world provider used in generation of this dimension
         */
        public WorldProviderSpaceStation() {
            // Configure the world provider for this dimension
            WorldProviderBuilder.configure(this)
                .sky(true)
                .skyColor(0, 0.1, 0.3)
                .fog(0, 0.1f, 0.3f)
                .biome(new SpaceStation.BiomeGenSpaceStation(BiomeIdOffsetter.getBiomeId()), 0, 0)
                .name(ENUM)
                .cloudHeight(Integer.MIN_VALUE)
                .chunkGen(() -> new ChunkProviderSpaceStation(worldObj, worldObj.getSeed()))
                .build();
        }
    }

    /**
     * Static class to hold the Biome generation
     */
    public static class BiomeGenSpaceStation extends BiomeGenSpace {

        /**
         * Creates the biome generator for Space Stations
         *
         * @param id The ID of the biome to generate
         */
        public BiomeGenSpaceStation(int id) {
            super(
                id,
                new BiomeGenBuilder(id).name("Space")
                    .temperature(1.0F)
                    .rainfall(0)
                    .generateBedrock(false));
        }
    }
}
