package com.gtnewhorizons.galaxia.registry.dimension.worldgen.mantle;

import com.gtnewhorizon.gtnhlib.util.data.ImmutableBlockMeta;

/**
 * Contains processed cache data for generating a mantle layer
 * @param ceilingHeightmap Heightmap of the mantle's ceiling
 * @param floorHeightmap Heightmap of the mantle's floor
 * @param ceilingSurfaceBlocks Surface replacement blocks for the ceiling
 * @param floorSurfaceBlocks Surface replacement blocks for the floor
 */
public record MantleCacheData(double[] ceilingHeightmap, double[] floorHeightmap,
    ImmutableBlockMeta[] ceilingSurfaceBlocks, ImmutableBlockMeta[] floorSurfaceBlocks) {}
