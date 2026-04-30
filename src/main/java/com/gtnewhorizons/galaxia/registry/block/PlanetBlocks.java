package com.gtnewhorizons.galaxia.registry.block;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraftforge.fluids.Fluid;

import com.gtnewhorizons.galaxia.registry.block.planet.PlanetBlockBuilder;
import com.gtnewhorizons.galaxia.registry.block.planet.fluid.FluidFiniteBuilder;
import com.gtnewhorizons.galaxia.registry.items.GalaxiaItemList;

public final class PlanetBlocks {

    // spotless:off
    /*
     * MOON blocks
     */
    public static final Block MOON_REGOLITH = PlanetBlockBuilder.create("moon/moon_regolith")
            .falling()
            .dropSelf()
            .hardness(1.0F)
            .harvest(1)
            .build();

    public static final Block MOON_MAGMA = PlanetBlockBuilder.create("moon/moon_magma")
            .dropSelf()
            .hardness(0.5F)
            .harvest(0)
            .build();

    public static final Block MOON_GABBRO = PlanetBlockBuilder.create("moon/moon_gabbro")
            .dropSelf()
            .hardness(1.5F)
            .harvest(1)
            .build();

    public static final Block MOON_BRECCIA = PlanetBlockBuilder.create("moon/moon_breccia")
            .dropSelf()
            .hardness(1.5F)
            .harvest(1)
            .build();

    public static final Block MOON_BASALT = PlanetBlockBuilder.create("moon/moon_basalt")
            .dropSelf()
            .hardness(1.5F)
            .harvest(1)
            .build();

    public static final Block MOON_ANORTHOSITE = PlanetBlockBuilder.create("moon/moon_anorthosite")
            .dropSelf()
            .hardness(1.5F)
            .harvest(1)
            .build();

    public static final Block MOON_ANDESITE = PlanetBlockBuilder.create("moon/moon_andesite")
            .dropSelf()
            .hardness(1.5F)
            .harvest(1)
            .build();

    public static final Block MOON_OBSIDIAN = PlanetBlockBuilder.create("moon/moon_obsidian")
            .dropSelf()
            .hardness(50.0F)
            .harvest(3)
            .build();

    public static final Block MOON_TEKTITE = PlanetBlockBuilder.create("moon/moon_tektite")
            .drop(GalaxiaItemList.MOON_TEKTITE_SHARD)
            .hardness(2.0F)
            .harvest(1)
            .build();

    /*
     * MARS blocks
     */
    public static final Block MARS_REGOLITH = PlanetBlockBuilder.create("mars/mars_regolith")
            .falling()
            .dropSelf()
            .hardness(0.5F)
            .shovel()
            .harvest(0)
            .build();

    public static final Block MARS_ANDESITE = PlanetBlockBuilder.create("mars/mars_andesite")
            .dropSelf()
            .hardness(1.5F)
            .harvest(1)
            .build();

    public static final Block MARS_BASALT = PlanetBlockBuilder.create("mars/mars_basalt")
            .dropSelf()
            .hardness(1.5F)
            .harvest(1)
            .build();

    public static final Block MARS_SNOW = PlanetBlockBuilder.create("mars/mars_snow")
            .falling()
            .dropSelf()
            .hardness(0.1F)
            .shovel()
            .harvest(0)
            .build();

    public static final Block MARS_ANORTHOSITE = PlanetBlockBuilder.create("mars/mars_anorthosite")
            .drop(GalaxiaItemList.DUST_MARS)
            .hardness(1.5F)
            .harvest(1)
            .build();

    public static final Block MARS_TEKTITE = PlanetBlockBuilder.create("mars/mars_tektite")
            .drop(GalaxiaItemList.MARS_TEKTITE_SHARD)
            .hardness(2.0F)
            .harvest(1)
            .build();

    public static final Block MARS_ICE = PlanetBlockBuilder.create("mars/mars_ice")
            .transparent()
            .drop(GalaxiaItemList.MARS_ICE_CUBES)
            .dropAmount(2, 4)
            .hardness(0.5F)
            .harvest(1)
            .build();

    public static final Block MARS_DENSE_ICE = PlanetBlockBuilder.create("mars/mars_dense_ice")
            .transparent()
            .drop(GalaxiaItemList.MARS_ICE_CUBES)
            .dropAmount(4, 7)
            .hardness(0.5F)
            .harvest(1)
            .build();

    public static final Block MARS_MAGMA = PlanetBlockBuilder.create("mars/mars_magma")
            .dropSelf()
            .hardness(0.5F)
            .harvest(0)
            .build();

