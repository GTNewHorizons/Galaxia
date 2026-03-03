package com.gtnewhorizons.galaxia.registry.dimension.planets;

import net.minecraft.world.biome.BiomeGenBase;

import com.gtnewhorizons.galaxia.registry.block.base.BlockVariant;
import com.gtnewhorizons.galaxia.registry.block.base.GalaxiaBlock;
import com.gtnewhorizons.galaxia.registry.dimension.DimensionEnum;
import com.gtnewhorizons.galaxia.registry.dimension.biome.BiomeGenBuilder;
import com.gtnewhorizons.galaxia.registry.dimension.builder.DimensionBuilder;
import com.gtnewhorizons.galaxia.registry.dimension.builder.EffectBuilder;
import com.gtnewhorizons.galaxia.registry.dimension.provider.WorldProviderBuilder;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.TerrainConfiguration;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.TerrainPreset;
import com.gtnewhorizons.galaxia.utility.BiomeIdOffsetter;
import com.gtnewhorizons.galaxia.utility.BlockMeta;

/**
 * The class holding all data related to the dimension Hemateria
 */
public class Hemateria extends BasePlanet {

    public static final DimensionEnum ENUM = DimensionEnum.HEMATERIA;

    /**
     * Getter for dimension Enum
     *
     * @return Dimension Enum
     */
    @Override
    public DimensionEnum getPlanetEnum() {
        return ENUM;
    }

    /**
     * The configuration of the DimensionBuilder to configure the dimension
     *
     * @param builder The dimension builder to chain on
     * @return The dimension Builder with all properties assigned
     */
    @Override
    protected DimensionBuilder customizeDimension(DimensionBuilder builder) {
        return builder.mass(0.25)
            .orbitalRadius(1.52 * earthRadiusToAU)
            .radius(0.53)
            .gravity(0.25)
            .airResistance(0.1)
            .effects(
                EffectBuilder.builder()
                    .baseTemp(67)
                    .oxygenPercent(0)
                    .pressure(1)
                    .build());
    }

    /**
     * Configures the world provider to add the correct biomes and settings
     *
     * @param builder The world provider builder being configured
     */
    @Override
    protected void configureProvider(WorldProviderBuilder builder) {
        builder.sky(true)
            .fog(0.15f, 0.1f, 0.3f)
            .biome(
                createBiome(
                    "Hemateria Dunes",
                    TerrainConfiguration.builder()
                        .feature(TerrainPreset.BASE_HEIGHT)
                        .height(64)
                        .endFeature()
                        .feature(TerrainPreset.SAND_DUNES)
                        .height(16)
                        .width(1.5)
                        .endFeature()
                        .build(),
                    true,
                    GalaxiaBlock.get(DimensionEnum.HEMATERIA, BlockVariant.REGOLITH)),
                0,
                0)
            .biome(
                createBiome(
                    "Hemateria Mountains",
                    TerrainConfiguration.builder()
                        .feature(TerrainPreset.BASE_HEIGHT)
                        .height(64)
                        .endFeature()
                        .feature(TerrainPreset.MOUNTAIN_RANGES)
                        .height(64)
                        .width(2)
                        .endFeature()
                        .build(),
                    false,
                    GalaxiaBlock.get(DimensionEnum.HEMATERIA, BlockVariant.REGOLITH)),
                0,
                1)
            .biome(
                createBiome(
                    "Hemateria Flatlands",
                    TerrainConfiguration.builder()
                        .feature(TerrainPreset.BASE_HEIGHT)
                        .height(64)
                        .endFeature()
                        .feature(TerrainPreset.SAND_DUNES)
                        .width(0.5)
                        .height(6)
                        .endFeature()
                        .feature(TerrainPreset.MOUNTAIN_RANGES)
                        .height(8)
                        .width(2)
                        .endFeature()
                        .build(),
                    true,
                    GalaxiaBlock.get(DimensionEnum.HEMATERIA, BlockVariant.REGOLITH)),
                1,
                0)
            .biome(
                createBiome(
                    "Hemateria Basins",
                    TerrainConfiguration.builder()
                        .feature(TerrainPreset.BASE_HEIGHT)
                        .height(16)
                        .endFeature()
                        .feature(TerrainPreset.MOUNTAIN_RANGES)
                        .height(8)
                        .width(0.5)
                        .endFeature()
                        .build(),
                    false,
                    GalaxiaBlock.get(DimensionEnum.HEMATERIA, "rhyolite")),
                1,
                1)
            .name(ENUM)
            .build();
    }

    protected static BiomeGenBase createBiome(String name, TerrainConfiguration terrain, boolean generateCaves,
        BlockMeta surfaceBlock) {
        return new BiomeGenBuilder(BiomeIdOffsetter.getBiomeId()).name(name)
            .temperature(0.4F)
            .rainfall(0.99F)
            .topBlock(surfaceBlock)
            .fillerBlock(GalaxiaBlock.get(DimensionEnum.HEMATERIA, BlockVariant.ANDESITE))
            .snowBlock(GalaxiaBlock.get(DimensionEnum.HEMATERIA, BlockVariant.SNOW), 144)
            .terrain(terrain)
            .generateCaves(generateCaves)
            .build();
    }
}
