package com.gtnewhorizons.galaxia.rocketmodules.tileentities;

import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.cleanroommc.modularui.factory.GuiFactories;
import com.gtnewhorizons.galaxia.registry.items.special.ItemLinkingTool;

public class BlockSilo extends Block implements ITileEntityProvider {

    public BlockSilo() {
        super(Material.rock);
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
        if (world.isRemote) return true;

        ItemStack held = player.getHeldItem();
        if (held != null && held.getItem() instanceof ItemLinkingTool) {
            return false;
        }

        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileEntitySilo) GuiFactories.tileEntity()
            .open(player, x, y, z);
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