    public static final Block MARS_SAND = PlanetBlockBuilder.create("mars/mars_sand")
            .falling()
            .dropSelf()
            .hardness(0.5F)
            .shovel()
            .harvest(0)
            .build();

    public static final Block MARS_SANDSTONE = PlanetBlockBuilder.create("mars/mars_sandstone")
            .dropSelf()
            .hardness(0.8F)
            .harvest(0)
            .build();

    public static final Block MARS_TUFF = PlanetBlockBuilder.create("mars/mars_tuff")
            .dropSelf()
            .hardness(1.5F)
            .harvest(1)
            .build();

    public static final Block MARS_PERIDOTITE = PlanetBlockBuilder.create("mars/mars_peridotite")
            .dropSelf()
            .hardness(2.7F)
            .harvest(1)
            .build();

    public static final Block MARS_RHYOLITE = PlanetBlockBuilder.create("mars/mars_rhyolite")
            .falling()
            .dropSelf()
            .hardness(0.7F)
            .shovel()
            .harvest(0)
            .build();

    /*
     * PANSPIRA blocks
     */
    public static final Block PANSPIRA_REGOLITH = PlanetBlockBuilder.create("panspira/panspira_regolith")
            .falling()
            .dropSelf()
            .hardness(0.5F)
            .shovel()
            .harvest(0)
            .build();

    public static final Block PANSPIRA_ANDESITE = PlanetBlockBuilder.create("panspira/panspira_andesite")
            .dropSelf()
            .hardness(1.5F)
            .harvest(1)
            .build();

    public static final Block PANSPIRA_SNOW = PlanetBlockBuilder.create("panspira/panspira_snow")
            .falling()
            .dropSelf()
            .hardness(0.1F)
            .shovel()
            .harvest(0)
            .build();

    public static final Block PANSPIRA_STONE = PlanetBlockBuilder.create("panspira/panspira_stone")
            .dropSelf()
            .hardness(1.5F)
            .harvest(1)
            .build();

    public static final Block PANSPIRA_SOIL = PlanetBlockBuilder.create("panspira/panspira_soil")
            .dropSelf()
            .hardness(0.6F)
            .shovel()
            .harvest(0)
            .build();

    public static final Block PANSPIRA_MAGMA = PlanetBlockBuilder.create("panspira/panspira_magma")
            .dropSelf()
            .hardness(0.5F)
            .harvest(0)
            .build();

    /*
     * TENEBRAE blocks
     */

    public static final Block TENEBRAE_BASALT = PlanetBlockBuilder.create("tenebrae/tenebrae_basalt")
            .dropSelf()
            .hardness(1.5F)
            .harvest(1)
            .build();

    public static final Block TENEBRAE_MAGMA = PlanetBlockBuilder.create("tenebrae/tenebrae_magma")
            .dropSelf()
            .hardness(0.5F)
            .harvest(0)
            .build();

    public static final Block TENEBRAE_ANDESITE = PlanetBlockBuilder.create("tenebrae/tenebrae_andesite")
            .dropSelf()
            .hardness(1.5F)
            .harvest(1)
            .build();

    public static final Block TENEBRAE_REGOLITH = PlanetBlockBuilder.create("tenebrae/tenebrae_regolith")
            .falling()
            .dropSelf()
            .hardness(0.5F)
            .shovel()
            .harvest(0)
            .build();

    public static final Block TENEBRAE_ASH = PlanetBlockBuilder.create("tenebrae/tenebrae_ash")
            .falling()
            .dropSelf()
            .hardness(0.5F)
            .shovel()
            .harvest(0)
            .build();

    public static final Block TENEBRAE_PYRITE_REGOLITH = PlanetBlockBuilder.create("tenebrae/tenebrae_pyrite_regolith")
            .falling()
            .dropSelf()
            .hardness(0.7F)
            .shovel()
            .harvest(0)
            .build();

    public static final Block TENEBRAE_SULFURIC_REGOLITH = PlanetBlockBuilder
            .create("tenebrae/tenebrae_sulfuric_regolith")
            .falling()
            .dropSelf()
            .hardness(0.7F)
            .shovel()
            .harvest(0)
            .build();

    public static final Block TENEBRAE_RHYOLITE = PlanetBlockBuilder.create("tenebrae/tenebrae_rhyolite")
            .falling()
            .dropSelf()
            .hardness(0.7F)
            .shovel()
            .harvest(0)
            .build();

    public static final Block TENEBRAE_LATITE = PlanetBlockBuilder.create("tenebrae/tenebrae_latite")
            .dropSelf()
            .hardness(2.0F)
            .harvest(1)
            .build();

