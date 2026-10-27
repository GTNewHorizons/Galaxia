package com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.utils.item.IItemHandlerModifiable;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartInstance;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.StoragePartDef;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.TileEntitySilo;

/**
 * Exposes every cargo hold of the rocket in a silo as one flat inventory. The size is fixed so client and server
 * always agree on the slot count, slots past the real capacity simply refuse items
 */
public class CargoItemHandler implements IItemHandlerModifiable {

    public static final int MAX_SLOTS = 54;

    private final TileEntitySilo silo;

    public CargoItemHandler(TileEntitySilo silo) {
        this.silo = silo;
    }

    /** Number of slots the rocket really has */
    public int getUsableSlots() {
        int total = 0;
        for (RocketPartInstance part : silo.getBuiltBlueprint()
            .getParts()) {
            if (part.def() instanceof StoragePartDef storage) total += storage.slots();
        }
        return Math.min(total, MAX_SLOTS);
    }

    /** A slot inside one hold's live array */
    private record HoldSlot(ItemStack[] stacks, int index) {}

    /**
     * Finds the hold array and index behind a flat slot
     *
     * @return The slot, or null past the capacity
     */
    private HoldSlot locate(int slot) {
        if (slot < 0 || slot >= MAX_SLOTS) return null;
        int remaining = slot;
        List<RocketPartInstance> holds = new ArrayList<>();
        for (RocketPartInstance part : silo.getBuiltBlueprint()
            .getParts()) {
            if (part.def() instanceof StoragePartDef) holds.add(part);
        }
        for (RocketPartInstance hold : holds) {
            ItemStack[] stacks = silo.getLoadout()
                .getCargo(hold);
            if (remaining < stacks.length) return new HoldSlot(stacks, remaining);
            remaining -= stacks.length;
        }
        return null;
    }

    @Override
    public int getSlots() {
        return MAX_SLOTS;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        HoldSlot location = locate(slot);
        return location == null ? null : location.stacks()[location.index()];
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        HoldSlot location = locate(slot);
        if (location == null) return;
        location.stacks()[location.index()] = stack == null || stack.stackSize <= 0 ? null : stack;
        silo.markDirty();
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (stack == null || locate(slot) == null) return stack;
        ItemStack existing = getStackInSlot(slot);
        int limit = Math.min(getSlotLimit(slot), stack.getMaxStackSize());
        if (existing != null && (!existing.isItemEqual(stack) || !ItemStack.areItemStackTagsEqual(existing, stack)))
            return stack;
        int present = existing == null ? 0 : existing.stackSize;
        int move = Math.min(limit - present, stack.stackSize);
        if (move <= 0) return stack;
        if (!simulate) {
            ItemStack placed = stack.copy();
            placed.stackSize = present + move;
            setStackInSlot(slot, placed);
        }
        if (move == stack.stackSize) return null;
        ItemStack rest = stack.copy();
        rest.stackSize -= move;
        return rest;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        ItemStack existing = getStackInSlot(slot);
        if (existing == null || amount <= 0) return null;
        int move = Math.min(amount, existing.stackSize);
        ItemStack taken = existing.copy();
        taken.stackSize = move;
        if (!simulate) {
            ItemStack rest = existing.copy();
            rest.stackSize -= move;
            setStackInSlot(slot, rest);
        }
        return taken;
    }

    @Override
    public int getSlotLimit(int slot) {
        return 64;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return locate(slot) != null;
    }
}
