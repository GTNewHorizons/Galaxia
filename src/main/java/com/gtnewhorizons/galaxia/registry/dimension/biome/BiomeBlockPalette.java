package com.gtnewhorizons.galaxia.registry.dimension.biome;

import com.gtnewhorizon.gtnhlib.util.data.ImmutableBlockMeta;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.StratificationFunction;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.StratificationLayers;

public interface BiomeBlockPalette {

    ImmutableBlockMeta getTopBlock();
    StratificationFunction getFillerBlocks();

    ImmutableBlockMeta getSnowBlock();
    ImmutableBlockMeta getOceanFiller();
    ImmutableBlockMeta getOceanSurface();
    ImmutableBlockMeta getSeabed();
    ImmutableBlockMeta getOceanCrackBlock();

    default boolean hasCracks() {
        return getOceanCrackBlock() != null && getOceanCrackThickness() > 0;
    }

    int getSnowHeight();
    int getOceanHeight();
    int getSeabedHeight();
    int getSurfaceThickness();
    float getOceanCrackThickness();
    int getOceanCrackComplexity();
}
