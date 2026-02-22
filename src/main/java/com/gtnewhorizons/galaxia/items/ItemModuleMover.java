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

public class ItemModuleMover extends Item {

    private static final String LANG_PREFIX = "galaxia.module_mover.";

    public ItemModuleMover() {
        super();
        setMaxStackSize(1);
        setUnlocalizedName("moduleMover");
    }

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

    public static void setSelectedPos(ItemStack stack, int x, int y, int z) {
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        NBTTagCompound nbt = stack.getTagCompound();
        nbt.setInteger("selectedX", x);
        nbt.setInteger("selectedY", y);
        nbt.setInteger("selectedZ", z);
    }

    public static void clearSelected(ItemStack stack) {
        if (stack.hasTagCompound()) {
            NBTTagCompound nbt = stack.getTagCompound();
            nbt.removeTag("selectedX");
            nbt.removeTag("selectedY");
            nbt.removeTag("selectedZ");
        }
    }

    public void selectModule(World world, int x, int y, int z, EntityPlayer player, ItemStack stack) {
        setSelectedPos(stack, x, y, z);
        player.addChatComponentMessage(new ChatComponentTranslation(LANG_PREFIX + "module_selected"));
    }

    @Override
    public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
        float hitX, float hitY, float hitZ) {
        if (world.isRemote) return true;

        int[] selected = getSelectedPos(stack);
        if (selected == null) {
            player.addChatComponentMessage(new ChatComponentTranslation(LANG_PREFIX + "no_module_selected"));
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
                player.addChatComponentMessage(new ChatComponentTranslation(LANG_PREFIX + "cannot_place_here"));
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

        player.addChatComponentMessage(new ChatComponentTranslation(LANG_PREFIX + "module_moved"));
        clearSelected(stack);
        return true;
    }

    // prevent player from destroying module on left click
    @Override
    public boolean onBlockStartBreak(ItemStack itemstack, int x, int y, int z, EntityPlayer player) {
        if (!player.capabilities.isCreativeMode) return false;

        World world = player.worldObj;
        if (world.getBlock(x, y, z) == GalaxiaBlocks.MODULE_CONTROLLER.get()) {
            selectModule(world, x, y, z, player, itemstack);
        }
        return true;
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        int[] pos = getSelectedPos(stack);
        if (pos != null) {
            list.add(StatCollector.translateToLocalFormatted(LANG_PREFIX + "tooltip.selected", pos[0], pos[1], pos[2]));
        }
        list.add(StatCollector.translateToLocal(LANG_PREFIX + "tooltip.instruction_left"));
        list.add(StatCollector.translateToLocal(LANG_PREFIX + "tooltip.instruction_right"));
    }
}
