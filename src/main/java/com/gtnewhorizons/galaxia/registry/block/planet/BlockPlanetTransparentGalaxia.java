package com.gtnewhorizons.galaxia.registry.block.planet;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.util.Facing;
import net.minecraft.world.IBlockAccess;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class BlockPlanetTransparentGalaxia extends BlockPlanetGalaxiaGeneral {

    public BlockPlanetTransparentGalaxia(String blockName, Item drop, float hardness, int harvestLevel,
        String harvestTool) {
        super(blockName, drop, hardness, harvestLevel, harvestTool);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public int getRenderBlockPass() {
        return 1;
    }

    @Override
    public boolean renderAsNormalBlock() {
        return false;
    }

    @SideOnly(Side.CLIENT)
    public boolean shouldSideBeRendered(IBlockAccess worldIn, int x, int y, int z, int side) {
        Block block = worldIn.getBlock(x, y, z);

        if (worldIn.getBlockMetadata(x, y, z) != worldIn.getBlockMetadata(
            x - Facing.offsetsXForSide[side],
            y - Facing.offsetsYForSide[side],
            z - Facing.offsetsZForSide[side])) {
            return true;
        }

        if (block == this) {
            return false;
        }

        return super.shouldSideBeRendered(worldIn, x, y, z, side);
    }

    public boolean isOpaqueCube() {
        return false;
    }

}
