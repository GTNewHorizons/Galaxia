package com.gtnewhorizons.galaxia.registry.dimension.worldgen.mantle;

import com.gtnewhorizon.gtnhlib.util.data.BlockMeta;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.TerrainConfiguration;

import lombok.Getter;

/**
 * Contains all important rules for generating a mantle layer
 */
public class MantleRules {

    @Getter
    private final TerrainConfiguration ceiling;
    @Getter
    private final TerrainConfiguration floor;

    public BlockMeta fillerBlock;

    /**
     * Creates mantle rules with terrain configurations
     * @param ceiling Terrain configuration for the ceiling (simply generates everything upside down)
     * @param floor Terrain configuration for the floor
     */
    public MantleRules(TerrainConfiguration ceiling, TerrainConfiguration floor) {
        this.ceiling = ceiling;
        this.floor = floor;
    }

    /**
     * Sets the filler block to place in the mantle layer
     * @param fillerBlock Block to fill the mantle with
     * @return The object (this is a builder method)
     */
    public MantleRules setFillerBlock(BlockMeta fillerBlock) {
        this.fillerBlock = fillerBlock;
        return this;
    }
}
