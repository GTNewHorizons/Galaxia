package com.gtnewhorizons.galaxia.registry.block.special;

import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.registry.block.base.BlockOpenable;
import com.gtnewhorizons.galaxia.registry.celestial.station.TileEntityAirlock;

public class BlockAirlockController extends BlockOpenable implements ITileEntityProvider {

    public BlockAirlockController() {
        super(Material.rock);
        this.setHardness(1.5F);
        this.setBlockName("airlock_controller");
        this.setBlockTextureName("galaxia:machine/airlock_controller");
        this.setCreativeTab(Galaxia.creativeTab);
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileEntityAirlock();
    }

    @Override
    public boolean hasTileEntity(int meta) {
        return true;
    }

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        TileEntity te = world.getTileEntity(x, y, z);

        if (!(te instanceof TileEntityAirlock airlock)) return;

        int f = MathHelper.floor_double((placer.rotationYaw * 4.0F / 360.0F) + 0.5D) & 3;
        ForgeDirection[] dirs = { ForgeDirection.NORTH, ForgeDirection.EAST, ForgeDirection.SOUTH,
            ForgeDirection.WEST };
        airlock.setPlacedFacing(dirs[f]);
        airlock.setFacing(dirs[f]);
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        if (world.isRemote) return true;
        return toggleDoor(world, x, y, z);
    }

    public boolean toggleDoor(World world, int x, int y, int z) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileEntityAirlock) {
            ((TileEntityAirlock) te).toggleState();
            return true;
        }

        return false;
    }
}
