package com.gtnewhorizons.galaxia.block;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;

public class BlockCalxRock extends Block implements IGalaxiaBlock {

    private final String blockName;

    protected BlockCalxRock(String blockName) {
        super(Material.rock);
        setHardness(1.5F);
        setBlockName(blockName);
        setBlockTextureName("galaxia:lunar_andesite");
        this.blockName = blockName;
    }

    @Override
    public String getBlockName() {
        return blockName;
    }
}
