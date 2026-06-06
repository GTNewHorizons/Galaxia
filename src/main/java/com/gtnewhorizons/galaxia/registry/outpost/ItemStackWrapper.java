package com.gtnewhorizons.galaxia.registry.outpost;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.registry.GameRegistry;

/**
 * NBT-aware wrapper for ItemStack to be used as a key in HashMaps.
 */

public record ItemStackWrapper(Item item, int meta, NBTTagCompound nbt) implements InventoryKey {

    private static final Logger LOG = LogManager.getLogger("Galaxia");

    public static ItemStackWrapper of(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return null;
        }
        return new ItemStackWrapper(
            stack.getItem(),
            stack.getItemDamage(),
            stack.hasTagCompound() ? (NBTTagCompound) stack.getTagCompound()
                .copy() : null);
    }

    public String toKey() {
        GameRegistry.UniqueIdentifier id;
        try {
            id = GameRegistry.findUniqueIdentifierFor(item);
        } catch (RuntimeException e) {
            LOG.warn(
                "[ItemStackWrapper] Item {} cannot be resolved by the registry; key will not resolve on reload.",
                item != null ? item.getClass()
                    .getName() : "null",
                e);
            return "unknown:unknown:" + meta;
        }
        if (id == null) {
            LOG.warn(
                "[ItemStackWrapper] Item {} has no registry entry; key will not resolve on reload.",
                item != null ? item.getClass()
                    .getName() : "null");
            return "unknown:unknown:" + meta;
        }
        return id.modId + ":" + id.name + ":" + meta;
    }

    public static ItemStackWrapper fromKey(String key) {
        String[] parts = key.split(":");
        if (parts.length < 3) return null;
        try {
            Item item = GameRegistry.findItem(parts[0], parts[1]);
            if (item == null) {
                LOG.warn("[ItemStackWrapper] Unknown item key '{}'; entry dropped.", key);
                return null;
            }
            int meta = Integer.parseInt(parts[2]);
            return new ItemStackWrapper(item, meta, null);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public ItemStack toStack(long amount) {
        ItemStack stack = new ItemStack(item, (int) amount, meta);
        if (nbt != null) {
            stack.setTagCompound((NBTTagCompound) nbt.copy());
        }
        return stack;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ItemStackWrapper that = (ItemStackWrapper) o;
        if (meta != that.meta) return false;
        if (item != that.item) return false;
        if (nbt == null) return that.nbt == null;
        return nbt.equals(that.nbt);
    }

    @Override
    public int hashCode() {
        int result = item != null ? item.hashCode() : 0;
        result = 31 * result + meta;
        result = 31 * result + (nbt != null ? nbt.hashCode() : 0);
        return result;
    }

    public ItemStack toItemStack() {
        return new ItemStack(item, 1, meta);
    }
}
