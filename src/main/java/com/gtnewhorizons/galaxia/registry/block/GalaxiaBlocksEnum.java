package com.gtnewhorizons.galaxia.registry.block;

import static com.gtnewhorizons.galaxia.registry.block.base.GalaxiaBlock.reg;

import net.minecraft.block.Block;

import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.registry.block.base.BlockNoduleController;
import com.gtnewhorizons.galaxia.registry.block.base.BlockSpaceAir;
import com.gtnewhorizons.galaxia.registry.block.base.BlockSpaceStation;
import com.gtnewhorizons.galaxia.registry.block.base.BlockSpaceStationGlass;
import com.gtnewhorizons.galaxia.registry.block.base.BlockVariant;
import com.gtnewhorizons.galaxia.registry.dimension.DimensionEnum;
import com.gtnewhorizons.galaxia.registry.items.GalaxiaItemList;
import com.gtnewhorizons.galaxia.rocketmodules.tileentities.BlockSilo;
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
    ; // leave trailing semicolon

    // spotless:on

    /**
     * Registers all blocks in the ENUM into the game registry, including tile entity blocks
     */
    public static void registerBlocks() {
        for (GalaxiaBlocksEnum block : values()) {
            GameRegistry.registerBlock(block.get(), block.name);
            block.theBlock.setCreativeTab(Galaxia.creativeTab);
        }

        GameRegistry.registerTileEntity(TileEntitySilo.class, "galaxia_silo_controller");
    }

    // spotless:off

    /**
     * Registers all block variants for each planet, alongside the relevant drop items
     * if drop item is not selected, blocks will drop themselves by default
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
            BlockVariant.ANDESITE,
            BlockVariant.OBSIDIAN);

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

        reg(DimensionEnum.PANSPIRA,
            BlockVariant.REGOLITH,
            BlockVariant.ANDESITE,
            BlockVariant.SNOW,
            BlockVariant.STONE,
            BlockVariant.SOIL,
            BlockVariant.MAGMA);
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
