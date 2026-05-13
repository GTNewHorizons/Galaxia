package com.gtnewhorizons.galaxia.registry.items.special;

import java.util.List;

import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartRegistry;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemMap;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.gtnewhorizons.galaxia.registry.items.GalaxiaItemList;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketBlueprint;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartInstance;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.TileEntitySilo;

public class ItemRocketSchematic extends ItemMap {

    public ItemRocketSchematic() {
        super();
    }

    public static ItemStack captureFromSilo(TileEntitySilo silo, String name) {
        RocketBlueprint blueprint = silo.getBlueprint();

        if (blueprint == null || blueprint.getParts().isEmpty()) {
            return null;
        }

        ItemStack stack = new ItemStack(GalaxiaItemList.ITEM_ROCKET_SCHEMATIC.getItem());

        NBTTagCompound tag = new NBTTagCompound();

        tag.setString(
            "schematicName",
            name.isEmpty()
                ? StatCollector.translateToLocal("item.galaxia.rocket_schematic.saved_name_none")
                : name);

        tag.setTag("blueprint", blueprint.serialize());

        stack.setTagCompound(tag);

        return stack;
    }

    public static RocketBlueprint readBlueprint(ItemStack stack) {

        if (stack == null || !stack.hasTagCompound()) {
            return new RocketBlueprint();
        }

        NBTTagCompound tag = stack.getTagCompound();

        if (!tag.hasKey("blueprint")) {
            return new RocketBlueprint();
        }

        return RocketBlueprint.deserialize(
            tag.getCompoundTag("blueprint"),
            RocketPartRegistry.instance()
        );
    }

    public static String readName(ItemStack stack) {
        if (stack == null || !stack.hasTagCompound()) {
            return StatCollector.translateToLocal("item.galaxia.rocket_schematic.saved_name_none");
        }

        NBTTagCompound tag = stack.getTagCompound();

        if (!tag.hasKey("schematicName")) {
            return StatCollector.translateToLocal("item.galaxia.rocket_schematic.saved_name_none");
        }

        return tag.getString("schematicName");
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List<String> tooltip, boolean advanced) {
        RocketBlueprint blueprint = readBlueprint(stack);

        tooltip.add(
            EnumChatFormatting.AQUA
                + readName(stack)
                + EnumChatFormatting.RESET);

        if (blueprint.getParts().isEmpty()) {
            tooltip.add(
                EnumChatFormatting.RED
                    + StatCollector.translateToLocal("item.galaxia.rocket_schematic.empty")
                    + EnumChatFormatting.RESET);
            return;
        }

        int width = 0;
        int height = 0;
        float totalWeight = 0;

        for (RocketPartInstance part : blueprint.getParts()) {

            width = Math.max(
                width,
                part.x() + part.def().getWidthCells());

            height = Math.max(
                height,
                part.y() + part.def().getHeightCells());

            totalWeight += part.def().weight();
        }

        tooltip.add(
            StatCollector.translateToLocalFormatted(
                "item.galaxia.rocket_schematic.modules",
                blueprint.getParts().size()));

        tooltip.add(
            StatCollector.translateToLocalFormatted(
                "item.galaxia.rocket_schematic.height",
                height));

        tooltip.add(
            StatCollector.translateToLocalFormatted(
                "item.galaxia.rocket_schematic.width",
                width));

        tooltip.add(
            StatCollector.translateToLocalFormatted(
                "item.galaxia.rocket_schematic.weight",
                totalWeight));
    }
}
