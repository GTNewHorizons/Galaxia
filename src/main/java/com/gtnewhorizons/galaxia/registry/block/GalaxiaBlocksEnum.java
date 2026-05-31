package com.gtnewhorizons.galaxia.registry.block;

import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;

import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.registry.block.base.BlockCasing;
import com.gtnewhorizons.galaxia.registry.block.base.BlockConfigurable;
import com.gtnewhorizons.galaxia.registry.block.machine.BlockOxygenCollector;
import com.gtnewhorizons.galaxia.registry.block.machine.BlockOxygenFiller;
import com.gtnewhorizons.galaxia.registry.block.machine.BlockOxygenPylon;
import com.gtnewhorizons.galaxia.registry.block.special.BlockAirlockCasing;
import com.gtnewhorizons.galaxia.registry.block.special.BlockAirlockController;
import com.gtnewhorizons.galaxia.registry.block.special.BlockAirlockDoor;
import com.gtnewhorizons.galaxia.registry.block.special.BlockFumarole;
import com.gtnewhorizons.galaxia.registry.block.special.BlockHammerCannon;
import com.gtnewhorizons.galaxia.registry.block.special.BlockHammerTarget;
import com.gtnewhorizons.galaxia.registry.block.special.BlockStationController;
import com.gtnewhorizons.galaxia.registry.block.tile.TileEntityFumarole;
import com.gtnewhorizons.galaxia.registry.block.tile.machine.TileEntityOxygenCollector;
import com.gtnewhorizons.galaxia.registry.block.tile.machine.TileEntityOxygenFiller;
import com.gtnewhorizons.galaxia.registry.block.tile.machine.TileEntityOxygenPylon;
import com.gtnewhorizons.galaxia.registry.celestial.station.TileEntityAirlock;
import com.gtnewhorizons.galaxia.registry.celestial.station.TileHammerCannon;
import com.gtnewhorizons.galaxia.registry.celestial.station.TileHammerTarget;
import com.gtnewhorizons.galaxia.registry.celestial.station.TileStation;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.BlockRocketTrophy;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.TileEntityModuleAssembler;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.TileEntityRocketTrophy;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.TileEntitySilo;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.controllers.BlockModuleAssembler;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.controllers.BlockSilo;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.gantry.BlockGantry;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.gantry.BlockGantryTerminal;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.gantry.TileEntityGantry;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.gantry.TileEntityGantryTerminal;

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
    AIRLOCK_CONTROLLER(new BlockAirlockController(), TileEntityAirlock.class, "airlock_controller"),
    STATION_CONTROLLER(new BlockStationController(), TileStation.class, "station_controller"),
    ASSEMBLER_CONTROLLER(new BlockModuleAssembler(), TileEntityModuleAssembler.class, "module_assembler_controller"),
    FUMAROLE(new BlockFumarole(), TileEntityFumarole.class, "tenebrae_fumarole"),
    GANTRY(new BlockGantry(), TileEntityGantry.class, "gantry_block"),
    GANTRY_TERMINAL(new BlockGantryTerminal(), TileEntityGantryTerminal.class, "gantry_terminal"),
    ROCKET_TROPHY(new BlockRocketTrophy(), TileEntityRocketTrophy.class, "rocket_trophy"),
    HAMMER_TARGET(new BlockHammerTarget(), TileHammerTarget.class, "hammer_target"),
    HAMMER_CANNON(new BlockHammerCannon(), TileHammerCannon.class, "hammer_cannon"),
    OXYGEN_COLLECTOR(new BlockOxygenCollector(), TileEntityOxygenCollector.class, "oxygen_collector"),
    OXYGEN_FILLER(new BlockOxygenFiller(), TileEntityOxygenFiller.class, "oxygen_filler"),
    OXYGEN_PYLON(new BlockOxygenPylon(), TileEntityOxygenPylon.class, "oxygen_pylon"),

    // NON-TE
    SPACE_STATION_GLASS(new BlockCasing("space_station/space_station_glass").glass(), "space_station_glass"),
    AIRLOCK_DOOR(new BlockAirlockDoor(), "airlock_door"),
    COOLING_COIL(new BlockCasing("space_station/cooling_coil"), "cooling_coil"),
    HEATING_COIL(new BlockCasing("space_station/heating_coil"), "heating_coil"),
    AIR_PURIFIER(new BlockCasing("space_station/air_purifier"), "air_purifier"),
    WITHER_BLOCKER(new BlockCasing("space_station/wither_blocker"), "wither_blocker"),
    OXYGENATOR(new BlockCasing("space_station/oxygenator"), "oxygenator"),

    // MISC
    BLOCK_OF_PYRITE(new BlockConfigurable("resource/block_of_pyrite")),
    BLOCK_OF_CHEESE(new BlockConfigurable("resource/block_of_cheese")),
    BLOCK_OF_CINNABAR(new BlockConfigurable("resource/block_of_cinnabar")),
    METEORIC_IRON_BLOCK(new BlockConfigurable("resource/meteoric_iron_block")),
    RAW_SULFUR_BLOCK(new BlockConfigurable("resource/raw_sulfur_block")),
    ENCHANTED_BLOCK_OF_CINNABAR(new BlockConfigurable("resource/enchanted_block_of_cinnabar")),
    BLEEDING_OBSIDIAN(new BlockConfigurable("bleeding_obsidian")
            .hardnessAndResistance(16, 500)
            .harvest("pickaxe", 3)),
    RESEARCH_OUTPOST_CASING(new BlockConfigurable("machine/research_outpost_casing")),
    LAUNCHPAD_CASING(new BlockConfigurable("machine/launchpad")),

    // MISC - PLANET DECORATION BLOCKS

    // -- MOON --
    MOON_ANDESITE_BRICK(new BlockConfigurable("moon/decoration/moon_andesite_bricks")),
    MOON_ANDESITE_SMOOTH(new BlockConfigurable("moon/decoration/moon_andesite_smooth")),
    MOON_ANDESITE_TILES(new BlockConfigurable("moon/decoration/moon_andesite_tiles")),
    MOON_ANDESITE_SMALL_BRICK(new BlockConfigurable("moon/decoration/moon_andesite_small_bricks")),
    MOON_ANDESITE_FANCY_BRICKS(new BlockConfigurable("moon/decoration/moon_andesite_fancy_bricks")),
    MOON_BASALT_BRICK(new BlockConfigurable("moon/decoration/moon_basalt_bricks")),
    MOON_BASALT_SMOOTH(new BlockConfigurable("moon/decoration/moon_basalt_smooth")),
    MOON_BASALT_TILES(new BlockConfigurable("moon/decoration/moon_basalt_tiles")),
    MOON_BASALT_SMALL_BRICK(new BlockConfigurable("moon/decoration/moon_basalt_small_bricks")),
    MOON_BASALT_FANCY_BRICKS(new BlockConfigurable("moon/decoration/moon_basalt_fancy_bricks")),
    MOON_TEKTITE_BRICK(new BlockConfigurable("moon/decoration/moon_tektite_bricks")),
    MOON_TEKTITE_SMOOTH(new BlockConfigurable("moon/decoration/moon_tektite_smooth")),
    MOON_TEKTITE_TILES(new BlockConfigurable("moon/decoration/moon_tektite_tiles")),
    MOON_TEKTITE_SMALL_BRICK(new BlockConfigurable("moon/decoration/moon_tektite_small_bricks")),
    MOON_TEKTITE_FANCY_BRICKS(new BlockConfigurable("moon/decoration/moon_tektite_fancy_bricks")),
    MOON_BRECCIA_BRICK(new BlockConfigurable("moon/decoration/moon_breccia_bricks")),
    MOON_BRECCIA_SMOOTH(new BlockConfigurable("moon/decoration/moon_breccia_smooth")),
    MOON_BRECCIA_TILES(new BlockConfigurable("moon/decoration/moon_breccia_tiles")),
    MOON_BRECCIA_SMALL_BRICK(new BlockConfigurable("moon/decoration/moon_breccia_small_bricks")),
    MOON_BRECCIA_FANCY_BRICKS(new BlockConfigurable("moon/decoration/moon_breccia_fancy_bricks")),
    MOON_GABBRO_BRICKS(new BlockConfigurable("moon/decoration/moon_gabbro_bricks")),
    MOON_GABBRO_SMOOTH(new BlockConfigurable("moon/decoration/moon_gabbro_smooth")),
    MOON_GABBRO_TILES(new BlockConfigurable("moon/decoration/moon_gabbro_tiles")),
    MOON_GABBRO_SMALL_BRICK(new BlockConfigurable("moon/decoration/moon_gabbro_small_bricks")),
    MOON_GABBRO_FANCY_BRICKS(new BlockConfigurable("moon/decoration/moon_gabbro_fancy_bricks")),
    MOON_ANORTHOSITE_BRICKS(new BlockConfigurable("moon/decoration/moon_anorthosite_bricks")),
    MOON_ANORTHOSITE_SMOOTH(new BlockConfigurable("moon/decoration/moon_anorthosite_smooth")),
    MOON_ANORTHOSITE_TILES(new BlockConfigurable("moon/decoration/moon_anorthosite_tiles")),
    MOON_ANORTHOSITE_SMALL_BRICK(new BlockConfigurable("moon/decoration/moon_anorthosite_small_bricks")),
    MOON_ANORTHOSITE_FANCY_BRICKS(new BlockConfigurable("moon/decoration/moon_anorthosite_fancy_bricks")),


    // MISC - MULTIBLOCK BLOCKS
    RUSTY_SCAFFOLDING(new BlockCasing("rusty_scaffolding").transparent()),
    RUSTY_PANEL(new BlockCasing("rusty_panel")),
    RUSTY_SHEETMETAL(new BlockCasing("rusty_sheetmetal")),
    SPACE_STATION_PANEL(new BlockCasing("space_station/space_station_panel")),
    RUSTY_IRON_BLOCK(new BlockCasing("rusty_iron_block")),
    SPACE_STATION_BLOCK(new BlockCasing("space_station/space_station_block")),
    AIRLOCK_CASING(new BlockAirlockCasing()),

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
