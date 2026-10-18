package com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.controllers;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.cleanroommc.modularui.factory.GuiFactories;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.TileEntityRocketLoadingPort;

/** Minecraft registers the physical block and its inventory tile provider together. */
public class BlockRocketLoadingPort extends BlockContainer {

    public BlockRocketLoadingPort() {
        super(Material.iron);
        setHardness(1.5f);
        setBlockTextureName("galaxia:machine/launchpad_assembling_casing");
    }

    @Override
    public TileEntity createNewTileEntity(World world, int metadata) {
        return new TileEntityRocketLoadingPort();
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        if (world.isRemote) return true;
        if (world.getTileEntity(x, y, z) instanceof TileEntityRocketLoadingPort port
            && port.isUseableByPlayer(player)) {
            GuiFactories.tileEntity()
                .open(player, x, y, z);
            return true;
        }
        return false;
    }
}
