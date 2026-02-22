package com.gtnewhorizons.galaxia.dimension.planets;

import net.minecraft.init.Blocks;
import net.minecraft.world.biome.BiomeGenBase;

import com.gtnewhorizons.galaxia.block.BlockVariant;
import com.gtnewhorizons.galaxia.block.GalaxiaBlockBase;
import com.gtnewhorizons.galaxia.dimension.BiomeGenBuilder;
import com.gtnewhorizons.galaxia.dimension.DimensionBuilder;
import com.gtnewhorizons.galaxia.dimension.DimensionEnum;
import com.gtnewhorizons.galaxia.dimension.EffectBuilder;
import com.gtnewhorizons.galaxia.dimension.WorldProviderBuilder;
import com.gtnewhorizons.galaxia.dimension.sky.SkyBuilder;
import com.gtnewhorizons.galaxia.utility.BlockMeta;

public class Theia extends BasePlanet {

    public static final DimensionEnum ENUM = DimensionEnum.THEIA;

    @Override
    public DimensionEnum getPlanetEnum() {
        return ENUM;
    }

    @Override
    protected DimensionBuilder customizeDimension(DimensionBuilder builder) {
        return builder.gravity(2)
            .airResistance(1.7)
            .mass(0.012)
            .radius(0.27)
            .orbitalRadius(1 * earthRadiusToAU)
            .sky(buildSky())
            .effects(
                new EffectBuilder().baseTemp(273)
                    .oxygenPercent(0)
                    .pressure(1));
    }

    @Override
    protected void configureProvider(WorldProviderBuilder builder) {
        builder.sky(true)
            .fog(0.15f, 0.1f, 0.3f)
            .avgGround(80)
            .biome(
                createBiome("Theia Surface", GalaxiaBlockBase.get(DimensionEnum.THEIA, BlockVariant.REGOLITH.suffix)),
                0,
                0)
            .biome(
                createBiome(
                    "Theia Rough Surface",
                    GalaxiaBlockBase.get(DimensionEnum.THEIA, BlockVariant.ANORTHOSITE.suffix)),
                1,
                0)
            .name(ENUM)
            .build();
    }

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
                m -> m.texture("galaxia:textures/environment/phobos.png")
                    .size(6f)
                    .distance(90.0)
                    .inclination(10.0f)
                    .period(3000L))
            .addBody(
                m -> m.texture("galaxia:textures/environment/phobos.png")
                    .size(6f)
                    .distance(90.0)
                    .inclination(20.0f)
                    .period(1200L))
            .addBody(
                m -> m.texture("galaxia:textures/environment/phobos.png")
                    .size(6f)
                    .distance(90.0)
                    .inclination(40.0f)
                    .period(12000L))
            .addBody(
                m -> m.texture("galaxia:textures/environment/phobos.png")
                    .size(6f)
                    .distance(90.0)
                    .inclination(30.0f)
                    .period(6000L));
    }

    protected static BiomeGenBase createBiome(String name, BlockMeta topBlock) {
        return new BiomeGenBuilder(100).name(name)
            .height(0.1F, 0.11F)
            .temperature(0.4F)
            .rainfall(0.99F)
            .topBlock(topBlock)
            .fillerBlock(Blocks.brick_block)
            .build();
    }
}
