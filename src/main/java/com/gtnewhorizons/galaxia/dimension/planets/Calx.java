package com.gtnewhorizons.galaxia.dimension.planets;

import net.minecraft.init.Blocks;
import net.minecraft.world.WorldProvider;

import com.gtnewhorizons.galaxia.block.GalaxiaBlockBase;
import com.gtnewhorizons.galaxia.dimension.BiomeGenBuilder;
import com.gtnewhorizons.galaxia.dimension.BiomeGenSpace;
import com.gtnewhorizons.galaxia.dimension.DimensionBuilder;
import com.gtnewhorizons.galaxia.dimension.PlanetEnum;
import com.gtnewhorizons.galaxia.dimension.WorldProviderBuilder;
import com.gtnewhorizons.galaxia.dimension.WorldProviderSpace;
import com.gtnewhorizons.galaxia.dimension.sky.SkyBuilder;

public class Calx extends BasePlanet {

    @Override
    protected DimensionBuilder createBuilder() {
        return super.createBuilder().gravity(2)
            .airResistance(1.7)
            .sky(
                SkyBuilder.builder()
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
                            .period(6000L)));
    }

    public static final PlanetEnum ENUM = PlanetEnum.CALX;

    @Override
    protected PlanetEnum getPlanetEnum() {
        return ENUM;
    }

    @Override
    protected Class<? extends WorldProvider> getProviderClass() {
        return WorldProviderCalx.class;
    }

    public static class WorldProviderCalx extends WorldProviderSpace {

        public WorldProviderCalx() {
            WorldProviderBuilder.configure(this)
                .sky(true)
                .fog(0.15f, 0.1f, 0.3f)
                .avgGround(80)
                .biome(new BiomeGenCalx(100))
                .name(ENUM)
                .build();
        }
    }

    public static class BiomeGenCalx extends BiomeGenSpace {

        public BiomeGenCalx(int id) {
            super(
                id,
                new BiomeGenBuilder(id).name("Calx Surface")
                    .height(0.1F, 0.11F)
                    .temperature(0.4F)
                    .rainfall(0.99F)
                    .topBlock(GalaxiaBlockBase.get(PlanetEnum.CALX))
                    .fillerBlock(Blocks.brick_block));
        }
    }
}
