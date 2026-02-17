package com.gtnewhorizons.galaxia.dimension.planets;

import net.minecraft.init.Blocks;
import net.minecraft.world.WorldProvider;

import com.gtnewhorizons.galaxia.block.BlockVariant;
import com.gtnewhorizons.galaxia.block.GalaxiaBlockBase;
import com.gtnewhorizons.galaxia.dimension.BiomeGenBuilder;
import com.gtnewhorizons.galaxia.dimension.BiomeGenSpace;
import com.gtnewhorizons.galaxia.dimension.DimensionBuilder;
import com.gtnewhorizons.galaxia.dimension.PlanetEnum;
import com.gtnewhorizons.galaxia.dimension.WorldProviderBuilder;
import com.gtnewhorizons.galaxia.dimension.WorldProviderSpace;

public class Dunia extends BasePlanet {

    @Override
    protected DimensionBuilder createBuilder() {
        return super.createBuilder().gravity(.5)
            .airResistance(.7);
    }

    public static final PlanetEnum ENUM = PlanetEnum.DUNIA;

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
                    .topBlock(GalaxiaBlockBase.get(PlanetEnum.CALX, BlockVariant.REGOLITH.suffix))
                    .fillerBlock(Blocks.brick_block));
        }
    }
}
