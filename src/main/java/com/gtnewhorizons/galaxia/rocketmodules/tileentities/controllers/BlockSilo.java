package com.gtnewhorizons.galaxia.rocketmodules.tileentities.controllers;

import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.cleanroommc.modularui.factory.GuiFactories;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaBlocksEnum;
import com.gtnewhorizons.galaxia.rocketmodules.tileentities.TileEntitySilo;

/**
 * Block for the Rocket Silo Controller
 */
public class BlockSilo extends BlockRocketController implements ITileEntityProvider {

    public BlockSilo() {
        super("galaxia:machine/silo_on", "galaxia:machine/silo_off", () -> GalaxiaBlocksEnum.RUSTY_PANEL.get());
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
        if (te instanceof TileEntitySilo) GuiFactories.tileEntity()
            .open(player, x, y, z);
        return true;
    }

    @Override
    public void breakBlock(World world, int x, int y, int z, Block block, int meta) {

        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileEntitySilo silo) silo.kill();

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
    public ForgeDirection getFacing(IBlockAccess world, int x, int y, int z) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileEntitySilo silo) {
            return silo.isStructureValid() ? silo.getCurrentFacing()
                .getDirection() : silo.getPlacedFacing();
        }
        return ForgeDirection.NORTH;
    }

    @Override
    public boolean isFormed(IBlockAccess world, int x, int y, int z) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileEntitySilo silo) return silo.isStructureValid();
        return false;
    }
}
