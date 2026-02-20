package com.gtnewhorizons.galaxia.dimension.planets;

import net.minecraft.init.Blocks;
import net.minecraft.world.biome.BiomeGenBase;

import com.gtnewhorizons.galaxia.block.GalaxiaBlockBase;
import com.gtnewhorizons.galaxia.dimension.BiomeGenBuilder;
import com.gtnewhorizons.galaxia.dimension.DimensionBuilder;
import com.gtnewhorizons.galaxia.dimension.DimensionEnum;
import com.gtnewhorizons.galaxia.dimension.EffectBuilder;
import com.gtnewhorizons.galaxia.dimension.WorldProviderBuilder;
import com.gtnewhorizons.galaxia.dimension.sky.SkyBuilder;

public class Calx extends BasePlanet {

    public static final DimensionEnum ENUM = DimensionEnum.CALX;

    @Override
    public DimensionEnum getPlanetEnum() {
        return ENUM;
    }

    @Override
    protected DimensionBuilder customizeDimension(DimensionBuilder builder) {
        return builder.gravity(2)
            .airResistance(1.7)
            .mass((int) (7 * Math.pow(10, 22)))
            .orbitalRadius((int) (1.5 * Math.pow(10, 11)))
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
            .biome(createBiome())
            .name(ENUM);
    }

    protected SkyBuilder buildSky() {
        return SkyBuilder.builder()
            .sun(
                s -> s.texture("minecraft:textures/environment/sun.png")
                    .size(30f)
                    .distance(100.0)
                    .inclination(45)
                    .period(24000L)
                    .emissive(true))
            .moon(
                m -> m.texture("minecraft:textures/environment/moon_phases.png")
                    .size(20f)
                    .distance(-100.0)
                    .inclination(60)
                    .period(23151L)
                    .phases())
            .moon(
                m -> m.texture("galaxia:textures/environment/phobos.png")
                    .size(6f)
                    .distance(90.0)
                    .inclination(10.0f)
                    .period(3000L))
            .moon(
                m -> m.texture("galaxia:textures/environment/phobos.png")
                    .size(6f)
                    .distance(90.0)
                    .inclination(20.0f)
                    .period(1200L))
            .moon(
                m -> m.texture("galaxia:textures/environment/phobos.png")
                    .size(6f)
                    .distance(90.0)
                    .inclination(40.0f)
                    .period(12000L))
            .moon(
                m -> m.texture("galaxia:textures/environment/phobos.png")
                    .size(6f)
                    .distance(90.0)
                    .inclination(30.0f)
                    .period(6000L));
    }

    protected static BiomeGenBase createBiome() {
        return new BiomeGenBuilder(100).name("Calx Surface")
            .height(0.1F, 0.11F)
            .temperature(0.4F)
            .rainfall(0.99F)
            .topBlock(GalaxiaBlockBase.get(DimensionEnum.CALX))
            .fillerBlock(Blocks.brick_block)
            .build();
    }
}
