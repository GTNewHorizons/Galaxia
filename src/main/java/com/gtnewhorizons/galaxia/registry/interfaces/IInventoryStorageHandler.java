package com.gtnewhorizons.galaxia.registry.interfaces;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

public interface IInventoryStorageHandler<T> extends IItemStorageHandler<T> {

    List<IInventory> getInventories(T attachment);

    @Override
    default long getItemSlots(T attachment) {
        long totalSlots = 0;
        for (IInventory inv : getInventories(attachment)) {
            totalSlots += inv.getSizeInventory();
        }
        return totalSlots;
    }

    @Override
    default long getItemStored(T attachment) {
        long totalStored = 0;
        for (IInventory inv : getInventories(attachment)) {
            totalStored += getInventoryStoredCount(inv);
        }
        return totalStored;
    }

    @Override
    default long getItemCapacity(T attachment) {
        long totalCapacity = 0;
        for (IInventory inv : getInventories(attachment)) {
            totalCapacity += (long) inv.getSizeInventory() * inv.getInventoryStackLimit();
        }
        return totalCapacity;
    }

    @Override
    default List<ItemStack> getAllItems(T attachment) {
        List<ItemStack> allItems = new ArrayList<>();
        for (IInventory inv : getInventories(attachment)) {
            addInventoryItemsToList(inv, allItems);
        }
        return allItems;
    }

    @Override
    default long insertItem(T attachment, ItemStack resource, boolean doDrain) {
        if (resource == null || resource.stackSize <= 0) {
            return 0;
        }

        int remaining = resource.stackSize;
        for (IInventory inv : getInventories(attachment)) {
            remaining = insertIntoInventory(inv, resource, remaining, doDrain);
            if (remaining <= 0) {
                return resource.stackSize;
            }
        }

        return resource.stackSize - remaining;
    }

    @Override
    default ItemStack extractItem(T attachment, ItemStack resource, boolean doDrain) {
        if (resource == null || resource.stackSize <= 0) {
            return null;
        }

        ItemStack extracted = null;
        int toExtract = resource.stackSize;

        for (IInventory inv : getInventories(attachment)) {
            int taken = extractFromInventory(inv, resource, toExtract, doDrain);
            if (taken <= 0) {
                continue;
            }

            if (extracted == null) {
                extracted = resource.copy();
                extracted.stackSize = 0;
            }

            extracted.stackSize += taken;
            toExtract -= taken;

            if (toExtract <= 0) {
                break;
            }
        }

        return extracted;
    }

    private long getInventoryStoredCount(IInventory inv) {
        long stored = 0;
        int slots = inv.getSizeInventory();
        for (int slot = 0; slot < slots; slot++) {
            ItemStack stack = inv.getStackInSlot(slot);
            if (stack != null) {
                stored += stack.stackSize;
            }
        }
        return stored;
    }

    private void addInventoryItemsToList(IInventory inv, List<ItemStack> list) {
        int slots = inv.getSizeInventory();
        for (int slot = 0; slot < slots; slot++) {
            ItemStack stack = inv.getStackInSlot(slot);
            if (stack != null) {
                list.add(stack.copy());
            }
        }
    }

    private int insertIntoInventory(IInventory inv, ItemStack resource, int remaining, boolean doDrain) {
        int slots = inv.getSizeInventory();
        for (int slot = 0; slot < slots; slot++) {
            if (!inv.isItemValidForSlot(slot, resource)) {
                continue;
            }

            ItemStack stackInSlot = inv.getStackInSlot(slot);
            int maxStackSize = Math.min(resource.getMaxStackSize(), inv.getInventoryStackLimit());

            if (stackInSlot == null) {
                int transfer = Math.min(remaining, maxStackSize);
                if (doDrain) {
                    ItemStack copy = resource.copy();
                    copy.stackSize = transfer;
                    inv.setInventorySlotContents(slot, copy);
                }
                remaining -= transfer;
            }

            else if (ItemStack.areItemStackTagsEqual(stackInSlot, resource)
                && stackInSlot.getItem() == resource.getItem()
                && stackInSlot.getItemDamage() == resource.getItemDamage()) {
                    int maxInsert = maxStackSize - stackInSlot.stackSize;
                    if (maxInsert <= 0) {
                        continue;
                    }

                    int transfer = Math.min(remaining, maxInsert);
                    if (doDrain) {
                        stackInSlot.stackSize += transfer;
                        inv.markDirty();
                    }
                    remaining -= transfer;
                }

            if (remaining <= 0) {
                break;
            }
        }
        return remaining;
    }

    private int extractFromInventory(IInventory inv, ItemStack resource, int toExtract, boolean doDrain) {
        int totalTaken = 0;
        int slots = inv.getSizeInventory();

        for (int slot = 0; slot < slots; slot++) {
            ItemStack stackInSlot = inv.getStackInSlot(slot);
            if (stackInSlot == null) {
                continue;
            }

            boolean matches = stackInSlot.getItem() == resource.getItem()
                && stackInSlot.getItemDamage() == resource.getItemDamage()
                && ItemStack.areItemStackTagsEqual(stackInSlot, resource);

            if (!matches) {
                continue;
            }

            int available = stackInSlot.stackSize;
            int chunk = Math.min(toExtract, available);

            if (doDrain) {
                inv.decrStackSize(slot, chunk);
            }

            totalTaken += chunk;
            toExtract -= chunk;

            if (toExtract <= 0) {
                break;
            }
        }
        return totalTaken;
    }
}
