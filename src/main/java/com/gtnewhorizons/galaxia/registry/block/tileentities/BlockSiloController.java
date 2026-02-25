package com.gtnewhorizons.galaxia.registry.block.tileentities;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.handlers.GuiHandler;

public class BlockSiloController extends BlockContainer {

    public BlockSiloController() {
        super(Material.iron);
        setBlockName("siloController");
        setHardness(5.0F);
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileSiloController();
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hx, float hy,
        float hz) {
        if (!world.isRemote) {
            player.openGui(Galaxia.instance, GuiHandler.GUI_SILO, world, x, y, z);
        }
        return true;
    }

    @Override
    public void breakBlock(World world, int x, int y, int z, net.minecraft.block.Block oldBlock, int oldMeta) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileSiloController) ((TileSiloController) te).removeRocket();
        super.breakBlock(world, x, y, z, oldBlock, oldMeta);
    }
}
