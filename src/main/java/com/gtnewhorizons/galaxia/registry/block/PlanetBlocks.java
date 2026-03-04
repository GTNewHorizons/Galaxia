package com.gtnewhorizons.galaxia.registry.block;

import net.minecraft.block.Block;

import com.gtnewhorizons.galaxia.registry.block.planet.BlockPlanetGalaxiaFalling;
import com.gtnewhorizons.galaxia.registry.block.planet.BlockPlanetGalaxiaGeneral;
import com.gtnewhorizons.galaxia.registry.block.planet.BlockPlanetTransparentGalaxia;
import com.gtnewhorizons.galaxia.registry.items.GalaxiaItemList;

import cpw.mods.fml.common.registry.GameRegistry;

public final class PlanetBlocks {

    // spotless:off
    // THEIA blocks
    public static final Block THEIA_REGOLITH = general("theia/theia_regolith", GalaxiaItemList.DUST_THEIA, 1.0F, 1, "pickaxe");
    public static final Block THEIA_MAGMA = general("theia/theia_magma", GalaxiaItemList.DUST_THEIA, 0.5F, 0, "pickaxe");
    public static final Block THEIA_GABBRO = general("theia/theia_gabbro", GalaxiaItemList.DUST_THEIA, 1.5F, 1, "pickaxe");
    public static final Block THEIA_BRECCIA = general("theia/theia_breccia", GalaxiaItemList.DUST_THEIA, 1.5F, 1, "pickaxe");
    public static final Block THEIA_BASALT = general("theia/theia_basalt", GalaxiaItemList.DUST_THEIA, 1.5F, 1, "pickaxe");
    public static final Block THEIA_ANORTHOSITE = general("theia/theia_anorthosite", GalaxiaItemList.DUST_THEIA, 1.5F, 1, "pickaxe");
    public static final Block THEIA_ANDESITE = general("theia/theia_andesite", GalaxiaItemList.DUST_THEIA, 1.5F, 1, "pickaxe");
    public static final Block THEIA_OBSIDIAN = general("theia/theia_obsidian", null, 50.0F, 3, "pickaxe");
    public static final Block THEIA_TEKTITE = general("theia/theia_tektite", GalaxiaItemList.THEIA_TEKTITE_SHARD, 2.0F, 1, "pickaxe");

    // HEMATERIA blocks
    public static final Block HEMATERIA_REGOLITH = falling("hemateria/hemateria_regolith", GalaxiaItemList.DUST_HEMATERIA, 0.5F, 0, "shovel");
    public static final Block HEMATERIA_ANDESITE = general("hemateria/hemateria_andesite", GalaxiaItemList.DUST_HEMATERIA, 1.5F, 1, "pickaxe");
    public static final Block HEMATERIA_BASALT = general("hemateria/hemateria_basalt", GalaxiaItemList.DUST_HEMATERIA, 1.5F, 1, "pickaxe");
    public static final Block HEMATERIA_SNOW = falling("hemateria/hemateria_snow", GalaxiaItemList.DUST_HEMATERIA, 0.1F, 0, "shovel");
    public static final Block HEMATERIA_MAGMA = general("hemateria/hemateria_magma", GalaxiaItemList.DUST_HEMATERIA, 0.5F, 0, "pickaxe");
    public static final Block HEMATERIA_SAND = falling("hemateria/hemateria_sand", GalaxiaItemList.DUST_HEMATERIA, 0.5F, 0, "shovel");
    public static final Block HEMATERIA_SANDSTONE = general("hemateria/hemateria_sandstone", GalaxiaItemList.DUST_HEMATERIA, 0.8F, 0, "pickaxe");
    public static final Block HEMATERIA_TUFF = general("hemateria/hemateria_tuff", GalaxiaItemList.DUST_HEMATERIA, 1.5F, 1, "pickaxe");
    public static final Block HEMATERIA_PERIDOTITE = general("hemateria/hemateria_peridotite", GalaxiaItemList.DUST_HEMATERIA, 2.7F, 1, "pickaxe");
    public static final Block HEMATERIA_RHYOLITE = falling("hemateria/hemateria_rhyolite", GalaxiaItemList.DUST_HEMATERIA, 0.7F, 0, "shovel");
    public static final Block HEMATERIA_ICE = transparent("hemateria/hemateria_ice", GalaxiaItemList.HEMATERIA_ICE_CUBES, 0.5F, 0, "pickaxe");
    public static final Block HEMATERIA_DENSE_ICE = transparent("hemateria/hemateria_dense_ice", GalaxiaItemList.HEMATERIA_ICE_CUBES, 0.5F, 0, "pickaxe");
    public static final Block HEMATERIA_TEKTITE = general("hemateria/hemateria_tektite", GalaxiaItemList.HEMATERIA_TEKTITE_SHARD, 2.0F, 1, "pickaxe");

