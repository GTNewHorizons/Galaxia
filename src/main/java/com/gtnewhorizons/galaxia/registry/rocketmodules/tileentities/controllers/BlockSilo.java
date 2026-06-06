package com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.controllers;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.cleanroommc.modularui.factory.GuiFactories;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.TileEntitySilo;

public class BlockSilo extends BlockContainer {

    public BlockSilo() {
        super(Material.iron);
        setHardness(1.5f);
        setBlockName("galaxia.silo");
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
