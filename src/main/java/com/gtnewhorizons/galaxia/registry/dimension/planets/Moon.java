package com.gtnewhorizons.galaxia.registry.dimension.planets;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.biome.BiomeGenBase;

import com.gtnewhorizons.galaxia.client.EnumTextures;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaBlocksEnum;
import com.gtnewhorizons.galaxia.registry.block.PlanetBlocks;
import com.gtnewhorizons.galaxia.registry.dimension.DimensionEnum;
import com.gtnewhorizons.galaxia.registry.dimension.biome.BiomeGenBuilder;
import com.gtnewhorizons.galaxia.registry.dimension.biome.BiomeIdOffsetter;
import com.gtnewhorizons.galaxia.registry.dimension.builder.DimensionBuilder;
import com.gtnewhorizons.galaxia.registry.dimension.builder.EffectBuilder;
import com.gtnewhorizons.galaxia.registry.dimension.cave.CaveShapeCracks;
import com.gtnewhorizons.galaxia.registry.dimension.cave.CaveShapeTubes;
import com.gtnewhorizons.galaxia.registry.dimension.provider.WorldProviderBuilder;
import com.gtnewhorizons.galaxia.registry.dimension.sky.SkyBuilder;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.StratificationPreset;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.TerrainConfiguration;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.TerrainPreset;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.feature.CraterFeature;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.feature.CrystalClusterFeature;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.feature.FluidSpringFeature;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.feature.GeodeFeature;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.feature.StalactiteFeature;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.locationrule.LocationRuleGalaxiaCave;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.locationrule.LocationRuleGalaxiaSurface;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.locationrule.LocationRuleGalaxiaWall;
import com.gtnewhorizons.galaxia.registry.rocketmodules.utility.EnumTiers;

/**
 * The class holding all data related to the dimension Moon
 */
public class Moon extends BasePlanet {

