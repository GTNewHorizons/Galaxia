package com.gtnewhorizons.galaxia.block;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;

public class BlockCalxRegolith extends Block implements IGalaxiaBlock {
    private final String blockName;

    protected BlockCalxRegolith(String blockName) {
        super(Material.rock);
        setHardness(1.5F);
        setBlockName(blockName);
        this.blockName = blockName;
    }

    @Override
    public String getBlockName() {
        return blockName;
    }
}
