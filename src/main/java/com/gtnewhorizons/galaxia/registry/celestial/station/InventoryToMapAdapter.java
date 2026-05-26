package com.gtnewhorizons.galaxia.registry.celestial.station;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import com.gtnewhorizons.galaxia.registry.interfaces.IDistributedInventory;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.ResourceFilter;

public class InventoryToMapAdapter implements IDistributedInventory {

    private final IInventory inventory;
    private final ResourceFilter<ItemStackWrapper> filter;

    public InventoryToMapAdapter(IInventory inventory) {
        this(inventory, ResourceFilter.forItems());
    }

    public InventoryToMapAdapter(IInventory inventory, ResourceFilter<ItemStackWrapper> filter) {
        this.inventory = inventory;
        this.filter = filter;
    }

    @Override
    public Map<ItemStackWrapper, Long> getItemAmounts() {
        Map<ItemStackWrapper, Long> map = new LinkedHashMap<>();
        for (int s = 0; s < inventory.getSizeInventory(); s++) {
            ItemStack stack = inventory.getStackInSlot(s);
            if (stack != null) {
                ItemStackWrapper key = ItemStackWrapper.of(stack);
                if (key != null) map.merge(key, (long) stack.stackSize, Long::sum);
            }
        }
        return map;
    }

    @Override
    public long totalItemCapacity() {
        long sum = 0;
        for (int s = 0; s < inventory.getSizeInventory(); s++) {
            sum += inventory.getInventoryStackLimit();
        }
        return sum;
    }

    @Override
    public long getFreeItemSpace(ItemStackWrapper item) {
        if (!getItemFilter().test(item)) return 0L;
        long space = 0;
        ItemStack template = item.toStack(1);
        for (int s = 0; s < inventory.getSizeInventory(); s++) {
            ItemStack stack = inventory.getStackInSlot(s);
            if (stack == null) {
                space += Math.min(template.getMaxStackSize(), inventory.getInventoryStackLimit());
            } else if (stack.getItem() == item.item() && stack.getItemDamage() == item.meta()
                && ItemStack.areItemStackTagsEqual(stack, template)) {
                    int limit = Math.min(stack.getMaxStackSize(), inventory.getInventoryStackLimit());
                    space += Math.max(0, limit - stack.stackSize);
                }
        }
        return space;
    }

    @Override
    public long insertIntoOwnStorage(ItemStackWrapper item, long target) {
        if (item == null || target <= 0) return 0;
        ItemStack template = item.toStack(1);
        long transferred = 0;
        boolean dirty = false;

        for (int s = 0; s < inventory.getSizeInventory() && transferred < target; s++) {
            ItemStack stack = inventory.getStackInSlot(s);
            if (stack == null || stack.getItem() != item.item()
                || stack.getItemDamage() != item.meta()
                || !ItemStack.areItemStackTagsEqual(stack, template)) continue;
            int limit = Math.min(stack.getMaxStackSize(), inventory.getInventoryStackLimit());
            int space = limit - stack.stackSize;
            if (space > 0) {
                int toAdd = (int) Math.min(target - transferred, space);
                stack.stackSize += toAdd;
                transferred += toAdd;
                dirty = true;
            }
        }

        for (int s = 0; s < inventory.getSizeInventory() && transferred < target; s++) {
            if (inventory.getStackInSlot(s) != null) continue;
            int maxSize = Math.min(template.getMaxStackSize(), inventory.getInventoryStackLimit());
            int toAdd = (int) Math.min(target - transferred, maxSize);
            inventory.setInventorySlotContents(s, item.toStack(toAdd));
            transferred += toAdd;
            dirty = true;
        }

        if (dirty) inventory.markDirty();
        return transferred;
    }

    @Override
    public long extractFromOwnStorage(ItemStackWrapper item, long target) {
        if (item == null || target <= 0) return 0;
        long transferred = 0;
        boolean dirty = false;

        for (int s = 0; s < inventory.getSizeInventory() && transferred < target; s++) {
            ItemStack stack = inventory.getStackInSlot(s);
            if (stack == null || !item.equals(ItemStackWrapper.of(stack))) continue;
            int toRemove = (int) Math.min(target - transferred, stack.stackSize);
            stack.stackSize -= toRemove;
            transferred += toRemove;
            dirty = true;
            if (stack.stackSize <= 0) inventory.setInventorySlotContents(s, null);
        }

        if (dirty) inventory.markDirty();
        return transferred;
    }

    @Override
    public void markDirty() {
        inventory.markDirty();
    }

    @Override
    public ResourceFilter<ItemStackWrapper> getItemFilter() {
        return filter;
    }
}
