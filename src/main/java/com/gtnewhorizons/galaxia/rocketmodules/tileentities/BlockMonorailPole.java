package com.gtnewhorizons.galaxia.rocketmodules.tileentities;

import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

public class BlockMonorailPole extends Block implements ITileEntityProvider {

    public BlockMonorailPole() {
        super(Material.iron);
        this.setBlockTextureName("iron_block");
        this.setHardness(2.0F);
    }

    @Override
    public TileEntity createNewTileEntity(World worldIn, int meta) {
        return new TileEntityMonorailPole();
    }
}
