package com.gtnewhorizons.galaxia.block;

import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.block.Block;

public class GalaxiaBlocks {


    public enum GalaxiaBlock {

        CALX_REGOLITH(new BlockCalxRegolith("calxRegolith"));

        private final Block block;
        private final String blockName;

        GalaxiaBlock(Block block) {
            this.block = block;
            this.blockName = ((IGalaxiaBlock)block).getBlockName();
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
