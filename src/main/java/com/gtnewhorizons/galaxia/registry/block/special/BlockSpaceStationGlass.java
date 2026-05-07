package com.gtnewhorizons.galaxia.registry.block.special;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.world.IBlockAccess;

import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.registry.block.base.BlockUpdatable;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class BlockSpaceStationGlass extends BlockUpdatable {

    private final boolean ignoreSimilatiry = false;

    public BlockSpaceStationGlass() {
        super(Material.iron);
        this.setBlockName("space_station_glass");
        this.stepSound = soundTypeMetal;
        this.setBlockBounds(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F);
        this.setCreativeTab(Galaxia.creativeTab);
        this.setLightOpacity(0);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void registerBlockIcons(IIconRegister reg) {
        this.blockIcon = reg.registerIcon("galaxia:space_station/space_station_glass");
    }

    @SideOnly(Side.CLIENT)
    @Override
    public int getRenderBlockPass() {
        return 1;
    }

    public boolean renderAsNormalBlock() {
        return false;
    }

    protected boolean canSilkHarvest() {
        return true;
    }

    // TODO: make updateable glass
    @SideOnly(Side.CLIENT)
    public boolean shouldSideBeRendered(IBlockAccess worldIn, int x, int y, int z, int side) {
        Block block = worldIn.getBlock(x, y, z);
        return !this.ignoreSimilatiry && block == this ? false : super.shouldSideBeRendered(worldIn, x, y, z, side);
    }

    @Override
    public boolean isOpaqueCube() {
        return false;
    }
}
