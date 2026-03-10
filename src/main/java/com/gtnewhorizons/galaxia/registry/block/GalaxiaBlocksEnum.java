package com.gtnewhorizons.galaxia.registry.block;

import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;

import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.registry.block.base.BlockConfigurable;
import com.gtnewhorizons.galaxia.registry.block.special.BlockFumarole;
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
import com.gtnewhorizons.galaxia.rocketmodules.tileentities.gantry.BlockGantry;
import com.gtnewhorizons.galaxia.rocketmodules.tileentities.gantry.BlockGantryTerminal;
import com.gtnewhorizons.galaxia.rocketmodules.tileentities.gantry.TileEntityGantry;
import com.gtnewhorizons.galaxia.rocketmodules.tileentities.gantry.TileEntityGantryTerminal;

import cpw.mods.fml.common.registry.GameRegistry;

/**
 * The ENUM used for all Tile Entities in Galaxia
 */
public enum GalaxiaBlocksEnum {
    // spotless:off

    // TODO fill all leftover textures: glowstone_torch.png, rusty_iron_bars.png,
    // research_outpost_controller_[], some others if you can find (i can't)

    // TE
    SILO_CONTROLLER(new BlockSilo(), TileEntitySilo.class, "silo_controller"),
    NODULE_CONTROLLER(new BlockNoduleController(), TileNoduleController.class, "nodule_controller"),
    ASSEMBLER_CONTROLLER(new BlockModuleAssembler(), TileEntityModuleAssembler.class, "module_assembler_controller"),
    FUMAROLE(new BlockFumarole(), TileEntityFumarole.class, "fumarole"),
    GANTRY(new BlockGantry(), TileEntityGantry.class, "gantry_block"),
    GANTRY_TERMINAL(new BlockGantryTerminal(), TileEntityGantryTerminal.class, "gantry_terminal"),

    // NON-TE
    SPACE_STATION_BLOCK(new BlockSpaceStation(), "space_station_block"),
    SPACE_STATION_GLASS(new BlockSpaceStationGlass(), "space_station_glass"),
    SPACE_AIR(new BlockSpaceAir(), "space_air"),

    // MISC
    BLOCK_OF_PYRITE(new BlockConfigurable("resource/block_of_pyrite")),
    BLOCK_OF_CHEESE(new BlockConfigurable("resource/block_of_cheese")),
    BLOCK_OF_CINNABAR(new BlockConfigurable("resource/block_of_cinnabar")),
    METEORIC_IRON_BLOCK(new BlockConfigurable("resource/meteoric_iron_block")),
    RAW_SULFUR_BLOCK(new BlockConfigurable("resource/raw_sulfur_block")),
    ENCHANTED_BLOCK_OF_CINNABAR(new BlockConfigurable("resource/enchanted_block_of_cinnabar")),
    RUSTY_IRON_BLOCK(new BlockConfigurable("rusty_iron_block")),
    BLEEDING_OBSIDIAN(new BlockConfigurable("bleeding_obsidian")
            .hardnessAndResistance(16, 500)
            .harvest("pickaxe", 3)),
    RUSTY_SCAFFOLDING(new BlockConfigurable("rusty_scaffolding")
            .opaque()),
    RUSTY_PANEL(new BlockConfigurable("rusty_panel")),
    RUSTY_SHEETMETAL(new BlockConfigurable("rusty_sheetmetal")),
    SPACE_STATION_PANEL(new BlockConfigurable("space_station/space_station_panel")),
    RESEARCH_OUTPOST_CASING(new BlockConfigurable("machine/research_outpost_casing")),
    LAUNCHPAD_CASING(new BlockConfigurable("machine/launchpad")),

    ; // leave trailing semicolon

    // spotless:on

    /**
     * Registers all blocks in the ENUM into the game registry, including tile
     * entity blocks
     */
    public static void registerBlocks() {
        for (GalaxiaBlocksEnum block : values()) {
            block.theBlock.setBlockName(block.name);
            block.theBlock.setCreativeTab(Galaxia.creativeTab);
            GameRegistry.registerBlock(block.get(), block.name);
            if (block.tileEntityClass != null)
                GameRegistry.registerTileEntity(block.tileEntityClass, Galaxia.REGISTRY_PREFIX + block.name);
        }
    }

    private final Block theBlock;
    private final String name;
    private final Class<? extends TileEntity> tileEntityClass;

    GalaxiaBlocksEnum(Block block, Class<? extends TileEntity> tileEntityClass, String name) {
        this.theBlock = block;
        this.name = name;
        this.tileEntityClass = tileEntityClass;
    }

    GalaxiaBlocksEnum(Block block, String name) {
        this.theBlock = block;
        this.name = name;
        this.tileEntityClass = null;
    }

    GalaxiaBlocksEnum(Block block) {
        this.theBlock = block;
        this.name = block.getUnlocalizedName()
            .substring(5); // substring to remove "tile."
        this.tileEntityClass = null;
    }

    public Block get() {
        return theBlock;
    }
}
