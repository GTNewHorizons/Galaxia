package com.gtnewhorizons.galaxia.rocketmodules.tileentities;

import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

public class BlockSilo extends Block implements ITileEntityProvider {

    public BlockSilo() {
        super(Material.rock);
        this.setBlockName("silo");
        this.setBlockTextureName("stone");
        this.setHardness(1.5F);
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileEntitySilo();
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        if (!world.isRemote) {
            TileEntitySilo te = (TileEntitySilo) world.getTileEntity(x, y, z);

            if (player.isSneaking()) {
                te.addModule(0); // TODO replace with proper gui
            } else if (te.getEntityRocket() != null) {
                player.mountEntity(te.getEntityRocket());
                te.getEntityRocket()
                    .launch();
            }

            te.markDirty();
            world.markBlockForUpdate(x, y, z);
        }
        return true;
    }

    @Override
    public boolean renderAsNormalBlock() {
        return false;
    }

    @Override
    public boolean isOpaqueCube() {
        return false;
    }
}
