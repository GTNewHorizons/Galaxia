package com.gtnewhorizons.galaxia.registry.outpost;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import com.gtnewhorizons.galaxia.registry.interfaces.IDistributedInventory.FluidKey;

/**
 * Virtual item inventory for an automated outpost.
 * All amounts are stored in RAM; persisted to JSON on WorldEvent.Save.
 *
 * <p>
 * This class is NOT thread-safe and must only be accessed from the server thread.
 */
public final class AutomatedFacilityInventory implements IInventory {

    private final Map<ItemStackWrapper, Long> amounts = new LinkedHashMap<>();
    private final Map<FluidKey, Long> fluidAmounts = new LinkedHashMap<>();
    private final Map<ItemStackWrapper, Long> itemLowerBounds = new LinkedHashMap<>();
    private final Map<ItemStackWrapper, Long> itemUpperBounds = new LinkedHashMap<>();
    private final Map<FluidKey, Long> fluidLowerBounds = new LinkedHashMap<>();
    private final Map<FluidKey, Long> fluidUpperBounds = new LinkedHashMap<>();
    private long totalItemAmount;

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
        final long current = getAmount(item);
        final long actual = Math.clamp(delta, -current, getSizeInventory() - current);
        final long value = current + actual;
        if (value == 0) amounts.remove(item);
        else amounts.put(item, value);
        totalItemAmount += actual;

