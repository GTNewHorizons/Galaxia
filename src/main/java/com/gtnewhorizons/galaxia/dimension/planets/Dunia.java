package com.gtnewhorizons.galaxia.dimension.planets;

import net.minecraft.init.Blocks;
import net.minecraft.world.biome.BiomeGenBase;

import com.gtnewhorizons.galaxia.block.BlockVariant;
import com.gtnewhorizons.galaxia.block.GalaxiaBlockBase;
import com.gtnewhorizons.galaxia.dimension.BiomeGenBuilder;
import com.gtnewhorizons.galaxia.dimension.DimensionBuilder;
import com.gtnewhorizons.galaxia.dimension.DimensionEnum;
import com.gtnewhorizons.galaxia.dimension.WorldProviderBuilder;
import com.gtnewhorizons.galaxia.worldgen.TerrainConfiguration;
import com.gtnewhorizons.galaxia.worldgen.TerrainPreset;

public class Dunia extends BasePlanet {

    public static final DimensionEnum ENUM = DimensionEnum.DUNIA;

    @Override
    public DimensionEnum getPlanetEnum() {
        return ENUM;
    }

    @Override
    protected DimensionBuilder customizeDimension(DimensionBuilder builder) {
        return builder.mass(0.1)
            .orbitalRadius(1.52 * earthRadiusToAU)
            .radius(0.53)
            .gravity(0.5)
            .airResistance(0.7);
    }

    @Override
    protected void configureProvider(WorldProviderBuilder builder) {
        builder.sky(true)
            .fog(0.15f, 0.1f, 0.3f)
            .avgGround(80)
            .biome(createBiome())
            .name(ENUM)
            .terrain(
                TerrainConfiguration.builder()
                    .feature(TerrainPreset.MOUNTAIN_RANGES)
                    .scale(4)
                    .fillerBlock(GalaxiaBlockBase.get(DimensionEnum.CALX, BlockVariant.ANDESITE.suffix))
                    .topBlock(GalaxiaBlockBase.get(DimensionEnum.CALX, BlockVariant.REGOLITH.suffix))
                    .endFeature()
                    .feature(TerrainPreset.SAND_DUNES)
                    .scale(4)
                    .fillerBlock(GalaxiaBlockBase.get(DimensionEnum.CALX, BlockVariant.ANDESITE.suffix))
                    .topBlock(GalaxiaBlockBase.get(DimensionEnum.CALX, BlockVariant.REGOLITH.suffix))
                    .endFeature()
                    .build());
    }

    protected static BiomeGenBase createBiome() {
        return new BiomeGenBuilder(100).name("Dunia Surface")
            .height(0.1F, 0.11F)
            .temperature(0.4F)
            .rainfall(0.99F)
            .topBlock(GalaxiaBlockBase.get(DimensionEnum.DUNIA, BlockVariant.REGOLITH.suffix))
            .fillerBlock(Blocks.brick_block)
            .build();
    }
}
