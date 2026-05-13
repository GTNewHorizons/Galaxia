package com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.controllers;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

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
}
