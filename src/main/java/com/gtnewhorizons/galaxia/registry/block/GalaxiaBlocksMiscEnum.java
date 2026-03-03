package com.gtnewhorizons.galaxia.registry.block;

import net.minecraft.block.Block;

import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.registry.block.base.BlockConfigurable;

import cpw.mods.fml.common.registry.GameRegistry;

public enum GalaxiaBlocksMiscEnum {

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

    ;
    //spotless:on

    private final Block theBlock;

    GalaxiaBlocksMiscEnum(Block block) {
        this.theBlock = block;
    }

    public Block get() {
        return theBlock;
    }

    public static void registerBlocksMisc() {
        for (GalaxiaBlocksMiscEnum block : values()) {
            GameRegistry.registerBlock(
                block.get(),
                block.get()
                    .getUnlocalizedName());
            block.theBlock.setCreativeTab(Galaxia.creativeTab);
        }
    }
}
