package com.gtnewhorizons.galaxia.registry.block.planet;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraftforge.fluids.Fluid;

import com.gtnewhorizons.galaxia.registry.block.planet.fluid.FluidFiniteBuilder;
import com.gtnewhorizons.galaxia.registry.items.GalaxiaItemList;

public final class PlanetBlocks {

    // spotless:off
    /*
        THEIA blocks
     */
    public static final Block THEIA_REGOLITH = PlanetBlockBuilder.create("theia/theia_regolith")
        .falling()
        .dropSelf()
        .hardness(1.0F)
        .harvest(1)
        .build();

    public static final Block THEIA_MAGMA = PlanetBlockBuilder.create("theia/theia_magma")
        .dropSelf()
        .hardness(0.5F)
        .harvest(0)
        .build();

    public static final Block THEIA_GABBRO = PlanetBlockBuilder.create("theia/theia_gabbro")
        .dropSelf()
        .hardness(1.5F)
        .harvest(1)
        .build();

    public static final Block THEIA_BRECCIA = PlanetBlockBuilder.create("theia/theia_breccia")
        .dropSelf()
        .hardness(1.5F)
        .harvest(1)
        .build();

    public static final Block THEIA_BASALT = PlanetBlockBuilder.create("theia/theia_basalt")
        .dropSelf()
        .hardness(1.5F)
        .harvest(1)
        .build();

    public static final Block THEIA_ANORTHOSITE = PlanetBlockBuilder.create("theia/theia_anorthosite")
        .dropSelf()
        .hardness(1.5F)
        .harvest(1)
        .build();

    public static final Block THEIA_ANDESITE = PlanetBlockBuilder.create("theia/theia_andesite")
        .dropSelf()
        .hardness(1.5F)
        .harvest(1)
        .build();

    public static final Block THEIA_OBSIDIAN = PlanetBlockBuilder.create("theia/theia_obsidian")
        .dropSelf()
        .hardness(50.0F)
        .harvest(3)
        .build();

    public static final Block THEIA_TEKTITE = PlanetBlockBuilder.create("theia/theia_tektite")
        .drop(GalaxiaItemList.THEIA_TEKTITE_SHARD)
        .hardness(2.0F)
        .harvest(1)
        .build();

    /*
        HEMATERIA blocks
     */
    public static final Block HEMATERIA_REGOLITH = PlanetBlockBuilder.create("hemateria/hemateria_regolith")
        .falling()
        .dropSelf()
        .hardness(0.5F)
        .shovel()
        .harvest(0)
        .build();

    public static final Block HEMATERIA_ANDESITE = PlanetBlockBuilder.create("hemateria/hemateria_andesite")
        .dropSelf()
        .hardness(1.5F)
        .harvest(1)
        .build();

    public static final Block HEMATERIA_BASALT = PlanetBlockBuilder.create("hemateria/hemateria_basalt")
        .dropSelf()
        .hardness(1.5F)
        .harvest(1)
        .build();

    public static final Block HEMATERIA_SNOW = PlanetBlockBuilder.create("hemateria/hemateria_snow")
        .falling()
        .dropSelf()
        .hardness(0.1F)
        .shovel()
        .harvest(0)
        .build();

    public static final Block HEMATERIA_ANORTHOSITE = PlanetBlockBuilder.create("hemateria/hemateria_anorthosite")
        .drop(GalaxiaItemList.DUST_HEMATERIA)
        .hardness(1.5F)
        .harvest(1)
        .build();

    public static final Block HEMATERIA_TEKTITE = PlanetBlockBuilder.create("hemateria/hemateria_tektite")
        .drop(GalaxiaItemList.HEMATERIA_TEKTITE_SHARD)
        .hardness(2.0F)
        .harvest(1)
        .build();

    public static final Block HEMATERIA_ICE = PlanetBlockBuilder.create("hemateria/hemateria_ice")
        .transparent()
        .drop(GalaxiaItemList.HEMATERIA_ICE_CUBES)
        .dropAmount(2, 4)
        .hardness(0.5F)
        .harvest(1)
        .build();

    public static final Block HEMATERIA_DENSE_ICE = PlanetBlockBuilder.create("hemateria/hemateria_dense_ice")
        .transparent()
        .drop(GalaxiaItemList.HEMATERIA_ICE_CUBES)
        .dropAmount(4, 7)
        .hardness(0.5F)
        .harvest(1)
        .build();

    public static final Block HEMATERIA_MAGMA = PlanetBlockBuilder.create("hemateria/hemateria_magma")
        .dropSelf()
        .hardness(0.5F)
        .harvest(0)
        .build();

    public static final Block HEMATERIA_SAND = PlanetBlockBuilder.create("hemateria/hemateria_sand")
        .falling()
        .dropSelf()
        .hardness(0.5F)
        .shovel()
        .harvest(0)
        .build();

    public static final Block HEMATERIA_SANDSTONE = PlanetBlockBuilder.create("hemateria/hemateria_sandstone")
        .dropSelf()
        .hardness(0.8F)
        .harvest(0)
        .build();

    public static final Block HEMATERIA_TUFF = PlanetBlockBuilder.create("hemateria/hemateria_tuff")
        .dropSelf()
        .hardness(1.5F)
        .harvest(1)
        .build();

    public static final Block HEMATERIA_PERIDOTITE = PlanetBlockBuilder.create("hemateria/hemateria_peridotite")
        .dropSelf()
        .hardness(2.7F)
        .harvest(1)
        .build();

    public static final Block HEMATERIA_RHYOLITE = PlanetBlockBuilder.create("hemateria/hemateria_rhyolite")
        .falling()
        .dropSelf()
        .hardness(0.7F)
        .shovel()
        .harvest(0)
        .build();

    /*
        PANSPIRA blocks
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
        TENEBRAE blocks
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

    public static final Block TENEBRAE_SULFURIC_REGOLITH = PlanetBlockBuilder.create("tenebrae/tenebrae_sulfuric_regolith")
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
        FROZEN BELT blocks
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
        ASTEROIDS
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

    public static final Block ASTEROID_SHELL_NAQUADAH = PlanetBlockBuilder.create("asteroid_belt/naquadah_asteroid_shell")
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
        FLUIDS
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

    //spotless:on

    public static void init() {
        // intentionally empty
    }
}
