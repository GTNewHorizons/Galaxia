package com.gtnewhorizons.galaxia.dimension.asteroidbelts;

import net.minecraft.world.WorldProvider;

import com.gtnewhorizons.galaxia.block.BlockVariant;
import com.gtnewhorizons.galaxia.block.GalaxiaBlockBase;
import com.gtnewhorizons.galaxia.dimension.BiomeGenBuilder;
import com.gtnewhorizons.galaxia.dimension.BiomeGenSpace;
import com.gtnewhorizons.galaxia.dimension.DimensionBuilder;
import com.gtnewhorizons.galaxia.dimension.DimensionEnum;
import com.gtnewhorizons.galaxia.dimension.EffectBuilder;
import com.gtnewhorizons.galaxia.dimension.WorldProviderBuilder;
import com.gtnewhorizons.galaxia.dimension.WorldProviderSpace;
import com.gtnewhorizons.galaxia.utility.BlockMeta;
import com.gtnewhorizons.galaxia.worldgen.Asteroid;

public class FrozenBelt extends BaseAsteroidBelt {

    public static final DimensionEnum ENUM = DimensionEnum.FROZEN_BELT;

    @Override
    public DimensionEnum getPlanetEnum() {
        return ENUM;
    }

    @Override
    protected DimensionBuilder customizeDimension(DimensionBuilder builder) {
        return super.customizeDimension(builder).effects(
            new EffectBuilder().baseTemp(67)
                .oxygenPercent(0)
                .pressure(1));
    }

    @Override
    protected Class<? extends WorldProvider> getProviderClass() {
        return WorldProviderFrozenBelt.class;
    }

    public static class WorldProviderFrozenBelt extends WorldProviderSpace {

        public WorldProviderFrozenBelt() {
            Asteroid[] asteroids = new Asteroid[] {
                new Asteroid(
                    12,
                    16,
                    32,
                    new BlockMeta[] { GalaxiaBlockBase.get(DimensionEnum.FROZEN_BELT, BlockVariant.ANDESITE.suffix),
                        GalaxiaBlockBase.get(DimensionEnum.FROZEN_BELT, BlockVariant.ANORTHOSITE.suffix) },
                    1),
                new Asteroid(
                    16,
                    20,
                    64,
                    new BlockMeta[] { GalaxiaBlockBase.get(DimensionEnum.FROZEN_BELT, BlockVariant.ICE.suffix),
                        GalaxiaBlockBase.get(DimensionEnum.FROZEN_BELT, BlockVariant.BASALT.suffix) },
                    6),
                new Asteroid(
                    20,
                    32,
                    128,
                    new BlockMeta[] { GalaxiaBlockBase.get(DimensionEnum.FROZEN_BELT, BlockVariant.GABBRO.suffix),
                        GalaxiaBlockBase.get(DimensionEnum.FROZEN_BELT, BlockVariant.BRECCIA.suffix) },
                    8),
                new Asteroid(
                    32,
                    64,
                    512,
                    new BlockMeta[] { GalaxiaBlockBase.get(DimensionEnum.FROZEN_BELT, BlockVariant.GABBRO.suffix),
                        GalaxiaBlockBase.get(DimensionEnum.FROZEN_BELT, BlockVariant.BASALT.suffix) },
                    12),
                new Asteroid(
                    32,
                    64,
                    512,
                    new BlockMeta[] { GalaxiaBlockBase.get(DimensionEnum.FROZEN_BELT, BlockVariant.ICE.suffix),
                        GalaxiaBlockBase.get(DimensionEnum.FROZEN_BELT, BlockVariant.BRECCIA.suffix) },
                    4) };
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

    public static class BiomeGenFrozenBelt extends BiomeGenSpace {

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
