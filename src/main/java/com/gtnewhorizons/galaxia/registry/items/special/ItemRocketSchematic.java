package com.gtnewhorizons.galaxia.registry.items.special;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemMap;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.Constants.NBT;

import com.gtnewhorizons.galaxia.registry.items.GalaxiaItemList;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.RocketAssembly;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.TileEntitySilo;

public class ItemRocketSchematic extends ItemMap {

    public ItemRocketSchematic() {
        super();
    }

    /**
     * Captures the current rocket assembly from a silo and saves it to a new
     * schematic item
     *
     * @param silo The rocket silo being saved from
     * @param name The name entered for the schematic build
     *
     * @return The ItemStack with the schematic
     */
    public static ItemStack captureFromSilo(TileEntitySilo silo, String name) {
        ArrayList<Integer> moduleIds = silo.getModules();
        if (moduleIds.isEmpty()) return null;

        NBTTagList list = new NBTTagList();
        for (int id : moduleIds) {
            NBTTagCompound moduleEntry = new NBTTagCompound();
            moduleEntry.setInteger("type", id);
            list.appendTag(moduleEntry);
        }
        NBTTagCompound tag = new NBTTagCompound();
        tag.setTag("modules", list);
        tag.setString(
            "schematicName",
            name.isEmpty() ? StatCollector.translateToLocal("item.galaxia.rocket_schematic.saved_name_none") : name);

        ItemStack stack = new ItemStack(GalaxiaItemList.ITEM_ROCKET_SCHEMATIC.getItem());
        stack.setTagCompound(tag);
        return stack;
    }

    /**
     * A static method to get the modules from a rocket schematic
     *
     * @param stack The item stack to read modules from
     *
     * @return The list of integer types containing all modules
     */
    public static List<Integer> readModules(ItemStack stack) {
        List<Integer> result = new ArrayList<>();
        if (stack == null || !stack.hasTagCompound()) return result;

        NBTTagList list = stack.getTagCompound()
            .getTagList("modules", NBT.TAG_COMPOUND);
        for (int i = 0; i < list.tagCount(); i++) {
            result.add(
                list.getCompoundTagAt(i)
                    .getInteger("type"));

        }
        return result;
    }

    /**
     * Reads the name of a schematic from the NBT data of a supplied schematic ItemStack
     *
     * @param stack The item stack to read from
     *
     * @return The name given from the NBT tag
     */
    public static String readName(ItemStack stack) {
        if (stack == null || !stack.hasTagCompound())
            return StatCollector.translateToLocal("item.galaxia.rocket_schematic.saved_name_none");
        NBTTagCompound tag = stack.getTagCompound();
        if (!tag.hasKey("schematicName"))
            return StatCollector.translateToLocal("item.galaxia.rocket_schematic.saved_name_none");

        return tag.getString("schematicName");
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List<String> tooltip, boolean p_77624_4_) {
        if (!stack.hasTagCompound()) return;
        NBTTagCompound tag = stack.getTagCompound();

        tooltip.add(EnumChatFormatting.AQUA + tag.getString("schematicName") + EnumChatFormatting.RESET);

        List<Integer> modules = readModules(stack);
        if (modules.isEmpty()) {
            tooltip.add(
                EnumChatFormatting.RED + StatCollector.translateToLocal("item.galaxia.rocket_schematic.empty")
                    + EnumChatFormatting.RESET);
            return;
        }

        RocketAssembly assembly = new RocketAssembly(modules);
        tooltip.add(StatCollector.translateToLocalFormatted("item.galaxia.rocket_schematic.modules", modules.size()));
        tooltip.add(
            StatCollector.translateToLocalFormatted("item.galaxia.rocket_schematic.height", assembly.getTotalHeight()));
        tooltip.add(
            StatCollector.translateToLocalFormatted("item.galaxia.rocket_schematic.width", assembly.getTotalWidth()));
    }
}