    public static final DimensionEnum ENUM = DimensionEnum.MOON;

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
            .addValidSpaceStationBlocks(
                GalaxiaBlocksEnum.RUSTY_SCAFFOLDING.get(),
                GalaxiaBlocksEnum.SPACE_STATION_BLOCK.get(),
                GalaxiaBlocksEnum.SPACE_STATION_PANEL.get(),
                GalaxiaBlocksEnum.SPACE_STATION_GLASS.get())
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
        BiomeGenBase border = createOceanBiome(
            "Moon Ocean Edge",
            TerrainConfiguration.builder()
                .feature(TerrainPreset.BASE_HEIGHT)
                .height(50)
                .endFeature()
                .feature(TerrainPreset.MOUNTAIN_RANGES)
                .width(4)
                .height(32)
                .endFeature()
                .build());
        BiomeGenBase smallVolcanoes = createOceanBiome(
            "Moon Small Volcanoes",
            TerrainConfiguration.builder()
                .feature(TerrainPreset.BASE_HEIGHT)
                .height(32)
                .endFeature()
                .feature(TerrainPreset.SHIELD_VOLCANOES)
                .replacementBlock(PlanetBlocks.MOON_MAGMA)
                .width(2)
                .height(32)
                .endFeature()
                .build());
        BiomeGenBase bigVolcanoes = createOceanBiome(
            "Moon Big Volcanoes",
            TerrainConfiguration.builder()
                .feature(TerrainPreset.BASE_HEIGHT)
                .height(32)
                .endFeature()
                .feature(TerrainPreset.SHIELD_VOLCANOES)
                .replacementBlock(PlanetBlocks.MOON_MAGMA)
                .width(4)
                .height(64)
                .endFeature()
                .build());
        BiomeGenBase hills = createLandBiome(
            "Moon Hills",
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
                .build());
        BiomeGenBase mountains = createLandBiome(
            "Moon Mountains",
            TerrainConfiguration.builder()
                .feature(TerrainPreset.BASE_HEIGHT)
                .height(64)
                .endFeature()
                .feature(TerrainPreset.MOUNTAIN_RANGES)
                .width(3)
                .height(16)
                .endFeature()
                .feature(TerrainPreset.MOUNTAIN_RANGES)
                .width(8)
                .height(128)
                .endFeature()
                .build());
        builder.sky(true)
            .fog(0, 0, 0)
            .skyColor(0, 0, 0.001f)
            .avgGround(80)
            // Inner volcanic biomes
            .biome(smallVolcanoes, 1, 1)
            .biome(bigVolcanoes, 1, 2)
            .biome(bigVolcanoes, 2, 1)
            .biome(smallVolcanoes, 2, 2)
            // Border
            .biome(border, 0, 0)
            .biome(border, 1, 0)
            .biome(border, 2, 0)
            .biome(border, 3, 0)
            .biome(border, 0, 1)
            .biome(border, 3, 1)
            .biome(border, 0, 2)
            .biome(border, 3, 2)
            .biome(border, 0, 3)
            .biome(border, 1, 3)
            .biome(border, 2, 3)
            .biome(border, 3, 3)
            // Hills
            .biome(hills, 4, 0)
            .biome(hills, 5, 0)
            .biome(hills, 6, 0)
            .biome(hills, 7, 0)
            .biome(hills, 4, 1)
            .biome(hills, 7, 1)
            .biome(hills, 4, 2)
            .biome(hills, 7, 2)
            .biome(hills, 4, 3)
            .biome(hills, 5, 3)
            .biome(hills, 6, 3)
            .biome(hills, 7, 3)
            // Mountains
            .biome(mountains, 5, 1)
            .biome(mountains, 5, 2)
            .biome(mountains, 6, 1)
            .biome(mountains, 6, 2)
            // Finish
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
                m -> m.texture(EnumTextures.MARS.get())
                    .size(6f)
                    .distance(90.0)
                    .inclination(10.0f)
                    .period(3000L))
            .addBody(
                m -> m.texture(EnumTextures.MARS.get())
                    .size(6f)
                    .distance(90.0)
                    .inclination(20.0f)
                    .period(1200L))
            .addBody(
                m -> m.texture(EnumTextures.MARS.get())
                    .size(6f)
                    .distance(90.0)
                    .inclination(40.0f)
                    .period(12000L))
            .addBody(
                m -> m.texture(EnumTextures.MARS.get())
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
            .topBlock(PlanetBlocks.MOON_REGOLITH)
            .fillerBlocks(
                new StratificationPreset(PlanetBlocks.MOON_ANDESITE).addStrataLayer(Blocks.bedrock, 0, 0)
                    .addStrataLayer(PlanetBlocks.MOON_ANORTHOSITE, 1, 32))
            .caveShape(new CaveShapeCracks())
            .surfaceFeature(
                new LocationRuleGalaxiaSurface(
                    8,
                    new Block[] { PlanetBlocks.MOON_REGOLITH, PlanetBlocks.MOON_BASALT },
                    new CraterFeature(PlanetBlocks.MOON_TEKTITE),
                    true))
            .caveFeature(
                new LocationRuleGalaxiaCave(
                    64,
                    4,
                    32,
                    new Block[] { PlanetBlocks.MOON_ANORTHOSITE },
                    new StalactiteFeature(PlanetBlocks.MOON_ANORTHOSITE)))
            .caveFeature(
                new LocationRuleGalaxiaCave(
                    64,
                    32,
                    64,
                    new Block[] { PlanetBlocks.MOON_ANDESITE },
                    new StalactiteFeature(PlanetBlocks.MOON_ANDESITE)))
            .caveFeature(
                new LocationRuleGalaxiaCave(
                    32,
                    4,
                    32,
                    new Block[] { PlanetBlocks.MOON_ANORTHOSITE },
                    new CrystalClusterFeature(GalaxiaBlocksEnum.BLOCK_OF_CINNABAR.get())))
            .wallFeature(
                new LocationRuleGalaxiaWall(
                    2,
                    new Block[] { PlanetBlocks.MOON_ANDESITE, PlanetBlocks.MOON_ANORTHOSITE },
                    new FluidSpringFeature(PlanetBlocks.LIQUID_MERCURY.getBlock()),
                    8,
                    256))
            .wallFeature(
                new LocationRuleGalaxiaWall(
                    4,
                    new Block[] { PlanetBlocks.MOON_ANORTHOSITE, PlanetBlocks.MOON_ANDESITE },
                    new GeodeFeature(Blocks.glass, Blocks.stained_glass),
                    24,
                    96))
            .terrain(terrainConfiguration)
            .ocean(PlanetBlocks.MOON_OBSIDIAN, PlanetBlocks.MOON_BASALT, 1, PlanetBlocks.MOON_OBSIDIAN, 1)
            .surfaceThickness(4)
            .build();
    }

    protected static BiomeGenBase createOceanBiome(String name, TerrainConfiguration terrainConfiguration) {
        return new BiomeGenBuilder(BiomeIdOffsetter.getBiomeId()).name(name)
            .height(0.1F, 0.11F)
            .temperature(0.4F)
            .rainfall(0.99F)
            .topBlock(PlanetBlocks.MOON_BASALT)
            .fillerBlocks(
                new StratificationPreset(PlanetBlocks.MOON_BASALT).addStrataLayer(Blocks.bedrock, 0, 0)
                    .addStrataLayer(PlanetBlocks.MOON_GABBRO, 1, 32))
            .caveShape(new CaveShapeTubes((byte) 16, (byte) 4, (short) 100))
            .surfaceFeature(
                new LocationRuleGalaxiaSurface(
                    32,
                    new Block[] { PlanetBlocks.MOON_REGOLITH, PlanetBlocks.MOON_BASALT },
                    new CraterFeature(PlanetBlocks.MOON_TEKTITE)))
            .terrain(terrainConfiguration)
            .ocean(PlanetBlocks.MOON_OBSIDIAN, PlanetBlocks.MOON_BASALT, 56, PlanetBlocks.MOON_OBSIDIAN, 1)
            .oceanCracks(0.3F, PlanetBlocks.MOON_MAGMA, 4)
            .surfaceThickness(4)
            .build();
    }
}
