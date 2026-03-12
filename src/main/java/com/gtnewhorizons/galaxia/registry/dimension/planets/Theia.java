package com.gtnewhorizons.galaxia.registry.dimension.planets;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.biome.BiomeGenBase;

import com.gtnewhorizons.galaxia.client.EnumTextures;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaBlocksEnum;
import com.gtnewhorizons.galaxia.registry.block.PlanetBlocks;
import com.gtnewhorizons.galaxia.registry.dimension.DimensionEnum;
import com.gtnewhorizons.galaxia.registry.dimension.biome.BiomeGenBuilder;
import com.gtnewhorizons.galaxia.registry.dimension.builder.DimensionBuilder;
import com.gtnewhorizons.galaxia.registry.dimension.builder.EffectBuilder;
import com.gtnewhorizons.galaxia.registry.dimension.provider.WorldProviderBuilder;
import com.gtnewhorizons.galaxia.registry.dimension.sky.SkyBuilder;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.CraterFeature;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.CrystalClusterFeature;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.FluidSpringFeature;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.StalactiteFeature;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.StratificationPreset;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.TerrainConfiguration;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.TerrainPreset;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.WorldGenGalaxiaCave;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.WorldGenGalaxiaSurface;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.WorldGenGalaxiaWall;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.EnumTiers;
import com.gtnewhorizons.galaxia.utility.BiomeIdOffsetter;

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
                    .pressure(0)
                    .build())
            .tier(EnumTiers.TIER_1);
    }

    /**
     * Configures the world provider to add the correct biomes and settings
     *
     * @param builder The world provider builder being configured
     */
    @Override
    protected void configureProvider(WorldProviderBuilder builder) {
        builder.sky(true)
            .fog(0, 0, 0)
            .skyColor(0, 0, 0.001f)
            .avgGround(80)
            .biome(
                createLandBiome(
                    "Theia Hills",
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
                        .build()),
                0,
                0)
            .biome(
                createLandBiome(
                    "Theia Mountains",
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
                        .build()),
                0,
                1)
            .biome(
                createOceanBiome(
                    "Theia Small Volcanoes",
                    TerrainConfiguration.builder()
                        .feature(TerrainPreset.BASE_HEIGHT)
                        .height(32)
                        .endFeature()
                        .feature(TerrainPreset.SHIELD_VOLCANOES)
                        .width(2)
                        .height(16)
                        .endFeature()
                        .build()),
                1,
                0)
            .biome(
                createOceanBiome(
                    "Theia Big Volcanoes",
                    TerrainConfiguration.builder()
                        .feature(TerrainPreset.BASE_HEIGHT)
                        .height(32)
                        .endFeature()
                        .feature(TerrainPreset.SHIELD_VOLCANOES)
                        .width(4)
                        .height(64)
                        .endFeature()
                        .build()),
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
                m -> m.texture(EnumTextures.HEMATERIA.get())
                    .size(6f)
                    .distance(90.0)
                    .inclination(10.0f)
                    .period(3000L))
            .addBody(
                m -> m.texture(EnumTextures.HEMATERIA.get())
                    .size(6f)
                    .distance(90.0)
                    .inclination(20.0f)
                    .period(1200L))
            .addBody(
                m -> m.texture(EnumTextures.HEMATERIA.get())
                    .size(6f)
                    .distance(90.0)
                    .inclination(40.0f)
                    .period(12000L))
            .addBody(
                m -> m.texture(EnumTextures.HEMATERIA.get())
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
    protected static BiomeGenBase createLandBiome(String name, TerrainConfiguration terrainConfiguration) {
        return new BiomeGenBuilder(BiomeIdOffsetter.getBiomeId()).name(name)
            .height(0.1F, 0.11F)
            .temperature(0.4F)
            .rainfall(0.99F)
            .topBlock(PlanetBlocks.THEIA_REGOLITH)
            .fillerBlocks(
                new StratificationPreset(PlanetBlocks.THEIA_ANDESITE).addStrataLayer(Blocks.bedrock, 0, 0)
                    .addStrataLayer(PlanetBlocks.THEIA_ANORTHOSITE, 1, 32))
            .generateCaves(true)
            .surfaceFeature(
                new WorldGenGalaxiaSurface(
                    8,
                    new Block[] { PlanetBlocks.THEIA_REGOLITH, PlanetBlocks.THEIA_BASALT },
                    new CraterFeature(PlanetBlocks.THEIA_TEKTITE)))
            .caveFeature(
                new WorldGenGalaxiaCave(
                    64,
                    4,
                    32,
                    new Block[] { PlanetBlocks.THEIA_ANORTHOSITE },
                    new StalactiteFeature(PlanetBlocks.THEIA_ANORTHOSITE)))
            .caveFeature(
                new WorldGenGalaxiaCave(
                    64,
                    32,
                    64,
                    new Block[] { PlanetBlocks.THEIA_ANDESITE },
                    new StalactiteFeature(PlanetBlocks.THEIA_ANDESITE)))
            .caveFeature(
                new WorldGenGalaxiaCave(
                    32,
                    4,
                    32,
                    new Block[] { PlanetBlocks.THEIA_ANORTHOSITE },
                    new CrystalClusterFeature(GalaxiaBlocksEnum.BLOCK_OF_CINNABAR.get())))
            .wallFeature(
                new WorldGenGalaxiaWall(
                    2,
                    new Block[] { PlanetBlocks.THEIA_ANDESITE, PlanetBlocks.THEIA_ANORTHOSITE },
                    new FluidSpringFeature(PlanetBlocks.LIQUID_MERCURY.getBlock())))
            .terrain(terrainConfiguration)
            .ocean(PlanetBlocks.THEIA_OBSIDIAN, PlanetBlocks.THEIA_BASALT, 1, PlanetBlocks.THEIA_OBSIDIAN, 1)
            .surfaceThickness(4)
            .build();
    }

    protected static BiomeGenBase createOceanBiome(String name, TerrainConfiguration terrainConfiguration) {
        return new BiomeGenBuilder(BiomeIdOffsetter.getBiomeId()).name(name)
            .height(0.1F, 0.11F)
            .temperature(0.4F)
            .rainfall(0.99F)
            .topBlock(PlanetBlocks.THEIA_BASALT)
            .fillerBlocks(
                new StratificationPreset(PlanetBlocks.THEIA_BASALT).addStrataLayer(Blocks.bedrock, 0, 0)
                    .addStrataLayer(PlanetBlocks.THEIA_GABBRO, 1, 32))
            .generateCaves(false)
            .surfaceFeature(
                new WorldGenGalaxiaSurface(
                    32,
                    new Block[] { PlanetBlocks.THEIA_REGOLITH, PlanetBlocks.THEIA_BASALT },
                    new CraterFeature(PlanetBlocks.THEIA_TEKTITE)))
            .terrain(terrainConfiguration)
            .ocean(PlanetBlocks.THEIA_OBSIDIAN, PlanetBlocks.THEIA_BASALT, 56, PlanetBlocks.THEIA_OBSIDIAN, 1)
            .oceanCracks(0.3F, PlanetBlocks.THEIA_MAGMA, 4)
            .surfaceThickness(4)
            .build();
    }
}
