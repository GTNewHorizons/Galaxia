package com.gtnewhorizons.galaxia.registry.block;

import net.minecraft.block.Block;
import net.minecraft.item.ItemBlock;

import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.registry.block.base.BlockConfigurable;
import com.gtnewhorizons.galaxia.registry.block.base.BlockFumarole;

import cpw.mods.fml.common.registry.GameRegistry;

public enum GalaxiaBlocksMiscEnum {

    BLOCK_OF_PYRITE(new BlockConfigurable("block_of_pyrite")),
    BLOCK_OF_CHEESE(new BlockConfigurable("block_of_cheese")),
    BLOCK_OF_CINNABAR(new BlockConfigurable("block_of_cinnabar")),
    ENCHANTED_BLOCK_OF_CINNABAR(new BlockConfigurable("enchanted_block_of_cinnabar")),
    BLEEDING_OBSIDIAN(new BlockConfigurable("bleeding_obsidian").hardnessAndResistance(16, 500)
        .harvest("pickaxe", 3)),
    RUSTY_SCAFFOLDING(new BlockConfigurable("rusty_scaffolding")),
    RUSTY_PANEL(new BlockConfigurable("rusty_panel")),
    RUSTY_IRON_BLOCK(new BlockConfigurable("rusty_iron_block")),
    RAW_SULFUR_BLOCK(new BlockConfigurable("raw_sulfur_block")),
    METEORIC_IRON_BLOCK(new BlockConfigurable("meteoric_iron_block")),
    FUMAROLE(new BlockFumarole()),;

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
