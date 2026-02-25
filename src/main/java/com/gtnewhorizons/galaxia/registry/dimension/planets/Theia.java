package com.gtnewhorizons.galaxia.registry.dimension.planets;

import net.minecraft.init.Blocks;
import net.minecraft.world.biome.BiomeGenBase;

import com.gtnewhorizons.galaxia.registry.block.base.BlockVariant;
import com.gtnewhorizons.galaxia.registry.block.base.GalaxiaBlock;
import com.gtnewhorizons.galaxia.registry.dimension.DimensionEnum;
import com.gtnewhorizons.galaxia.registry.dimension.biome.BiomeGenBuilder;
import com.gtnewhorizons.galaxia.registry.dimension.builder.DimensionBuilder;
import com.gtnewhorizons.galaxia.registry.dimension.builder.EffectBuilder;
import com.gtnewhorizons.galaxia.registry.dimension.provider.WorldProviderBuilder;
import com.gtnewhorizons.galaxia.registry.dimension.sky.SkyBuilder;
import com.gtnewhorizons.galaxia.utility.BiomeIdOffsetter;
import com.gtnewhorizons.galaxia.utility.BlockMeta;
import com.gtnewhorizons.galaxia.worldgen.WorldGenCrater;

/**
 * The class holding all data related to the dimension Theia
 */
public class Theia extends BasePlanet {

    private static int biomeIdOffset = 0;

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
        return builder.gravity(2)
            .airResistance(1.7)
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
            .fog(0.15f, 0.1f, 0.3f)
            .avgGround(80)
            .biome(
                createBiome(
                    "Theia Surface",
                    GalaxiaBlock.get(DimensionEnum.THEIA, BlockVariant.REGOLITH.suffix()),
                    true),
                0,
                0)
            .biome(
                createBiome(
                    "Theia Rough Surface",
                    GalaxiaBlock.get(DimensionEnum.THEIA, BlockVariant.ANORTHOSITE.suffix()),
                    false),
                1,
                0)
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
    protected static BiomeGenBase createBiome(String name, BlockMeta topBlock, boolean generateCaves) {
        return new BiomeGenBuilder(BiomeIdOffsetter.getBiomeId()).name(name)
            .height(0.1F, 0.11F)
            .temperature(0.4F)
            .rainfall(0.99F)
            .topBlock(topBlock)
            .fillerBlock(Blocks.brick_block)
            .generateCaves(generateCaves)
            .surfaceFeature(
                new WorldGenCrater(
                    4,
                    new BlockMeta[] { GalaxiaBlock.get(DimensionEnum.THEIA, BlockVariant.REGOLITH.suffix()),
                        GalaxiaBlock.get(DimensionEnum.THEIA, BlockVariant.ANORTHOSITE.suffix()) }))
            .build();
    }
}
