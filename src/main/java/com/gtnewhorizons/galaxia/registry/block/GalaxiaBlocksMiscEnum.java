package com.gtnewhorizons.galaxia.registry.block;

import net.minecraft.block.Block;
import net.minecraft.item.ItemBlock;

import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.registry.block.base.BlockConfigurable;
import com.gtnewhorizons.galaxia.registry.block.special.BlockFumarole;

import cpw.mods.fml.common.registry.GameRegistry;

public enum GalaxiaBlocksMiscEnum {

    // TODO fill all leftover textures:
    // glowstone_torch.png, rusty_iron_bars.png, research_outpost_controller_[], some others if you can find (i can't)

    // spotless:off
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
    FUMAROLE(new BlockFumarole()),
    ;
    //spotless:on

    private final Block theBlock;
    private final Class<? extends ItemBlock> itemClass;

    GalaxiaBlocksMiscEnum(Block block) {
        this.theBlock = block;
        this.itemClass = null;
    }

    GalaxiaBlocksMiscEnum(Block block, Class<? extends ItemBlock> item) {
        this.theBlock = block;
        this.itemClass = item;
    }

    public Block get() {
        return theBlock;
    }

    public static void registerBlocksMisc() {
        for (GalaxiaBlocksMiscEnum block : values()) {
            if (block.itemClass == null) {
                GameRegistry.registerBlock(
                    block.get(),
                    block.get()
                        .getUnlocalizedName());
                block.theBlock.setCreativeTab(Galaxia.creativeTab);
            } else {
                GameRegistry.registerBlock(
                    block.get(),
                    block.itemClass,
                    block.get()
                        .getUnlocalizedName());
                block.theBlock.setCreativeTab(Galaxia.creativeTab);
            }
        }
    }
}
