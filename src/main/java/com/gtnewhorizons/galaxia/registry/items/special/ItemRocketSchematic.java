package com.gtnewhorizons.galaxia.registry.items.special;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.Constants.NBT;

import com.gtnewhorizons.galaxia.registry.items.GalaxiaItemList;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.RocketAssembly;
import com.gtnewhorizons.galaxia.rocketmodules.tileentities.TileEntitySilo;

public class ItemRocketSchematic extends Item {

    public ItemRocketSchematic() {}

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

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List<String> tooltip, boolean p_77624_4_) {
        if (!stack.hasTagCompound()) return;
        NBTTagCompound tag = stack.getTagCompound();

        tooltip.add(EnumChatFormatting.AQUA + tag.getString("schematicName") + EnumChatFormatting.RESET);

        List<Integer> modules = readModules(stack);
        if (modules.isEmpty()) {
            tooltip.add(EnumChatFormatting.RED + StatCollector.translateToLocal("item.galaxia.rocket_schematic.empty"));
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
