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

import com.cleanroommc.modularui.factory.GuiFactories;
import com.gtnewhorizons.galaxia.registry.block.base.BlockUpdatable;
import com.gtnewhorizons.galaxia.registry.block.tile.TileHammerTarget;

public class BlockHammerTarget extends BlockUpdatable implements ITileEntityProvider {

    public BlockHammerTarget() {
        super(Material.iron);
    }

    @Override
    public TileEntity createNewTileEntity(World worldIn, int meta) {
        return new TileHammerTarget();
    }

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {

        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof TileHammerTarget sm)) return;

        int f = MathHelper.floor_double((placer.rotationYaw * 4.0F / 360.0F) + 0.5D) & 3;
        ForgeDirection[] dirs = { ForgeDirection.NORTH, ForgeDirection.EAST, ForgeDirection.SOUTH,
            ForgeDirection.WEST };
        sm.setPlacedFacing(dirs[f]);
        sm.setFacing(dirs[f]);
    }

    @Override
    public boolean onBlockActivated(World worldIn, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        if (worldIn.isRemote) return true;
        TileEntity te = worldIn.getTileEntity(x, y, z);
        if (!(te instanceof TileHammerTarget)) return false;

        GuiFactories.tileEntity()
            .open(player, x, y, z);

        return true;
    }
}
