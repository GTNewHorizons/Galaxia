package com.gtnewhorizons.galaxia.registry.interfaces;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

public interface IDistributedInventory extends IInventory, IFilteredInventory {

    List<IInventory> getInventories();

    @Override
    default int getSizeInventory() {
        int total = 0;
        for (IInventory inv : getInventories()) {
            if (inv != null) total += inv.getSizeInventory();
        }
        return total;
    }

    @Override
    default ItemStack getStackInSlot(int index) {
        int[] r = resolveSlot(index);
        if (r == null) return null;
        IInventory inv = getInventories().get(r[0]);
        return inv != null ? inv.getStackInSlot(r[1]) : null;
    }

    @Override
    default ItemStack decrStackSize(int index, int count) {
        int[] r = resolveSlot(index);
        if (r == null) return null;
        IInventory inv = getInventories().get(r[0]);
        return inv != null ? inv.decrStackSize(r[1], count) : null;
    }

    @Override
    default ItemStack getStackInSlotOnClosing(int index) {
        int[] r = resolveSlot(index);
        if (r == null) return null;
        IInventory inv = getInventories().get(r[0]);
        return inv != null ? inv.getStackInSlotOnClosing(r[1]) : null;
    }

    @Override
    default void setInventorySlotContents(int index, ItemStack stack) {
        int[] r = resolveSlot(index);
        if (r == null) return;
        IInventory inv = getInventories().get(r[0]);
        // Respect the filter — null clears the slot, so always allow that
        if (inv != null && (stack == null || passesFilter(r[0], stack))) {
            inv.setInventorySlotContents(r[1], stack);
        }
    }

    @Override
    default boolean isItemValidForSlot(int index, ItemStack stack) {
        int[] r = resolveSlot(index);
        if (r == null) return false;
        IInventory inv = getInventories().get(r[0]);
        if (inv == null) return false;
        // Must pass this inventory's filter AND the sub-inventory's own check
        return passesFilter(r[0], stack) && inv.isItemValidForSlot(r[1], stack);
    }

    /** Returns total free space across all inventories that accept 'stack'. */
    default int getFreeSpaceFor(ItemStack stack) {
        int totalSpace = 0;
        int invIndex = 0;
        for (IInventory chest : getInventories()) {
            if (chest != null && passesFilter(invIndex, stack)) {
                for (int i = 0; i < chest.getSizeInventory(); i++) {
                    ItemStack slotStack = chest.getStackInSlot(i);
                    if (slotStack == null) {
                        totalSpace += stack.getMaxStackSize();
                    } else if (canStacksMerge(slotStack, stack)) {
                        totalSpace += (slotStack.getMaxStackSize() - slotStack.stackSize);
                    }
                }
            }
            invIndex++;
        }
        return totalSpace;
    }

    /**
     * Distributes 'amount' units of 'stack' into all inventories that accept it.
     * Returns true if at least one item was inserted.
     */
    default boolean addToInventory(ItemStack stack, int amount) {
        if (amount <= 0) return false;

        int remaining = amount;
        int invIndex = 0;

        for (IInventory chest : getInventories()) {
            if (chest == null || !IFilteredInventory.super.passesFilter(invIndex, stack)) {
                invIndex++;
                continue;
            }

            for (int i = 0; i < chest.getSizeInventory(); i++) {
                ItemStack slotStack = chest.getStackInSlot(i);

                if (slotStack == null) {
                    int toAdd = Math.min(remaining, stack.getMaxStackSize());
                    ItemStack newStack = stack.copy();
                    newStack.stackSize = toAdd;
                    chest.setInventorySlotContents(i, newStack);
                    remaining -= toAdd;
                } else if (canStacksMerge(slotStack, stack)) {
                    int space = slotStack.getMaxStackSize() - slotStack.stackSize;
                    int toAdd = Math.min(remaining, space);
                    slotStack.stackSize += toAdd;
                    remaining -= toAdd;
                }

                if (remaining <= 0) {
                    chest.markDirty();
                    return true;
                }
            }
            chest.markDirty();
            invIndex++;
        }

        return remaining < amount;
    }

    @Override
    default boolean hasCustomInventoryName() {
        return false;
    }

    @Override
    default int getInventoryStackLimit() {
        return 64;
    }

    @Override
    default void markDirty() {
        for (IInventory inv : getInventories()) {
            if (inv != null) inv.markDirty();
        }
    }

    @Override
    default boolean isUseableByPlayer(EntityPlayer player) {
        return true;
    }

    @Override
    default void openInventory() {
        for (IInventory inv : getInventories()) {
            if (inv != null) inv.openInventory();
        }
    }

    @Override
    default void closeInventory() {
        for (IInventory inv : getInventories()) {
            if (inv != null) inv.closeInventory();
        }
    }

    private boolean canStacksMerge(ItemStack stack1, ItemStack stack2) {
        return stack1.getItem() == stack2.getItem()
            && (!stack1.getHasSubtypes() || stack1.getItemDamage() == stack2.getItemDamage())
            && ItemStack.areItemStackTagsEqual(stack1, stack2);
    }

    /** Returns {inventoryIndex, localSlot}, or null if index is out of range. */
    private int[] resolveSlot(int index) {
        int slot = index;
        int invIndex = 0;
        for (IInventory inv : getInventories()) {
            if (inv == null) {
                invIndex++;
                continue;
            }
            if (slot < inv.getSizeInventory()) return new int[] { invIndex, slot };
            slot -= inv.getSizeInventory();
            invIndex++;
        }
        return null;
    }
}
