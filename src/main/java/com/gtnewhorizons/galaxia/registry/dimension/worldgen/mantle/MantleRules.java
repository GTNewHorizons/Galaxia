package com.gtnewhorizons.galaxia.registry.dimension.worldgen.mantle;

import com.gtnewhorizon.gtnhlib.util.data.BlockMeta;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.TerrainConfiguration;
import lombok.Getter;

public class MantleRules {
    @Getter
    private final TerrainConfiguration ceiling;
    @Getter
    private final TerrainConfiguration floor;

    public BlockMeta fillerBlock;


    public MantleRules(TerrainConfiguration ceiling, TerrainConfiguration floor) {
        this.ceiling = ceiling;
        this.floor = floor;
    }

    public MantleRules setFillerBlock(BlockMeta fillerBlock) {
        this.fillerBlock = fillerBlock;
        return this;
    }
}
