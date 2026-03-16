package com.gtnewhorizons.galaxia.rocketmodules.tileentities.gantry;

import java.util.ArrayList;

import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

public class BlockGantry extends Block implements ITileEntityProvider {

    public BlockGantry() {
        super(Material.iron);
        this.setBlockTextureName("iron_block");
        this.setHardness(1.5F);
        this.setResistance(10.0f);
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileEntityGantry();
    }

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        if (world.isRemote) return;

        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof TileEntityGantry)) {
            return;
        }
        TileEntityGantry teg = (TileEntityGantry) te;

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

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        if (world.isRemote) return true;
        if (player.isSneaking()) return false;

        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof TileEntityGantry)) {
            return false;
        }
        // Debugging chat message
        TileEntityGantry teg = (TileEntityGantry) te;
        player.addChatComponentMessage(
            new ChatComponentText(
                "Module: " + teg.getModule()
                    + ", Direction: "
                    + teg.getDirection()
                    + ", isJunction: "
                    + teg.isJunction
                    + ", facing: "
                    + teg.facing));
        return true;

    }

    @Override
    public void breakBlock(World world, int x, int y, int z, Block block, int meta) {

        TileEntity gantry = world.getTileEntity(x, y, z);
        if (!(gantry instanceof TileEntityGantry)) {
            return;
        }
        TileEntityGantry terminal = (TileEntityGantry) gantry;

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
    public boolean renderAsNormalBlock() {
        return false;
    }

    @Override
    public boolean isOpaqueCube() {
        return false;
    }

    @Override
    public int getRenderType() {
        return -1;
    }
}
