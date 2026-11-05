package com.gtnewhorizons.galaxia.registry.celestial.station.devices;

import java.util.function.Supplier;

import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.cleanroommc.modularui.factory.GuiFactories;
import com.gtnewhorizons.galaxia.core.Galaxia;

/**
 * Block of a station machine that keeps a room liveable
 */
public class BlockStationDevice extends Block implements ITileEntityProvider {

    private final Supplier<TileEntity> tile;

    public BlockStationDevice(String name, Supplier<TileEntity> tile) {
        super(Material.iron);
        this.tile = tile;
        setHardness(5.0F);
        setResistance(10.0F);
        setHarvestLevel("pickaxe", 1);
        setBlockName(name);
        setBlockTextureName("galaxia:space_station/" + name);
        setCreativeTab(Galaxia.creativeTab);
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return tile.get();
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        if (world.isRemote) return true;
        GuiFactories.tileEntity()
            .open(player, x, y, z);
        return true;
    }

    @Override
    public void breakBlock(World world, int x, int y, int z, Block block, int meta) {
        if (world.getTileEntity(x, y, z) instanceof IInventory inventory) {
            for (int i = 0; i < inventory.getSizeInventory(); i++) {
                ItemStack stack = inventory.getStackInSlot(i);
                if (stack != null) world.spawnEntityInWorld(new EntityItem(world, x + 0.5, y + 0.5, z + 0.5, stack));
            }
        }
        super.breakBlock(world, x, y, z, block, meta);
    }
}
