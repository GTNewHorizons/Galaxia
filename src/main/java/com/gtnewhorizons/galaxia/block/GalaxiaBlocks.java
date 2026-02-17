package com.gtnewhorizons.galaxia.block;

import net.minecraft.block.Block;

import cpw.mods.fml.common.registry.GameRegistry;

public class GalaxiaBlocks {

    private static final String UNLOCALIZED_PREFIX = "galaxia.";

    public enum GalaxiaBlock {

        CALX_REGOLITH(new BlockCalxRegolith("calxRegolith")),
        CALX_ROCK(new BlockCalxRock("calxRock")),

        DUNIA_SAND(new BlockDuniaSand("duniaSand")),
        DUNIA_ROCK(new BlockDuniaRock("duniaRock"));

        private final Block block;
        private final String blockName;

        GalaxiaBlock(Block block) {
            this.block = block;
            this.blockName = ((IGalaxiaBlock) block).getBlockName();
            this.block.setBlockName(UNLOCALIZED_PREFIX + blockName);
        }

        public void register() {
            GameRegistry.registerBlock(block, blockName);
        }

        public Block getBlock() {
            return block;
        }
    }

    public static void registerAll() {
        for (GalaxiaBlock entry : GalaxiaBlock.values()) {
            entry.register();
        }
    }
}
