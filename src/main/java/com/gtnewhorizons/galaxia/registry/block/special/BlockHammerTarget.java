package com.gtnewhorizons.galaxia.registry.block.special;

import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.cleanroommc.modularui.factory.GuiFactories;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaMultiblockBase;
import com.gtnewhorizons.galaxia.registry.block.base.BlockUpdatable;
import com.gtnewhorizons.galaxia.registry.celestial.station.attachments.TileHammerTarget;

public class BlockHammerTarget extends BlockUpdatable implements ITileEntityProvider {

    public BlockHammerTarget() {
        super(Material.iron);
        setHardness(5.0F);
        setResistance(10.0F);
        setHarvestLevel("pickaxe", 1);
    }

    @Override
    public TileEntity createNewTileEntity(World worldIn, int meta) {
        return new TileHammerTarget();
    }

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {

        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof TileHammerTarget target)) return;

        target.onPlacedBy(placer);
    }

    @Override
    public boolean onBlockActivated(World worldIn, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        if (GalaxiaMultiblockBase.handleWrench(worldIn, x, y, z, player, side, hitX, hitY, hitZ)) return true;
        if (worldIn.isRemote) return true;
        TileEntity te = worldIn.getTileEntity(x, y, z);
        if (!(te instanceof TileHammerTarget)) return false;

        GuiFactories.tileEntity()
            .open(player, x, y, z);

        return true;
    }
}