    // FROZEN_BELT blocks
    public static final Block FROZEN_BELT_ICE = new BlockPlanetTransparentGalaxia("frozen_belt/frozen_belt_ice", null, 0.5F, 0, "pickaxe");
    public static final Block FROZEN_BELT_BRECCIA = general("frozen_belt/frozen_belt_breccia", null, 1.5F, 1, "pickaxe");
    public static final Block FROZEN_BELT_GABBRO = general("frozen_belt/frozen_belt_gabbro", null, 1.5F, 1, "pickaxe");
    public static final Block FROZEN_BELT_BASALT = general("frozen_belt/frozen_belt_basalt", null, 1.5F, 1, "pickaxe");
    public static final Block FROZEN_BELT_ANDESITE = general("frozen_belt/frozen_belt_andesite", null, 1.5F, 1, "pickaxe");
    public static final Block FROZEN_BELT_ANORTHOSITE = general("frozen_belt/frozen_belt_anorthosite", null, 1.5F, 1, "pickaxe");

    // PANSPIRA blocks
    public static final Block PANSPIRA_REGOLITH = falling("panspira/panspira_regolith", null, 0.5F, 0, "shovel");
    public static final Block PANSPIRA_ANDESITE = general("panspira/panspira_andesite", null, 1.5F, 1, "pickaxe");
    public static final Block PANSPIRA_SNOW = falling("panspira/panspira_snow", null, 0.1F, 0, "shovel");
    public static final Block PANSPIRA_STONE = general("panspira/panspira_stone", null, 1.5F, 1, "pickaxe");
    public static final Block PANSPIRA_SOIL = general("panspira/panspira_soil", null, 0.6F, 0, "shovel");
    public static final Block PANSPIRA_MAGMA = general("panspira/panspira_magma", null, 0.5F, 0, "pickaxe");

    // TENEBRAE blocks
    public static final Block TENEBRAE_BASALT = general("tenebrae/tenebrae_basalt", null, 1.5F, 1, "pickaxe");
    public static final Block TENEBRAE_MAGMA = general("tenebrae/tenebrae_magma", null, 0.5F, 0, "pickaxe");
    public static final Block TENEBRAE_ANDESITE = general("tenebrae/tenebrae_andesite", null, 1.5F, 1, "pickaxe");
    public static final Block TENEBRAE_REGOLITH = falling("tenebrae/tenebrae_regolith", null, 0.5F, 0, "shovel");
    public static final Block TENEBRAE_ASH = falling("tenebrae/tenebrae_ash", null, 0.5F, 0, "shovel");
    public static final Block TENEBRAE_PYRITE_REGOLITH = falling("tenebrae/tenebrae_pyrite_regolith", null, 0.7F, 0, "shovel");
    public static final Block TENEBRAE_SULFURIC_REGOLITH = falling("tenebrae/tenebrae_sulfuric_regolith", null, 0.7F, 0, "shovel");
    public static final Block TENEBRAE_RHYOLITE = falling("tenebrae/tenebrae_rhyolite", null, 0.7F, 0, "shovel");
    public static final Block TENEBRAE_LATITE = general("tenebrae/tenebrae_latite", null, 2.0F, 1, "pickaxe");
    public static final Block TENEBRAE_BRIMSTONE = general("tenebrae/tenebrae_brimstone", null, 2.0F, 1, "pickaxe");

    //spotless:on

    private static Block register(Block block, String name) {
        GameRegistry.registerBlock(block, name);
        return block;
    }

    private static Block general(String name, GalaxiaItemList drop, float hardness, int harvestLevel, String tool) {
        return register(
            new BlockPlanetGalaxiaGeneral(name, drop != null ? drop.getItem() : null, hardness, harvestLevel, tool),
            name);
    }

    private static Block falling(String name, GalaxiaItemList drop, float hardness, int harvestLevel, String tool) {
        return register(
            new BlockPlanetGalaxiaFalling(name, drop != null ? drop.getItem() : null, hardness, harvestLevel, tool),
            name);
    }

    private static Block transparent(String name, GalaxiaItemList drop, float hardness, int harvestLevel, String tool) {
        return register(
            new BlockPlanetTransparentGalaxia(name, drop != null ? drop.getItem() : null, hardness, harvestLevel, tool),
            name);
    }

    public static void init() {
        // intentionally empty
    }
}
