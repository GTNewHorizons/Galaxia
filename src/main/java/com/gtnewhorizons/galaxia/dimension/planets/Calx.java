package com.gtnewhorizons.galaxia.dimension.planets;

import net.minecraft.init.Blocks;
import net.minecraft.world.WorldProvider;

import com.gtnewhorizons.galaxia.dimension.BiomeGenBuilder;
import com.gtnewhorizons.galaxia.dimension.BiomeGenSpace;
import com.gtnewhorizons.galaxia.dimension.DimensionBuilder;
import com.gtnewhorizons.galaxia.dimension.PlanetEnum;
import com.gtnewhorizons.galaxia.dimension.WorldProviderBuilder;
import com.gtnewhorizons.galaxia.dimension.WorldProviderSpace;

public class Calx extends BasePlanet {

    @Override
    protected DimensionBuilder createBuilder() {
        return super.createBuilder().gravity(2)
            .airResistance(1.7);
    }

    public static final PlanetEnum ENUM = PlanetEnum.CALX;

    @Override
    protected PlanetEnum getPlanetEnum() {
        return ENUM;
    }

    @Override
    protected Class<? extends WorldProvider> getProviderClass() {
        return WorldProviderCalx.class;
    }

    public static class WorldProviderCalx extends WorldProviderSpace {

        public WorldProviderCalx() {
            WorldProviderBuilder.configure(this)
                .sky(true)
                .fog(0.15f, 0.1f, 0.3f)
                .avgGround(80)
                .biome(new BiomeGenCalx(100))
                .name(ENUM)
                .build();
        }
    }

    public static class BiomeGenCalx extends BiomeGenSpace {

        public BiomeGenCalx(int id) {
            super(
                id,
                new BiomeGenBuilder(id).name("Calx Surface")
                    .height(0.1F, 0.11F)
                    .temperature(0.4F)
                    .rainfall(0.99F)
                    .topBlock(Blocks.brick_block)
                    .fillerBlock(Blocks.stone));
        }
    }
}
