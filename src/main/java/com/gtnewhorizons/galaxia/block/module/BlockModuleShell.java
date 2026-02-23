package com.gtnewhorizons.galaxia.block.module;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizons.galaxia.modules.ModuleConfig;

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

    @Override
    public AxisAlignedBB getCollisionBoundingBoxFromPool(World world, int x, int y, int z) {
        return null;
    }

    @Override
    public boolean isSideSolid(IBlockAccess world, int x, int y, int z, ForgeDirection side) {
        return true;
    }
}
