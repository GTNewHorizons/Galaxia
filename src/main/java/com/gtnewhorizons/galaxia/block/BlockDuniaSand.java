package com.gtnewhorizons.galaxia.block;

import net.minecraft.block.BlockFalling;
import net.minecraft.block.material.Material;

public class BlockDuniaSand extends BlockFalling implements IGalaxiaBlock {

    private final String blockName;

    protected BlockDuniaSand(String blockName) {
        super(Material.sand);
        setHardness(1.0F);
        setBlockName(blockName);
        this.blockName = blockName;
    }

    @Override
    public String getBlockName() {
        return blockName;
    }
}