        return actual;
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
        totalItemAmount -= amount;
        return true;
    }

    public @Nonnull Map<ItemStackWrapper, Long> snapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(amounts));
    }

    public long totalItems() {
        return totalItemAmount;
    }

    public boolean keepsItemLowerBoundAfterConsume(ItemStackWrapper item, long consumed, long lowerBound) {
        return getAmount(item) - Math.max(0L, consumed) >= lowerBound;
    }

    public boolean isItemBelowUpperBound(ItemStackWrapper item, long upperBound) {
        return getAmount(item) < upperBound;
    }

    public boolean hasItemLowerBound(ItemStackWrapper item) {
        return itemLowerBounds.containsKey(item);
    }

    public boolean hasItemUpperBound(ItemStackWrapper item) {
        return itemUpperBounds.containsKey(item);
    }

    public long itemLowerBoundOrDefault(ItemStackWrapper item) {
        return itemLowerBounds.getOrDefault(item, 0L);
    }

    public long itemUpperBoundOrDefault(ItemStackWrapper item) {
        return itemUpperBounds.getOrDefault(item, Long.MAX_VALUE);
    }

    public void setItemLowerBound(ItemStackWrapper item, long amount) {
        setBound(itemLowerBounds, item, amount);
    }

    public void setItemUpperBound(ItemStackWrapper item, long amount) {
        setBound(itemUpperBounds, item, amount);
    }

    public void clearItemLowerBound(ItemStackWrapper item) {
        itemLowerBounds.remove(item);
    }

    public void clearItemUpperBound(ItemStackWrapper item) {
        itemUpperBounds.remove(item);
    }

    // ── Fluid amounts (FluidKey-based, canonical) ──

    public long getFluidAmount(FluidKey fluid) {
        if (fluid == null) return 0L;
        Long v = fluidAmounts.get(fluid);
        return v == null ? 0L : v;
    }

    public long addFluid(FluidKey fluid, long delta) {
        if (fluid == null) return 0L;
        long current = getFluidAmount(fluid);
        if (delta < 0) {
            long actual = Math.max(delta, -current);
            long newValue = current + actual;
            if (newValue == 0) fluidAmounts.remove(fluid);
            else fluidAmounts.put(fluid, newValue);
            return actual;
        }
        fluidAmounts.put(fluid, current + delta);
        return delta;
    }

    public @Nonnull Map<FluidKey, Long> fluidSnapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(fluidAmounts));
    }

    public boolean keepsFluidLowerBoundAfterConsume(FluidKey fluid, long consumed, long lowerBound) {
        return getFluidAmount(fluid) - Math.max(0L, consumed) >= lowerBound;
    }

    public boolean isFluidBelowUpperBound(FluidKey fluid, long upperBound) {
        return getFluidAmount(fluid) < upperBound;
    }

    public boolean hasFluidLowerBound(FluidKey fluid) {
        return fluidLowerBounds.containsKey(fluid);
    }

    public boolean hasFluidUpperBound(FluidKey fluid) {
        return fluidUpperBounds.containsKey(fluid);
    }

    public long fluidLowerBoundOrDefault(FluidKey fluid) {
        return fluidLowerBounds.getOrDefault(fluid, 0L);
    }

    public long fluidUpperBoundOrDefault(FluidKey fluid) {
        return fluidUpperBounds.getOrDefault(fluid, Long.MAX_VALUE);
    }

    public void setFluidLowerBound(FluidKey fluid, long amount) {
        setFluidBound(fluidLowerBounds, fluid, amount);
    }

    public void setFluidUpperBound(FluidKey fluid, long amount) {
        setFluidBound(fluidUpperBounds, fluid, amount);
    }

    public void clearFluidLowerBound(FluidKey fluid) {
        fluidLowerBounds.remove(fluid);
    }

    public void clearFluidUpperBound(FluidKey fluid) {
        fluidUpperBounds.remove(fluid);
    }

    public @Nonnull Map<FluidKey, Long> fluidLowerBoundsSnapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(fluidLowerBounds));
    }

    public @Nonnull Map<FluidKey, Long> fluidUpperBoundsSnapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(fluidUpperBounds));
    }

    // ── Fluid methods (String-keyed, deprecated) ──

    @Deprecated
    public long getFluidAmount(String fluidName) {
        FluidKey key = FluidKey.fromName(fluidName);
        return key != null ? getFluidAmount(key) : 0L;
    }

    @Deprecated
    public long addFluid(String fluidName, long delta) {
        FluidKey key = FluidKey.fromName(fluidName);
        return key != null ? addFluid(key, delta) : 0L;
    }

    @Deprecated
    public @Nonnull Map<String, Long> fluidSnapshotByName() {
        Map<String, Long> result = new LinkedHashMap<>();
        for (Map.Entry<FluidKey, Long> e : fluidAmounts.entrySet()) {
            result.put(
                e.getKey()
                    .fluid()
                    .getName(),
                e.getValue());
        }
        return Collections.unmodifiableMap(result);
    }

    @Deprecated
    public boolean keepsFluidLowerBoundAfterConsume(String fluidName, long consumed, long lowerBound) {
        FluidKey key = FluidKey.fromName(fluidName);
        return key != null && keepsFluidLowerBoundAfterConsume(key, consumed, lowerBound);
    }

    @Deprecated
    public boolean isFluidBelowUpperBound(String fluidName, long upperBound) {
        FluidKey key = FluidKey.fromName(fluidName);
        return key != null && isFluidBelowUpperBound(key, upperBound);
    }

    @Deprecated
    public boolean hasFluidLowerBound(String fluidName) {
        FluidKey key = FluidKey.fromName(fluidName);
        return key != null && hasFluidLowerBound(key);
    }

    @Deprecated
    public boolean hasFluidUpperBound(String fluidName) {
        FluidKey key = FluidKey.fromName(fluidName);
        return key != null && hasFluidUpperBound(key);
    }

    @Deprecated
    public long fluidLowerBoundOrDefault(String fluidName) {
        FluidKey key = FluidKey.fromName(fluidName);
        return key != null ? fluidLowerBoundOrDefault(key) : 0L;
    }

    @Deprecated
    public long fluidUpperBoundOrDefault(String fluidName) {
        FluidKey key = FluidKey.fromName(fluidName);
        return key != null ? fluidUpperBoundOrDefault(key) : Long.MAX_VALUE;
    }

    @Deprecated
    public void setFluidLowerBound(String fluidName, long amount) {
        FluidKey key = FluidKey.fromName(fluidName);
        if (key != null) setFluidLowerBound(key, amount);
    }

    @Deprecated
    public void setFluidUpperBound(String fluidName, long amount) {
        FluidKey key = FluidKey.fromName(fluidName);
        if (key != null) setFluidUpperBound(key, amount);
    }

    @Deprecated
    public void clearFluidLowerBound(String fluidName) {
        FluidKey key = FluidKey.fromName(fluidName);
        if (key != null) clearFluidLowerBound(key);
    }

    @Deprecated
    public void clearFluidUpperBound(String fluidName) {
        FluidKey key = FluidKey.fromName(fluidName);
        if (key != null) clearFluidUpperBound(key);
    }

    @Deprecated
    public @Nonnull Map<String, Long> fluidBoundsLowerByName() {
        Map<String, Long> result = new LinkedHashMap<>();
        for (Map.Entry<FluidKey, Long> e : fluidLowerBounds.entrySet()) {
            result.put(
                e.getKey()
                    .fluid()
                    .getName(),
                e.getValue());
        }
        return Collections.unmodifiableMap(result);
    }

    @Deprecated
    public @Nonnull Map<String, Long> fluidBoundsUpperByName() {
        Map<String, Long> result = new LinkedHashMap<>();
        for (Map.Entry<FluidKey, Long> e : fluidUpperBounds.entrySet()) {
            result.put(
                e.getKey()
                    .fluid()
                    .getName(),
                e.getValue());
        }
        return Collections.unmodifiableMap(result);
    }

    // ── Unified bound dispatch (uses canonical FluidKey methods) ──

    public void setBound(BoundKind kind, String resourceKey, long amount) {
        if (kind == null || resourceKey == null || resourceKey.isEmpty()) return;
        switch (kind) {
            case ITEM_LOWER -> setItemLowerBound(ItemStackWrapper.fromKey(resourceKey), amount);
            case ITEM_UPPER -> setItemUpperBound(ItemStackWrapper.fromKey(resourceKey), amount);
            case FLUID_LOWER -> setFluidLowerBound(FluidKey.fromName(resourceKey), amount);
            case FLUID_UPPER -> setFluidUpperBound(FluidKey.fromName(resourceKey), amount);
        }
    }

    public void clearBound(BoundKind kind, String resourceKey) {
        if (kind == null || resourceKey == null || resourceKey.isEmpty()) return;
        switch (kind) {
            case ITEM_LOWER -> clearItemLowerBound(ItemStackWrapper.fromKey(resourceKey));
            case ITEM_UPPER -> clearItemUpperBound(ItemStackWrapper.fromKey(resourceKey));
            case FLUID_LOWER -> clearFluidLowerBound(FluidKey.fromName(resourceKey));
            case FLUID_UPPER -> clearFluidUpperBound(FluidKey.fromName(resourceKey));
        }
    }

    public @Nonnull Map<ItemStackWrapper, Long> itemLowerBoundsSnapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(itemLowerBounds));
    }

    public @Nonnull Map<ItemStackWrapper, Long> itemUpperBoundsSnapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(itemUpperBounds));
    }

    /** Replaces the entire inventory contents (used during deserialization and migration). */
    public void loadFromSnapshot(@Nonnull Map<ItemStackWrapper, Long> snapshot) {
        amounts.clear();
        totalItemAmount = 0L;
        for (Map.Entry<ItemStackWrapper, Long> e : snapshot.entrySet()) {
            if (e.getValue() > 0) {
                amounts.put(e.getKey(), e.getValue());
                totalItemAmount += e.getValue();
            }
        }
    }

    public void loadFluidSnapshot(@Nonnull Map<String, Long> snapshot) {
        fluidAmounts.clear();
        for (Map.Entry<String, Long> e : snapshot.entrySet()) {
            if (e.getKey() == null || e.getKey()
                .isEmpty() || e.getValue() <= 0) continue;
            FluidKey key = FluidKey.fromName(e.getKey());
            if (key != null) fluidAmounts.put(key, e.getValue());
        }
    }

    public void setAmount(ItemStackWrapper item, long amount) {
        long current = getAmount(item);
        if (amount <= 0) amounts.remove(item);
        else amounts.put(item, amount);

        totalItemAmount += amount > 0 ? amount - current : -current;
    }

    public void loadItemLowerBounds(@Nonnull Map<ItemStackWrapper, Long> snapshot) {
        loadBounds(itemLowerBounds, snapshot);
    }

    public void loadItemUpperBounds(@Nonnull Map<ItemStackWrapper, Long> snapshot) {
        loadBounds(itemUpperBounds, snapshot);
    }

    public void loadFluidLowerBounds(@Nonnull Map<String, Long> snapshot) {
        loadFluidBounds(fluidLowerBounds, snapshot);
    }

    public void loadFluidUpperBounds(@Nonnull Map<String, Long> snapshot) {
        loadFluidBounds(fluidUpperBounds, snapshot);
    }

    /** Returns {@code true} if the inventory contains no resources. */
    public boolean isEmpty() {
        return totalItemAmount == 0L && fluidAmounts.isEmpty();
    }

    public void clear() {
        amounts.clear();
        fluidAmounts.clear();
        itemLowerBounds.clear();
        itemUpperBounds.clear();
        fluidLowerBounds.clear();
        fluidUpperBounds.clear();
        totalItemAmount = 0L;
    }

    private static void setBound(Map<ItemStackWrapper, Long> bounds, ItemStackWrapper item, long amount) {
        if (item == null) return;
        if (amount < 0L) throw new IllegalArgumentException("bound amount must be >= 0: " + amount);
        bounds.put(item, amount);
    }

    private static void setFluidBound(Map<FluidKey, Long> bounds, @Nullable FluidKey fluid, long amount) {
        if (fluid == null) return;
        if (amount < 0L) throw new IllegalArgumentException("bound amount must be >= 0: " + amount);
        bounds.put(fluid, amount);
    }

    private static void loadBounds(Map<ItemStackWrapper, Long> bounds, Map<ItemStackWrapper, Long> snapshot) {
        bounds.clear();
        for (Map.Entry<ItemStackWrapper, Long> e : snapshot.entrySet()) {
            if (e.getKey() != null && e.getValue() >= 0L) bounds.put(e.getKey(), e.getValue());
        }
    }

    private static void loadFluidBounds(Map<FluidKey, Long> bounds, Map<String, Long> snapshot) {
        bounds.clear();
        for (Map.Entry<String, Long> e : snapshot.entrySet()) {
            if (e.getKey() == null || e.getKey()
                .isEmpty() || e.getValue() < 0L) continue;
            FluidKey key = FluidKey.fromName(e.getKey());
            if (key != null) bounds.put(key, e.getValue());
        }
    }

    public enum BoundKind {
        ITEM_LOWER,
        ITEM_UPPER,
        FLUID_LOWER,
        FLUID_UPPER
    }

    @Override
    public int getSizeInventory() {
        return Integer.MAX_VALUE;
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
