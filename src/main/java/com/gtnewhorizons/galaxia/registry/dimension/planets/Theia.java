package com.gtnewhorizons.galaxia.registry.dimension.planets;

import net.minecraft.world.biome.BiomeGenBase;

import com.gtnewhorizons.galaxia.registry.block.base.BlockVariant;
import com.gtnewhorizons.galaxia.registry.block.base.GalaxiaBlock;
import com.gtnewhorizons.galaxia.registry.dimension.DimensionEnum;
import com.gtnewhorizons.galaxia.registry.dimension.biome.BiomeGenBuilder;
import com.gtnewhorizons.galaxia.registry.dimension.builder.DimensionBuilder;
import com.gtnewhorizons.galaxia.registry.dimension.builder.EffectBuilder;
import com.gtnewhorizons.galaxia.registry.dimension.provider.WorldProviderBuilder;
import com.gtnewhorizons.galaxia.registry.dimension.sky.SkyBuilder;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.TerrainConfiguration;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.TerrainPreset;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.WorldGenCrater;
import com.gtnewhorizons.galaxia.utility.BiomeIdOffsetter;
import com.gtnewhorizons.galaxia.utility.BlockMeta;

/**
 * The class holding all data related to the dimension Theia
 */
public class Theia extends BasePlanet {

    public static final DimensionEnum ENUM = DimensionEnum.THEIA;

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
        return builder.gravity(0.25)
            .airResistance(0.01)
            .mass(0.012)
            .radius(0.27)
            .orbitalRadius(1 * earthRadiusToAU)
            .sky(buildSky())
            .effects(
                EffectBuilder.builder()
                    .baseTemp(225)
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
            .fog(0.03f, 0.02f, 0.06f)
            .skyColor(0.015f, 0.01f, 0.03f)
            .avgGround(80)
            .biome(
                createBiome(
                    "Theia Hills",
                    GalaxiaBlock.get(DimensionEnum.THEIA, BlockVariant.REGOLITH.suffix()),
                    GalaxiaBlock.get(DimensionEnum.THEIA, BlockVariant.ANDESITE.suffix()),
                    true,
                    TerrainConfiguration.builder()
                        .feature(TerrainPreset.BASE_HEIGHT)
                        .height(64)
                        .endFeature()
                        .feature(TerrainPreset.MOUNTAIN_RANGES)
                        .width(32)
                        .height(32)
                        .endFeature()
                        .feature(TerrainPreset.CANYONS)
                        .width(4)
                        .height(32)
                        .endFeature()
                        .build(),
                    2,
                    1),
                0,
                0)
            .biome(
                createBiome(
                    "Theia Mountains",
                    GalaxiaBlock.get(DimensionEnum.THEIA, BlockVariant.REGOLITH.suffix()),
                    GalaxiaBlock.get(DimensionEnum.THEIA, BlockVariant.ANDESITE.suffix()),
                    true,
                    TerrainConfiguration.builder()
                        .feature(TerrainPreset.BASE_HEIGHT)
                        .height(64)
                        .endFeature()
                        .feature(TerrainPreset.MOUNTAIN_RANGES)
                        .width(3)
                        .height(16)
                        .endFeature()
                        .feature(TerrainPreset.MOUNTAIN_RANGES)
                        .width(12)
                        .height(64)
                        .endFeature()
                        .build(),
                    2,
                    1),
                0,
                1)
            .biome(
                createBiome(
                    "Theia Small Volcanoes",
                    GalaxiaBlock.get(DimensionEnum.THEIA, BlockVariant.BASALT.suffix()),
                    GalaxiaBlock.get(DimensionEnum.THEIA, BlockVariant.BASALT.suffix()),
                    false,
                    TerrainConfiguration.builder()
                        .feature(TerrainPreset.BASE_HEIGHT)
                        .height(32)
                        .endFeature()
                        .feature(TerrainPreset.SHIELD_VOLCANOES)
                        .width(2)
                        .height(8)
                        .endFeature()
                        .build(),
                    8,
                    41),
                1,
                0)
            .biome(
                createBiome(
                    "Theia Big Volcanoes",
                    GalaxiaBlock.get(DimensionEnum.THEIA, BlockVariant.BASALT.suffix()),
                    GalaxiaBlock.get(DimensionEnum.THEIA, BlockVariant.BASALT.suffix()),
                    false,
                    TerrainConfiguration.builder()
                        .feature(TerrainPreset.BASE_HEIGHT)
                        .height(32)
                        .endFeature()
                        .feature(TerrainPreset.SHIELD_VOLCANOES)
                        .width(4)
                        .height(32)
                        .endFeature()
                        .build(),
                    8,
                    41),
                1,
                1)
            .name(ENUM)
            .build();
    }

    /**
     * Builds a skybox builder with required bodies in the sky
     *
     * @return The SkyBuilder configured with correct bodies
     */
    protected SkyBuilder buildSky() {
        return SkyBuilder.builder()
            .addBody(
                s -> s.texture("minecraft:textures/environment/sun.png")
                    .size(30f)
                    .distance(100.0)
                    .inclination(45)
                    .period(24000L))
            .addBody(
                m -> m.texture("minecraft:textures/environment/moon_phases.png")
                    .size(20f)
                    .distance(-100.0)
                    .inclination(60)
                    .period(23151L)
                    .hasPhases())
            .addBody(
                m -> m.textureGalaxia("textures/environment/phobos.png")
                    .size(6f)
                    .distance(90.0)
                    .inclination(10.0f)
                    .period(3000L))
            .addBody(
                m -> m.textureGalaxia("textures/environment/phobos.png")
                    .size(6f)
                    .distance(90.0)
                    .inclination(20.0f)
                    .period(1200L))
            .addBody(
                m -> m.textureGalaxia("textures/environment/phobos.png")
                    .size(6f)
                    .distance(90.0)
                    .inclination(40.0f)
                    .period(12000L))
            .addBody(
                m -> m.textureGalaxia("textures/environment/phobos.png")
                    .size(6f)
                    .distance(90.0)
                    .inclination(30.0f)
                    .period(6000L));
    }

    /**
     * Creates a biome generator with specific requirements
     *
     * @return The BiomeGenBase used to generated biomes of that type
     */
    protected static BiomeGenBase createBiome(String name, BlockMeta topBlock, BlockMeta fillerBlock,
        boolean generateCaves, TerrainConfiguration terrainConfiguration, int craterRarity, int oceanHeight) {
        return new BiomeGenBuilder(BiomeIdOffsetter.getBiomeId()).name(name)
            .height(0.1F, 0.11F)
            .temperature(0.4F)
            .rainfall(0.99F)
            .topBlock(topBlock)
            .fillerBlock(fillerBlock)
            .generateCaves(generateCaves)
            .surfaceFeature(
                new WorldGenCrater(
                    craterRarity,
                    new BlockMeta[] { GalaxiaBlock.get(DimensionEnum.THEIA, BlockVariant.REGOLITH.suffix()),
                        GalaxiaBlock.get(DimensionEnum.THEIA, BlockVariant.BASALT.suffix()) }))
            .terrain(terrainConfiguration)
            .ocean(
                GalaxiaBlock.get(DimensionEnum.THEIA, BlockVariant.OBSIDIAN.suffix()),
                GalaxiaBlock.get(DimensionEnum.THEIA, BlockVariant.BASALT.suffix()),
                oceanHeight,
                GalaxiaBlock.get(DimensionEnum.THEIA, BlockVariant.OBSIDIAN.suffix()),
                1)
            .surfaceThickness(4)
            .build();
    }
}
