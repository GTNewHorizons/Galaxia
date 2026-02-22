package com.gtnewhorizons.galaxia.block;

import static com.gtnewhorizons.galaxia.block.GalaxiaBlockBase.reg;

import net.minecraft.block.Block;

import com.gtnewhorizons.galaxia.dimension.DimensionEnum;
import com.gtnewhorizons.galaxia.items.GalaxiaItemList;
import com.gtnewhorizons.galaxia.modules.BlockModuleController;
import com.gtnewhorizons.galaxia.modules.BlockModuleShell;
import com.gtnewhorizons.galaxia.modules.TileEntityModuleController;

import cpw.mods.fml.common.registry.GameRegistry;

public enum GalaxiaBlocks {
    // spotless:off

    MODULE_CONTROLLER(new BlockModuleController(), "module_controller"),
    MODULE_SHELL(new BlockModuleShell(), "module_shell"),
    ; // leave trailing semicolon

    // spotless:on

    public static void registerBlocks() {
        for (GalaxiaBlocks block : values()) {
            GameRegistry.registerBlock(block.get(), block.name);
        }

        GameRegistry.registerTileEntity(TileEntityModuleController.class, "galaxia_module_controller");
    }

    // spotless:off
    public static void registerPlanetBlocks() {
        reg(DimensionEnum.THEIA, GalaxiaItemList.DUST_THEIA,
            BlockVariant.REGOLITH,
            BlockVariant.TEKTITE,
            BlockVariant.MAGMA,
            BlockVariant.GABBRO,
            BlockVariant.BRECCIA,
            BlockVariant.BASALT,
            BlockVariant.ANORTHOSITE,
            BlockVariant.ANDESITE);

        reg(DimensionEnum.HEMATERIA,
            BlockVariant.REGOLITH,
            BlockVariant.ANDESITE,
            BlockVariant.SNOW);
    }
    //spotless:on

    private final Block theBlock;
    private final String name;

    GalaxiaBlocks(Block block, String name) {
        this.theBlock = block;
        this.name = name;
    }

    public Block get() {
        return theBlock;
    }
}
