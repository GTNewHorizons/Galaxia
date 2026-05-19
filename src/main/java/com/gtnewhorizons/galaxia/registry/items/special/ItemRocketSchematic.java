package com.gtnewhorizons.galaxia.registry.items.special;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketBlueprint;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartRegistry;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.TileEntitySilo;

public class ItemRocketSchematic extends Item {

    private static final String NBT_KEY_BLUEPRINT = "GalaxiaRocketBlueprint";
    private static final String NBT_KEY_NAME = "SchematicName";

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

    public static ItemStack captureFromSilo(TileEntitySilo silo, String schematicName) {
        if (silo == null || silo.getBuiltBlueprint()
            .isEmpty()) return null;

        ItemStack stack = new ItemStack(new ItemRocketSchematic());
        RocketBlueprint bp = silo.getBuiltBlueprint()
            .copy();

        setBlueprint(stack, bp);

        if (schematicName != null && !schematicName.trim()
            .isEmpty()) {
            NBTTagCompound tag = stack.getTagCompound();
            if (tag == null) tag = new NBTTagCompound();
            tag.setString(NBT_KEY_NAME, schematicName.trim());
            stack.setTagCompound(tag);
        }

        return stack;
    }

    @Override
    public void onCreated(ItemStack stack, World world, EntityPlayer player) {
        if (stack.getTagCompound() == null) {
            stack.setTagCompound(new NBTTagCompound());
        }
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag != null && tag.hasKey(NBT_KEY_NAME)) {
            return tag.getString(NBT_KEY_NAME);
        }
        return super.getItemStackDisplayName(stack);
    }
}
