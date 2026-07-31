package com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.controllers;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.cleanroommc.modularui.factory.GuiFactories;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.TileEntitySilo;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class BlockSilo extends BlockContainer {

    @SideOnly(Side.CLIENT)
    private IIcon frontIcon;

    @SideOnly(Side.CLIENT)
    private IIcon sideIcon;

    public BlockSilo() {
        super(Material.iron);
        setHardness(1.5f);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister reg) {
        this.frontIcon = reg.registerIcon("galaxia:machine/launchpad_controller");
        this.sideIcon = reg.registerIcon("galaxia:machine/launchpad_sheeting");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        return side == 2 ? frontIcon : sideIcon;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(IBlockAccess world, int x, int y, int z, int side) {
        TileEntity te = world.getTileEntity(x, y, z);

        if (te instanceof TileEntitySilo silo) {
            ForgeDirection facing = silo.getPlacedFacing();
            if (side == facing.ordinal()) {
                return frontIcon;
            }
        }

        return sideIcon;
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileEntitySilo();
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        if (world.isRemote) return true;
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileEntitySilo) {
            GuiFactories.tileEntity()
                .open(player, x, y, z);
            return true;
        }
        return false;
    }
}
