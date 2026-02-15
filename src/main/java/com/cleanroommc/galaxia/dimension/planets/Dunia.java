package com.cleanroommc.galaxia.dimension.planets;

import net.minecraft.init.Blocks;
import net.minecraft.world.WorldProvider;

import com.cleanroommc.galaxia.dimension.BiomeGenBuilder;
import com.cleanroommc.galaxia.dimension.BiomeGenSpace;
import com.cleanroommc.galaxia.dimension.DimensionBuilder;
import com.cleanroommc.galaxia.dimension.PlanetEnum;
import com.cleanroommc.galaxia.dimension.WorldProviderBuilder;
import com.cleanroommc.galaxia.dimension.WorldProviderSpace;

public class Dunia extends BasePlanet {

    @Override
    protected DimensionBuilder createBuilder() {
        return super.createBuilder().gravity(.5)
            .airResistance(.7);
    }

    public static final PlanetEnum ENUM = PlanetEnum.Dunia;

    @Override
    protected PlanetEnum getPlanetEnum() {
        return ENUM;
    }

    @Override
    protected Class<? extends WorldProvider> getProviderClass() {
        return WorldProviderDunia.class;
    }

    public static class WorldProviderDunia extends WorldProviderSpace {

        public WorldProviderDunia() {
            WorldProviderBuilder.configure(this)
                .sky(true)
                .fog(0.15f, 0.1f, 0.3f)
                .avgGround(80)
                .biome(new BiomeGenDunia(100))
                .name(ENUM)
                .build();
        }
    }

    public static class BiomeGenDunia extends BiomeGenSpace {

        public BiomeGenDunia(int id) {
            super(
                id,
                new BiomeGenBuilder(id).name("Dunia Surface")
                    .height(0.1F, 0.11F)
                    .temperature(0.4F)
                    .rainfall(0.99F)
                    .topBlock(Blocks.lapis_block)
                    .fillerBlock(Blocks.brown_mushroom_block));
        }
    }
}
