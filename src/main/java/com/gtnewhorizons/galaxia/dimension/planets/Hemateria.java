package com.gtnewhorizons.galaxia.dimension.planets;

import net.minecraft.init.Blocks;

import com.gtnewhorizons.galaxia.block.BlockVariant;
import com.gtnewhorizons.galaxia.block.GalaxiaBlockBase;
import com.gtnewhorizons.galaxia.dimension.DimensionBuilder;
import com.gtnewhorizons.galaxia.dimension.DimensionEnum;
import com.gtnewhorizons.galaxia.dimension.EffectBuilder;
import com.gtnewhorizons.galaxia.dimension.WorldProviderBuilder;
import com.gtnewhorizons.galaxia.worldgen.TerrainConfiguration;
import com.gtnewhorizons.galaxia.worldgen.TerrainPreset;

public class Hemateria extends BasePlanet {

    public static final DimensionEnum ENUM = DimensionEnum.HEMATERIA;

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
            .airResistance(0.7)
            .effects(
                new EffectBuilder().baseTemp(67)
                    .oxygenPercent(0)
                    .pressure(1));
    }

    @Override
    protected void configureProvider(WorldProviderBuilder builder) {
        builder.sky(true)
            .fog(0.15f, 0.1f, 0.3f)
            .avgGround(80)
            // These biome names are mostly just for testing
            .createBiomeMatrix(2)
            .biome(
                createBiome(
                    "Hemateria Dunes",
                    Blocks.brick_block,
                    TerrainConfiguration.builder()
                        .feature(TerrainPreset.SAND_DUNES)
                        .scale(4)
                        .width(1.5)
                        .height(2)
                        .endFeature()
                        .build()),
                0,
                0)
            .biome(
                createBiome(
                    "Hemateria Mountains",
                    Blocks.wool,
                    4,
                    TerrainConfiguration.builder()
                        .feature(TerrainPreset.MOUNTAIN_RANGES)
                        .scale(4)
                        .height(0.5)
                        .width(2)
                        .endFeature()
                        .build()),
                0,
                1)
            .biome(
                createBiome(
                    "Hemateria Hills",
                    GalaxiaBlockBase.get(DimensionEnum.HEMATERIA, BlockVariant.REGOLITH.suffix)
                        .block(),
                    TerrainConfiguration.builder()
                        .feature(TerrainPreset.MOUNTAIN_RANGES)
                        .scale(0.25)
                        .height(4)
                        .width(2)
                        .endFeature()
                        .build()),
                1,
                0)
            .biome(
                createBiome(
                    "Hemateria Dune Hills",
                    GalaxiaBlockBase.get(DimensionEnum.THEIA, BlockVariant.REGOLITH.suffix)
                        .block(),
                    TerrainConfiguration.builder()
                        .feature(TerrainPreset.MOUNTAIN_RANGES)
                        .scale(4)
                        .height(0.5)
                        .width(2)
                        .endFeature()
                        .feature(TerrainPreset.SAND_DUNES)
                        .scale(4)
                        .width(1.5)
                        .height(2)
                        .endFeature()
                        .build()),
                1,
                1)
            .name(ENUM)
            .build();
    }
}
