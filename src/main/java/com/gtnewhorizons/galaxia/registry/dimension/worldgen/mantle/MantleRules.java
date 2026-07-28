package com.gtnewhorizons.galaxia.registry.dimension.worldgen.mantle;

import com.gtnewhorizon.gtnhlib.util.data.BlockMeta;

public class MantleRules {
    public BlockMeta fillerBlock;

    public MantleRules setFillerBlock(BlockMeta fillerBlock) {
        this.fillerBlock = fillerBlock;
        return this;
    }
}
