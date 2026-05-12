package com.gtnewhorizons.galaxia.registry.outpost;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.annotation.Nonnull;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

/**
 * Virtual item inventory for an automated outpost.
 * All amounts are stored in RAM; persisted to JSON on WorldEvent.Save.
 *
 * <p>
 * This class is NOT thread-safe and must only be accessed from the server thread.
 */
public final class AutomatedFacilityInventory implements IInventory {

    private final Map<ItemStackWrapper, Long> amounts = new LinkedHashMap<>();
    private final Map<String, Long> fluidAmounts = new LinkedHashMap<>();

    public long getAmount(ItemStackWrapper item) {
        Long v = amounts.get(item);
        return v == null ? 0L : v;
    }

    /**
     * Adds {@code delta} units. Deposits are silently rejected when the item
     * does not pass the current filter; withdrawals (negative delta) are always allowed.
     *
     * @return the actual amount added (positive) or removed (negative)
     */
    public long add(ItemStackWrapper item, long delta) {
        if (delta > 0) return 0L;

        long current = getAmount(item);
        if (delta < 0) {
            long actual = Math.max(delta, -current);
            long newValue = current + actual;
            if (newValue == 0) amounts.remove(item);
            else amounts.put(item, newValue);
            return actual;
        }
        amounts.put(item, current + delta);
        return delta;
    }

    /**
     * Attempts to remove exactly {@code amount} units. Returns {@code true} only if
     * the buffer holds at least that many, in which case they are consumed.
     * Withdrawals are never blocked by the filter.
     */
    public boolean tryConsume(ItemStackWrapper item, long amount) {
        if (amount <= 0) return true;
        long current = getAmount(item);
        if (current < amount) return false;
        long newValue = current - amount;
        if (newValue == 0) amounts.remove(item);
        else amounts.put(item, newValue);
        return true;
    }

    public @Nonnull Map<ItemStackWrapper, Long> snapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(amounts));
    }

    public long getFluidAmount(String fluidName) {
        if (fluidName == null) return 0L;
        Long v = fluidAmounts.get(fluidName);
        return v == null ? 0L : v;
    }

    public long addFluid(String fluidName, long delta) {
        if (fluidName == null || fluidName.isEmpty()) return 0L;
        long current = getFluidAmount(fluidName);
        if (delta < 0) {
            long actual = Math.max(delta, -current);
            long newValue = current + actual;
            if (newValue == 0) fluidAmounts.remove(fluidName);
            else fluidAmounts.put(fluidName, newValue);
            return actual;
        }
        fluidAmounts.put(fluidName, current + delta);
        return delta;
    }

    public @Nonnull Map<String, Long> fluidSnapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(fluidAmounts));
    }

    public void loadFromSnapshot(@Nonnull Map<ItemStackWrapper, Long> snapshot) {
        amounts.clear();
        for (Map.Entry<ItemStackWrapper, Long> e : snapshot.entrySet()) {
            if (e.getValue() > 0) amounts.put(e.getKey(), e.getValue());
        }
    }

    public void loadFluidSnapshot(@Nonnull Map<String, Long> snapshot) {
        fluidAmounts.clear();
        for (Map.Entry<String, Long> e : snapshot.entrySet()) {
            if (e.getKey() != null && !e.getKey()
                .isEmpty() && e.getValue() > 0) {
                fluidAmounts.put(e.getKey(), e.getValue());
            }
        }
    }

    public void setAmount(ItemStackWrapper item, long amount) {
        if (amount <= 0) amounts.remove(item);
        else amounts.put(item, amount);
    }

    public boolean isEmpty() {
        return amounts.isEmpty() && fluidAmounts.isEmpty();
    }

    public void clear() {
        amounts.clear();
        fluidAmounts.clear();
    }

    @Override
    public int getSizeInventory() {
        return 0;
    }

    @Override
    public ItemStack getStackInSlot(int i) {
        return null;
    }

    @Override
    public ItemStack decrStackSize(int i, int c) {
        return null;
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int i) {
        return null;
    }

    @Override
    public void setInventorySlotContents(int i, ItemStack s) {}

    @Override
    public String getInventoryName() {
        return "";
    }

    @Override
    public boolean hasCustomInventoryName() {
        return false;
    }

    @Override
    public int getInventoryStackLimit() {
        return 0;
    }

    @Override
    public void markDirty() {}

    @Override
    public boolean isUseableByPlayer(EntityPlayer p) {
        return false;
    }

    @Override
    public void openInventory() {}

    @Override
    public void closeInventory() {}

    @Override
    public boolean isItemValidForSlot(int index, ItemStack stack) {
        return true;
    }
}
