package com.gtnewhorizons.galaxia.registry.items.special;

import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketBlueprint;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartRegistry;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

public class ItemRocketSchematic extends Item {

    private static final String NBT_KEY_BLUEPRINT = "GalaxiaRocketBlueprint";

    public ItemRocketSchematic() {
        setMaxStackSize(1);
        setUnlocalizedName("galaxia.rocket_schematic");
    }

    public static void setBlueprint(ItemStack stack, RocketBlueprint blueprint) {
        if (stack == null || !(stack.getItem() instanceof ItemRocketSchematic)) return;
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }
        tag.setTag(NBT_KEY_BLUEPRINT, blueprint.serializeNBT());
    }

    public static RocketBlueprint getBlueprint(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof ItemRocketSchematic)) return null;
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey(NBT_KEY_BLUEPRINT)) return null;
        return RocketBlueprint.deserializeNBT(tag.getCompoundTag(NBT_KEY_BLUEPRINT), RocketPartRegistry.instance());
    }

    public static boolean hasBlueprint(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof ItemRocketSchematic)) return false;
        NBTTagCompound tag = stack.getTagCompound();
        return tag != null && tag.hasKey(NBT_KEY_BLUEPRINT);
    }

    @Override
    public void onCreated(ItemStack stack, World world, EntityPlayer player) {
        if (stack.getTagCompound() == null) {
            stack.setTagCompound(new NBTTagCompound());
        }
    }
}
