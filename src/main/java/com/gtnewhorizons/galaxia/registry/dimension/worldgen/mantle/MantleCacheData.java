package com.gtnewhorizons.galaxia.registry.dimension.worldgen.mantle;

import com.gtnewhorizon.gtnhlib.util.data.ImmutableBlockMeta;

public record MantleCacheData(double[] ceilingHeightmap, double[] floorHeightmap,
    ImmutableBlockMeta[] ceilingSurfaceBlocks, ImmutableBlockMeta[] floorSurfaceBlocks) {}
