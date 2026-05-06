package com.gtnewhorizons.galaxia.registry.dimension.worldgen;

import net.minecraft.block.Block;

public class StratificationPreset {

    private final Block defaultBlock;
    private final Block[] strataMap = new Block[ChunkProviderGalaxiaPlanet.HEIGHT_LIMIT];
    private boolean frozen = false;

    public StratificationPreset(Block defaultBlock) {
        this.defaultBlock = defaultBlock;
    }

    public StratificationPreset freeze() {
        this.frozen = true;
        return this;
    }

    public StratificationPreset addStrataLayer(Block strataBlock, int minimumHeight, int maximumHeight) {
        if (frozen) {
            throw new IllegalStateException("Cannot mutate a frozen StratificationPreset");
        }
        int lo = Math.max(0, minimumHeight);
        int hi = Math.min(strataMap.length - 1, maximumHeight);
        for (int height = lo; height <= hi; height++) {
            strataMap[height] = strataBlock;
        }
        return this;
    }

    public Block getStrataBlock(int height) {
        if (height < 0 || height >= strataMap.length) {
            return defaultBlock;
        }
        Block strataBlock = strataMap[height];
        return strataBlock != null ? strataBlock : defaultBlock;
    }
}
