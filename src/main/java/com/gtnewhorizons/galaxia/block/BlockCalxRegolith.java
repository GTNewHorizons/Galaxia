package com.gtnewhorizons.galaxia.block;

import net.minecraft.block.BlockFalling;
import net.minecraft.block.material.Material;

public class BlockCalxRegolith extends BlockFalling implements IGalaxiaBlock {

    private final String blockName;

    protected BlockCalxRegolith(String blockName) {
        super(Material.sand);
        setHardness(1.0F);
        setBlockName(blockName);
        setBlockTextureName("galaxia:lunar_regolith");
        this.blockName = blockName;
    }

    @Override
    public String getBlockName() {
        return blockName;
    }
}
