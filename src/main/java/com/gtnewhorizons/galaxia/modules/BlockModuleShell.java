package com.gtnewhorizons.galaxia.modules;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;

public class BlockModuleShell extends Block {

    public BlockModuleShell() {
        super(Material.iron);
        setBlockName("module.shell");
        setHardness(-1F);
        setResistance(6000000F);
        setBlockUnbreakable();
    }

    @Override
    public boolean renderAsNormalBlock() {
        return ModuleConfig.DEBUG_RENDER;
    }

    @Override
    public int getRenderType() {
        return ModuleConfig.DEBUG_RENDER ? 0 : -1;
    }

    @Override
    public boolean isOpaqueCube() {
        return ModuleConfig.DEBUG_RENDER;
    }

    @Override
    public boolean isNormalCube() {
        return ModuleConfig.DEBUG_RENDER;
    }
}
