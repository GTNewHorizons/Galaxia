package com.cleanroommc.galaxia.dimension.planets;

import com.cleanroommc.galaxia.dimension.BiomeGenBuilder;
import com.cleanroommc.galaxia.dimension.BiomeGenSpace;
import com.cleanroommc.galaxia.dimension.DimensionBuilder;
import com.cleanroommc.galaxia.dimension.DimensionDef;
import com.cleanroommc.galaxia.dimension.IPlanet;
import com.cleanroommc.galaxia.dimension.SpaceWorldBuilder;
import com.cleanroommc.galaxia.dimension.WorldProviderSpace;
import net.minecraft.init.Blocks;

public class Calx implements IPlanet {

    @Override
    public DimensionDef buildDimension() {
        return new DimensionBuilder()
            .name("calx")
            .id(666)
            .provider(WorldProviderCalx.class)
            .build();
    }

    public static class WorldProviderCalx extends WorldProviderSpace {

        public WorldProviderCalx() {
            SpaceWorldBuilder.configure(this)
                .sky(true)
                .fog(0.15, 0.1, 0.3)
                .avgGround(80)
                .biome(new BiomeGenCalx(100))
                .build();
        }

        @Override
        public String getDimensionName() {
            return "Calx (Moon)";
        }
    }

    public static class BiomeGenCalx extends BiomeGenSpace {
        public BiomeGenCalx(int id) {
            super(id, new BiomeGenBuilder(id)
                .name("Calx Surface")
                .height(0.1F, 0.3F)
                .temperature(0.4F)
                .rainfall(0.0F)
                .topBlock(Blocks.grass)
                .fillerBlock(Blocks.stone)
            );
        }
    }
}
