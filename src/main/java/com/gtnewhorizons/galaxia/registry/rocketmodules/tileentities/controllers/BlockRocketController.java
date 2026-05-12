package com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.controllers;

import java.util.function.Supplier;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.util.MathHelper;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizons.galaxia.registry.block.base.BlockUpdatable;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.IRocketControllerTE;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Abstract block class for handling controllers of multiblocks relating to launching a rocket
 */
public abstract class BlockRocketController extends BlockUpdatable {

    @SideOnly(Side.CLIENT)
    protected IIcon frontIconOff;

    @SideOnly(Side.CLIENT)
    protected IIcon frontIconOn;

    protected Supplier<Block> controllerMaterial;

    protected final String onLocationString;

    protected final String offLocationString;

    protected BlockRocketController(String onIcon, String offIcon, Supplier<Block> material) {
        super(Material.rock);
        this.setHardness(1.5F);
        this.onLocationString = onIcon;
        this.offLocationString = offIcon;
        this.controllerMaterial = material;
    }

    /**
     * Subclasses return the facing direction by reading their specific TE
     */
    public abstract ForgeDirection getFacing(IBlockAccess world, int x, int y, int z);

    /**
     * Subclasses return whether the structure is formed by reading their specific TE
     */
    public abstract boolean isFormed(IBlockAccess world, int x, int y, int z);

    /**
     * Makes sure the controller face always faces the player
     */
    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        int f = MathHelper.floor_double((placer.rotationYaw * 4.0F / 360.0F) + 0.5D) & 3;
        ForgeDirection[] dirs = { ForgeDirection.NORTH, ForgeDirection.EAST, ForgeDirection.SOUTH,
            ForgeDirection.WEST };
        ForgeDirection dir = dirs[f];
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof IRocketControllerTE rte) rte.setPlacedFacing(dir);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister iconRegister) {
        this.frontIconOff = iconRegister.registerIcon(offLocationString);
        this.frontIconOn = iconRegister.registerIcon(onLocationString);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(IBlockAccess world, int x, int y, int z, int side) {
        ForgeDirection sideDir = ForgeDirection.getOrientation(side);

        ForgeDirection facing = getFacing(world, x, y, z);
        boolean formed = isFormed(world, x, y, z);

        if (sideDir == facing) {
            return formed ? frontIconOn : frontIconOff;
        }

        return controllerMaterial.get()
            .getIcon(side, 0);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        if (side == ForgeDirection.WEST.ordinal()) {
            return frontIconOff;
        }
        return controllerMaterial.get()
            .getIcon(side, 0);
    }
}
