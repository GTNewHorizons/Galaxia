package com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities;

import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.cleanroommc.modularui.factory.GuiFactories;
import com.gtnewhorizons.galaxia.registry.items.special.ItemRocketSchematic;

public class BlockRocketTrophy extends BlockContainer {

    public BlockRocketTrophy() {
        super(Material.iron);
        setBlockTextureName("diamond_block");
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileEntityRocketTrophy();
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        if (world.isRemote) return true;

        TileEntityRocketTrophy te = (TileEntityRocketTrophy) world.getTileEntity(x, y, z);
        if (te == null) return false;

        ItemStack held = player.getHeldItem();
        boolean holdingSchematic = held != null && held.getItem() instanceof ItemRocketSchematic;

        if (holdingSchematic && te.getSchematic() == null) {
            // Insert
            te.setSchematic(held.copy());
            if (!player.capabilities.isCreativeMode)
                player.inventory.setInventorySlotContents(player.inventory.currentItem, null);
            return true;
        } else if (held == null && te.getSchematic() != null && player.isSneaking()) {
            // Extract
            player.inventory.addItemStackToInventory(
                te.getSchematic()
                    .copy());
            te.setSchematic(null);
            return true;
        }
        GuiFactories.tileEntity()
            .open(player, x, y, z);
        return true;
    }

    @Override
    public void breakBlock(World world, int x, int y, int z, Block block, int meta) {
        TileEntityRocketTrophy te = (TileEntityRocketTrophy) world.getTileEntity(x, y, z);
        if (te != null && te.getSchematic() != null) {
            EntityItem drop = new EntityItem(
                world,
                x + 0.5,
                y + 0.5,
                z + 0.5,
                te.getSchematic()
                    .copy());
            world.spawnEntityInWorld(drop);
        }
        super.breakBlock(world, x, y, z, block, meta);
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
