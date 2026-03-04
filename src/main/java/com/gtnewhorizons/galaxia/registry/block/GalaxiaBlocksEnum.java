package com.gtnewhorizons.galaxia.registry.block;

import net.minecraft.block.Block;

import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.registry.block.special.BlockNoduleController;
import com.gtnewhorizons.galaxia.registry.block.special.BlockSpaceAir;
import com.gtnewhorizons.galaxia.registry.block.special.BlockSpaceStation;
import com.gtnewhorizons.galaxia.registry.block.special.BlockSpaceStationGlass;
import com.gtnewhorizons.galaxia.registry.block.tile.TileEntityFumarole;
import com.gtnewhorizons.galaxia.registry.block.tile.TileNoduleController;
import com.gtnewhorizons.galaxia.rocketmodules.tileentities.BlockModuleAssembler;
import com.gtnewhorizons.galaxia.rocketmodules.tileentities.BlockSilo;
import com.gtnewhorizons.galaxia.rocketmodules.tileentities.TileEntityModuleAssembler;
import com.gtnewhorizons.galaxia.rocketmodules.tileentities.TileEntitySilo;

import cpw.mods.fml.common.registry.GameRegistry;

/**
 * The ENUM used for all custom blocks in Galaxia. BlockVariants are used to change the planet the block can be found on
 * for types of rock etc.
 */
public enum GalaxiaBlocksEnum {
    // spotless:off

    SILO_CONTROLLER(new BlockSilo(), "silo_controller"),
    NODULE_CONTROLLER(new BlockNoduleController(), "nodule_controller"),
    SPACE_STATION_BLOCK(new BlockSpaceStation(), "space_station_block"),
    SPACE_STATION_GLASS(new BlockSpaceStationGlass(), "space_station_glass"),
    SPACE_AIR(new BlockSpaceAir(), "space_air"),
    ASSEMBLER_CONTROLLER(new BlockModuleAssembler(), "module_assembler_controller")
    ; // leave trailing semicolon

    // spotless:on

    /**
     * Registers all blocks in the ENUM into the game registry, including tile entity blocks
     */
    public static void registerBlocks() {
        for (GalaxiaBlocksEnum block : values()) {
            block.theBlock.setBlockName(block.name);
            GameRegistry.registerBlock(block.get(), block.name);
            block.theBlock.setCreativeTab(Galaxia.creativeTab);
        }

        GameRegistry.registerTileEntity(TileEntitySilo.class, "galaxia_silo_controller");
        GameRegistry.registerTileEntity(TileNoduleController.class, "galaxia_nodule_controller");
        GameRegistry.registerTileEntity(TileEntityModuleAssembler.class, "galaxia_module_assembler_controller");
        GameRegistry.registerTileEntity(TileEntityFumarole.class, "galaxia_fumarole");
    }

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
