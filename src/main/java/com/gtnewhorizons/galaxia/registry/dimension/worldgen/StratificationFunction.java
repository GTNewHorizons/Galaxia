package com.gtnewhorizons.galaxia.registry.dimension.worldgen;

import com.gtnewhorizon.gtnhlib.util.data.ImmutableBlockMeta;

@FunctionalInterface
public interface StratificationFunction {

    ImmutableBlockMeta getStrataBlock(int layerY);

}
