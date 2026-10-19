package com.gtnewhorizons.galaxia.registry.items.special;

import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemMap;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.Packet;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapData;

import com.gtnewhorizons.galaxia.registry.items.GalaxiaItemList;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketBlueprint;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartRegistry;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.TileEntitySilo;

/**
 * A rocket design on paper. It is a map to the game only so it is held in both hands and drawn like one, it has no
 * map data of its own
 */
public class ItemRocketSchematic extends ItemMap {

    private static final String NBT_KEY_BLUEPRINT = "GalaxiaRocketBlueprint";
    private static final String NBT_KEY_NAME = "SchematicName";

    public ItemRocketSchematic() {
        setMaxStackSize(1);
        setHasSubtypes(false);
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
        if (silo == null || silo.getDesignBlueprint()
            .isEmpty()) return null;

        ItemStack stack = new ItemStack(GalaxiaItemList.ITEM_ROCKET_SCHEMATIC.getItem());
        RocketBlueprint bp = silo.getDesignBlueprint()
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

    // region No map behind the paper

    @Override
    public MapData getMapData(ItemStack stack, World world) {
        return null;
    }

    @Override
    public void onUpdate(ItemStack stack, World world, Entity entity, int slot, boolean held) {}

    @Override
    public Packet func_150911_c(ItemStack stack, World world, EntityPlayer player) {
        return null;
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List<String> tooltip, boolean advanced) {}

    // endregion
}
