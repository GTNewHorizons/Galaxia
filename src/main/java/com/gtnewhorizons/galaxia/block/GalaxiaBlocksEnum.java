package com.gtnewhorizons.galaxia.block;

import static com.gtnewhorizons.galaxia.block.base.GalaxiaBlock.reg;

import net.minecraft.block.Block;

import com.gtnewhorizons.galaxia.block.base.BlockVariant;
import com.gtnewhorizons.galaxia.block.module.BlockModuleController;
import com.gtnewhorizons.galaxia.block.module.BlockModuleShell;
import com.gtnewhorizons.galaxia.block.tileentities.TileEntityModuleController;
import com.gtnewhorizons.galaxia.dimension.DimensionEnum;
import com.gtnewhorizons.galaxia.items.GalaxiaItemList;

import cpw.mods.fml.common.registry.GameRegistry;

/**
 * The ENUM used for all custom blocks in Galaxia. BlockVariants are used to change the planet the block can be found on
 * for types of rock etc.
 */
public enum GalaxiaBlocksEnum {
    // spotless:off

    MODULE_CONTROLLER(new BlockModuleController(), "module_controller"),
    MODULE_SHELL(new BlockModuleShell(), "module_shell"),
    ; // leave trailing semicolon

    // spotless:on

    /**
     * Registers all blocks in the ENUM into the game registry, including tile entity blocks
     */
    public static void registerBlocks() {
        for (GalaxiaBlocksEnum block : values()) {
            GameRegistry.registerBlock(block.get(), block.name);
        }

        GameRegistry.registerTileEntity(TileEntityModuleController.class, "galaxia_module_controller");
    }

    // spotless:off

    /**
     * Registers all block variants for each planet, alongside the relevant dust items
     */
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

        reg(DimensionEnum.FROZEN_BELT,
            BlockVariant.ICE,
            BlockVariant.BRECCIA,
            BlockVariant.GABBRO,
            BlockVariant.BASALT,
            BlockVariant.ANDESITE,
            BlockVariant.ANORTHOSITE);
    }
    //spotless:on

    private final Block theBlock;
    private final String name;

    GalaxiaBlocksEnum(Block block, String name) {
        this.theBlock = block;
        this.name = name;
    }

    public Block get() {
        return theBlock;
    }
}
