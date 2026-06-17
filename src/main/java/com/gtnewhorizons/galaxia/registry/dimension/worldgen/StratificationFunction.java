package com.gtnewhorizons.galaxia.registry.dimension.worldgen;

import net.minecraft.block.Block;

import com.gtnewhorizon.gtnhlib.util.data.BlockMeta;
import com.gtnewhorizon.gtnhlib.util.data.ImmutableBlockMeta;

/// A functional interface that determines which stone block should be placed at a given Y level.
@FunctionalInterface
public interface StratificationFunction {

    ImmutableBlockMeta getStrataBlock(int layerY);

    static StratificationFunction of(Block block) {
        BlockMeta bm = new BlockMeta(block);

        return ignored -> bm;
    }

    static StratificationFunction of(ImmutableBlockMeta blockMeta) {
        return ignored -> blockMeta;
    }
}
