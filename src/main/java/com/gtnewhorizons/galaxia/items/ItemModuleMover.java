package com.gtnewhorizons.galaxia.items;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizons.galaxia.block.GalaxiaBlocks;
import com.gtnewhorizons.galaxia.modules.TileEntityModuleController;

/**
 * Item to help with moving modules in world
 */
public class ItemModuleMover extends Item {

    public ItemModuleMover() {
        super();
        setMaxStackSize(1);
        setUnlocalizedName("moduleMover");
    }

    /**
     * Gets the coordinates of a selected module / null if none selected
     * 
     * @param stack The item stack for the item
     * @return An integer array holding the position, or null if no selected
     */
    public static int[] getSelectedPos(ItemStack stack) {
        if (stack.hasTagCompound()) {
            NBTTagCompound nbt = stack.getTagCompound();
            if (nbt.hasKey("selectedX")) {
                return new int[] { nbt.getInteger("selectedX"), nbt.getInteger("selectedY"),
                    nbt.getInteger("selectedZ") };
            }
        }
        return null;
    }

    /**
     * Sets the selected position in the NBT data
     * 
     * @param stack The item stack for the item
     * @param x     The x coordinate of the module
     * @param y     The y coordinate of the module
     * @param z     The z coordinate of the module
     */
    public static void setSelectedPos(ItemStack stack, int x, int y, int z) {
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        NBTTagCompound nbt = stack.getTagCompound();
        nbt.setInteger("selectedX", x);
        nbt.setInteger("selectedY", y);
        nbt.setInteger("selectedZ", z);
    }

    /**
     * Clears the current selection from NBT data
     * 
     * @param stack The item stack for the item
     */
    public static void clearSelected(ItemStack stack) {
        if (stack.hasTagCompound()) {
            NBTTagCompound nbt = stack.getTagCompound();
            nbt.removeTag("selectedX");
            nbt.removeTag("selectedY");
            nbt.removeTag("selectedZ");
        }
    }

    /**
     * Selects a module based on the given coordinates
     * 
     * @param world  The world in which the item is used
     * @param x      The x coordinates to get the module from
     * @param y      The y coordinates to get the module from
     * @param z      The z coordinates to get the module from
     * @param player The player entity using the item
     * @param stack  The item stack for the item
     */
    public void selectModule(World world, int x, int y, int z, EntityPlayer player, ItemStack stack) {
        setSelectedPos(stack, x, y, z);
        player.addChatComponentMessage(new ChatComponentTranslation("galaxia.module_mover.module_selected"));
    }

    /**
     * Handler for when the item is used
     * 
     * @param stack  The item stack for the item
     * @param player The player entity using the item
     * @param world  The world in which the item is used
     * @param x      The x coordinates used on
     * @param y      The y coordinates used on
     * @param z      The z coordinates used on
     * @param side   The direction faced
     * @param hitX   Not used in this implementation, required from original signature
     * @param hitY   Not used in this implementation, required from original signature
     * @param hitZ   Not used in this implementation, required from original signature
     * @return Boolean : True => Successful use
     */
    @Override
    public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
        float hitX, float hitY, float hitZ) {
        if (world.isRemote) return true;

        int[] selected = getSelectedPos(stack);
        if (selected == null) {
            player.addChatComponentMessage(new ChatComponentTranslation("galaxia.module_mover.no_module_selected"));
            return false;
        }

        TileEntityModuleController oldTe = (TileEntityModuleController) world
            .getTileEntity(selected[0], selected[1], selected[2]);
        if (oldTe == null || oldTe.getType() == null) {
            clearSelected(stack);
            return false;
        }

        String moduleId = oldTe.getType()
            .getId();

        ForgeDirection dir = ForgeDirection.getOrientation(side);
        int nx = x + dir.offsetX;
        int ny = y + dir.offsetY;
        int nz = z + dir.offsetZ;

        boolean samePosition = selected[0] == nx && selected[1] == ny && selected[2] == nz;

        if (!samePosition) {
            if (!world.isAirBlock(nx, ny, nz) && !world.getBlock(nx, ny, nz)
                .isReplaceable(world, nx, ny, nz)) {
                player.addChatComponentMessage(new ChatComponentTranslation("galaxia.module_mover.cannot_place_here"));
                return false;
            }
        }

        if (!samePosition) {
            oldTe.destroyStructure();
            world.setBlockToAir(selected[0], selected[1], selected[2]);

            world.setBlock(nx, ny, nz, GalaxiaBlocks.MODULE_CONTROLLER.get());
            TileEntityModuleController newTe = (TileEntityModuleController) world.getTileEntity(nx, ny, nz);
            if (newTe != null) {
                newTe.setModule(moduleId);
                newTe.buildStructure();
            }
        } else {
            oldTe.buildStructure();
        }

        player.addChatComponentMessage(new ChatComponentTranslation("galaxia.module_mover.module_moved"));
        clearSelected(stack);
        return true;
    }

    /**
     * Handler for the start of one of the blocks being broken - prevents player from destroying modules in survival
     * 
     * @param itemstack The current ItemStack
     * @param x         The X Position
     * @param y         The Y Position
     * @param z         The Z Position
     * @param player    The player entity holding the item
     * @return Boolean : True => Successful break
     */
    @Override
    public boolean onBlockStartBreak(ItemStack itemstack, int x, int y, int z, EntityPlayer player) {
        if (!player.capabilities.isCreativeMode) return false;

        World world = player.worldObj;
        if (world.getBlock(x, y, z) == GalaxiaBlocks.MODULE_CONTROLLER.get()) {
            selectModule(world, x, y, z, player, itemstack);
        }
        return true;
    }

    /**
     * Adds information to the stat collector about the module
     * 
     * @param stack    The item stack of the item
     * @param player   The player entity using the item
     * @param list     The list of information to add to
     * @param advanced Not used in this implementation, required from original signature
     */
    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        int[] pos = getSelectedPos(stack);
        if (pos != null) {
            list.add(
                StatCollector
                    .translateToLocalFormatted("galaxia.tooltip.module_mover.selected", pos[0], pos[1], pos[2]));
        }
        list.add(StatCollector.translateToLocal("galaxia.tooltip.module_mover.instruction_left"));
        list.add(StatCollector.translateToLocal("galaxia.tooltip.module_mover.instruction_right"));
    }
}
