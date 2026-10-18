package com.gtnewhorizons.galaxia.registry.block.special;

import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;

import com.cleanroommc.modularui.factory.GuiFactories;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.registry.dimension.OrbitDimensions;

/**
 * Upper end of a space elevator, placed in orbit straight above the elevator, in the same chunk column
 */
public class BlockElevatorDock extends Block implements ITileEntityProvider {

    public BlockElevatorDock() {
        super(Material.iron);
        setHardness(5.0F);
        setResistance(10.0F);
        setHarvestLevel("pickaxe", 1);
        setBlockName("elevator_dock");
        setBlockTextureName("galaxia:space_station/elevator_dock");
        setCreativeTab(Galaxia.creativeTab);
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileElevatorDock();
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        if (world.isRemote) return true;
        if (!OrbitDimensions.isOrbit(world.provider.dimensionId)) {
            player.addChatComponentMessage(new ChatComponentTranslation("galaxia.chat.elevator_dock.not_in_orbit"));
        }
        GuiFactories.tileEntity()
            .open(player, x, y, z);
        return true;
    }

    @Override
    public void breakBlock(World world, int x, int y, int z, Block block, int meta) {
        if (world.getTileEntity(x, y, z) instanceof TileElevatorDock dock) {
            for (int i = 0; i < dock.getSizeInventory(); i++) {
                ItemStack stack = dock.getStackInSlot(i);
                if (stack != null) world.spawnEntityInWorld(new EntityItem(world, x + 0.5, y + 0.5, z + 0.5, stack));
            }
        }
        super.breakBlock(world, x, y, z, block, meta);
    }
}
