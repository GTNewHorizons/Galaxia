package com.gtnewhorizons.galaxia.rocketmodules.tileentities.gantry;

import java.util.ArrayList;

import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

public class BlockGantry extends Block implements ITileEntityProvider {

    public BlockGantry() {
        super(Material.iron);
        this.setHardness(1.5F);
        this.setResistance(10.0f);
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileEntityGantry();
    }

    /**
     * Handles logic to be ran on block placing - in this case, connecting to other
     * gantries
     *
     * @param world  The world placed in
     * @param x      X position of placed block
     * @param y      Y position of placed block
     * @param z      Z position of placed block
     * @param placer The placer of the block
     * @param stack  The item stack being used to place
     */
    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        if (world.isRemote) return;

        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof TileEntityGantry teg)) {
            return;
        }

        // Check valid directions and connect to others
        for (Vec3 check_offset : GantryAPI.CHECK_OFFSETS) {
            int cx = x + (int) check_offset.xCoord;
            int cy = y + (int) check_offset.yCoord;
            int cz = z + (int) check_offset.zCoord;

            TileEntity checkTe = world.getTileEntity(cx, cy, cz);
            if (checkTe instanceof TileEntityGantry checkGantry) {
                teg.connect(checkGantry);
            }
        }

    }

    /**
     * Handles logic on block break - in this case disconnecting from other gantries
     *
     * @param world  The world placed in
     * @param x      X position of placed block
     * @param y      Y position of placed block
     * @param z      Z position of placed block
     * @param placer The placer of the block
     * @param stack  The item stack being used to place
     */
    @Override
    public void breakBlock(World world, int x, int y, int z, Block block, int meta) {

        TileEntity gantry = world.getTileEntity(x, y, z);
        if (!(gantry instanceof TileEntityGantry terminal)) {
            return;
        }

        // Iterate through neighbours and disconnect them
        for (Vec3 check_offset : new ArrayList<>(terminal.neighbourDirs)) {
            int cx = x + (int) check_offset.xCoord;
            int cy = y + (int) check_offset.yCoord;
            int cz = z + (int) check_offset.zCoord;

            TileEntity checkTileEntity = world.getTileEntity(cx, cy, cz);
            if (checkTileEntity instanceof TileEntityGantry checkGantry) {
                terminal.disconnect(checkGantry);
            }
        }

    }

    @Override
    public boolean canPlaceBlockAt(World world, int x, int y, int z) {
        // if there is any gantry above/below the block without air gap
        if (world.getBlock(x, y - 1, z) == this) return false;
        if (world.getBlock(x, y + 1, z) == this) return false;
        return super.canPlaceBlockAt(world, x, y, z);
    }

    @Override
    public boolean renderAsNormalBlock() {
        return false;
    }

    @Override
    public boolean isOpaqueCube() {
        return false;
    }

    /**
     * Overrides the render type to not use the block render engine, but instead
     * solely use TESR
     *
     * @return The render type (always -1 in this case)
     */
    @Override
    public int getRenderType() {
        return -1;
    }
}
