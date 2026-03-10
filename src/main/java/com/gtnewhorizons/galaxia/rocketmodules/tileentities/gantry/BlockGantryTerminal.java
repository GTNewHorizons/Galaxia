
package com.gtnewhorizons.galaxia.rocketmodules.tileentities.gantry;

import static com.gtnewhorizons.galaxia.core.Galaxia.LOG;

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

public class BlockGantryTerminal extends Block implements ITileEntityProvider {

    public BlockGantryTerminal() {
        super(Material.iron);
        this.setBlockTextureName("gold_block");
        this.setHardness(1.5F);
        this.setResistance(10.0f);
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileEntityGantryTerminal();
    }

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        if (world.isRemote) return;

        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof TileEntityGantry)) {
            return;
        }
        TileEntityGantry teg = (TileEntityGantry) te;

        for (Vec3 check_offset : GantryAPI.CHECK_OFFSETS) {
            int cx = x + (int) check_offset.xCoord;
            int cy = y + (int) check_offset.yCoord;
            int cz = z + (int) check_offset.zCoord;

            TileEntity checkTe = world.getTileEntity(cx, cy, cz);
            if (checkTe instanceof TileEntityGantry checkTeg) {
                LOG.info("Connecting to: " + cx + ", " + cy + ", " + cz);
                teg.connect(checkTeg);
            }
        }

    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        if (world.isRemote) return true;

        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof TileEntityGantry)) {
            return false;
        }
        player.addChatComponentMessage(
            new ChatComponentText("Is connected: " + GantryAPI.terminatesWithTerminals(world, x, y, z)));
        return true;

    }

    @Override
    public boolean renderAsNormalBlock() {
        return false;
    }

    @Override
    public boolean isOpaqueCube() {
        return false;
    }
}
