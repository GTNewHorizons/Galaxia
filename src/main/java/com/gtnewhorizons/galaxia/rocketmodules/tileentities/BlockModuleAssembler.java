package com.gtnewhorizons.galaxia.rocketmodules.tileentities;

import com.cleanroommc.modularui.factory.GuiFactories;
import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

public class BlockModuleAssembler extends Block implements ITileEntityProvider {

    public BlockModuleAssembler() {
        super(Material.rock);
        this.setBlockTextureName("dirt");
        this.setHardness(1.5F);
    }
    @Override
    public TileEntity createNewTileEntity(World worldIn, int meta) {
        return new TileEntityModuleAssembler();
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX, float hitY, float hitZ) {
        if (world.isRemote) return true;
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileEntityModuleAssembler) GuiFactories.tileEntity()
            .open(player, x, y, z);
        return true;
    }
}