    public static final Block TENEBRAE_BRIMSTONE = PlanetBlockBuilder.create("tenebrae/tenebrae_brimstone")
            .dropSelf()
            .hardness(2.0F)
            .harvest(1)
            .build();

    /*
     * FROZEN BELT blocks
     */

    public static final Block FROZEN_BELT_ICE = PlanetBlockBuilder.create("frozen_belt/frozen_belt_ice")
            .dropSelf()
            .hardness(0.5F)
            .harvest(1)
            .build();

    public static final Block FROZEN_BELT_BRECCIA = PlanetBlockBuilder.create("frozen_belt/frozen_belt_breccia")
            .dropSelf()
            .hardness(1.5F)
            .harvest(1)
            .build();

    public static final Block FROZEN_BELT_GABBRO = PlanetBlockBuilder.create("frozen_belt/frozen_belt_gabbro")
            .dropSelf()
            .hardness(1.5F)
            .harvest(1)
            .build();

    public static final Block FROZEN_BELT_BASALT = PlanetBlockBuilder.create("frozen_belt/frozen_belt_basalt")
            .dropSelf()
            .hardness(1.5F)
            .harvest(1)
            .build();

    public static final Block FROZEN_BELT_ANDESITE = PlanetBlockBuilder.create("frozen_belt/frozen_belt_andesite")
            .dropSelf()
            .hardness(1.5F)
            .harvest(1)
            .build();

    public static final Block FROZEN_BELT_ANORTHOSITE = PlanetBlockBuilder.create("frozen_belt/frozen_belt_anorthosite")
            .dropSelf()
            .hardness(1.5F)
            .harvest(1)
            .build();

    /*
     * ASTEROIDS
     */

    public static final Block ASTEROID_SHELL_BLACK = PlanetBlockBuilder.create("asteroid_belt/black_asteroid_shell")
            .dropSelf()
            .hardness(2F)
            .harvest(2)
            .build();

    public static final Block ASTEROID_SHELL_FROZEN = PlanetBlockBuilder.create("asteroid_belt/frozen_asteroid_shell")
            .dropSelf()
            .hardness(2F)
            .harvest(2)
            .build();

    public static final Block ASTEROID_SHELL_GREY = PlanetBlockBuilder.create("asteroid_belt/grey_asteroid_shell")
            .dropSelf()
            .hardness(2F)
            .harvest(2)
            .build();

    public static final Block ASTEROID_SHELL_NAQUADAH = PlanetBlockBuilder
            .create("asteroid_belt/naquadah_asteroid_shell")
            .dropSelf()
            .hardness(4F)
            .harvest(2)
            .build();

    public static final Block ASTEROID_SHELL_OLIVINE = PlanetBlockBuilder.create("asteroid_belt/olivine_asteroid_shell")
            .dropSelf()
            .hardness(3F)
            .harvest(2)
            .build();

    public static final Block ASTEROID_SHELL_RED = PlanetBlockBuilder.create("asteroid_belt/red_asteroid_shell")
            .dropSelf()
            .hardness(2F)
            .harvest(2)
            .build();

    public static final Block ASTEROID_SHELL_ROCKY = PlanetBlockBuilder.create("asteroid_belt/rocky_asteroid_shell")
            .dropSelf()
            .hardness(2F)
            .harvest(2)
            .build();

    public static final Block ASTEROID_SHELL_SILVER = PlanetBlockBuilder.create("asteroid_belt/silver_asteroid_shell")
            .dropSelf()
            .hardness(2F)
            .harvest(2)
            .build();

    /*
     * FLUIDS
     */

    public static final Fluid LIQUID_LAHAR = FluidFiniteBuilder.create("fluids/lahar/lahar")
            .buildAndRegister()
            .getFluid();

    public static final Fluid LIQUID_MERCURY = FluidFiniteBuilder.create("fluids/mercury/liquid_mercury")
            .buildAndRegister()
            .getFluid();

    public static final Fluid LIQUID_RESIN = FluidFiniteBuilder.create("fluids/resin/molten_resin")
            .lightLevel(1)
            .material(Material.lava)
            .buildAndRegister()
            .getFluid();

    public static final Fluid LAVA_TENEBRAE = FluidFiniteBuilder.create("fluids/tenebrae_lava/tenebrae_lava")
            .lightLevel(1)
            .material(Material.lava)
            .buildAndRegister()
            .getFluid();

    // spotless:on

    public static void init() {
        // intentionally empty
    }
}
