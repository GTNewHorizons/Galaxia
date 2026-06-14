package com.gtnewhorizons.galaxia.registry.dimension;

import com.gtnewhorizons.galaxia.registry.dimension.biome.BiomeGenBuilder;
import com.gtnewhorizons.galaxia.registry.dimension.biome.BiomeGenSpace;
import com.gtnewhorizons.galaxia.registry.dimension.biome.BiomeIdOffsetter;
import com.gtnewhorizons.galaxia.registry.dimension.provider.WorldProviderBuilder;

public final class SpaceStation {

    private SpaceStation() {}

    public static void configureWorldProvider(WorldProviderBuilder builder) {
        builder.sky(true)
            .skyColor(0, 0.1, 0.3)
            .fog(0, 0.1f, 0.3f)
            .biome(new SpaceStation.BiomeGenSpaceStation(BiomeIdOffsetter.getBiomeId()), 0, 0)
            .name(DimensionEnum.OVERWORLD_ORBIT)
            .cloudHeight(Integer.MIN_VALUE)
            .chunkGen(() -> new ChunkProviderSpaceStation(builder.provider().worldObj))
            .build();
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
                    .rainfall(0));
        }
    }
}
