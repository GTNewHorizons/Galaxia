package com.gtnewhorizons.galaxia.registry.dimension.worldgen;

import java.util.HashMap;

import net.minecraft.block.Block;

public class StratificationPreset {

    private final Block defaultBlock;
    private final HashMap<Integer, Block> strataMap = new HashMap<>();

    public StratificationPreset(Block defaultBlock) {
        this.defaultBlock = defaultBlock;
    }

    public StratificationPreset addStrataLayer(Block strataBlock, int minimumHeight, int maximumHeight) {
        for (int height = minimumHeight; height <= maximumHeight; height++) {
            strataMap.put(height, strataBlock);
        }
        return this;
    }

    public Block getStrataBlock(int height) {
        Block strataBlock = strataMap.get(height);
        if (strataBlock == null) {
            strataBlock = defaultBlock;
        }
        return strataBlock;
    }
}
