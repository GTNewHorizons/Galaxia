package com.gtnewhorizons.galaxia.block;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;

public class BlockDuniaRock extends Block implements IGalaxiaBlock {

    private final String blockName;

    protected BlockDuniaRock(String blockName) {
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
