package com.gtnewhorizons.galaxia.registry.dimension.worldgen;

import com.gtnewhorizon.gtnhlib.util.data.ImmutableBlockMeta;
import it.unimi.dsi.fastutil.ints.Int2ObjectFunction;

@FunctionalInterface
public interface StratificationFunction {

    ImmutableBlockMeta getStrataBlock(int layerY);

}
