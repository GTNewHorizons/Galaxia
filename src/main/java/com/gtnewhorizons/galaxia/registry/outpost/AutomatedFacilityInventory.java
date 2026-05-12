package com.gtnewhorizons.galaxia.registry.outpost;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.annotation.Nonnull;

/**
 * Virtual item inventory for an automated outpost.
 * All amounts are stored in RAM; persisted to JSON on WorldEvent.Save.
 *
 * <p>
 * This class is NOT thread-safe and must only be accessed from the server thread.
 */
public final class AutomatedFacilityInventory {

    private final Map<ItemStackWrapper, Long> amounts = new LinkedHashMap<>();
    private final Map<String, Long> fluidAmounts = new LinkedHashMap<>();
    private final Map<ItemStackWrapper, Long> itemLowerBounds = new LinkedHashMap<>();
    private final Map<ItemStackWrapper, Long> itemUpperBounds = new LinkedHashMap<>();
    private final Map<String, Long> fluidLowerBounds = new LinkedHashMap<>();
    private final Map<String, Long> fluidUpperBounds = new LinkedHashMap<>();
    private long totalItemAmount;

    /** Returns the stored amount for the given item, or 0 if absent. */
    public long getAmount(ItemStackWrapper item) {
        Long v = amounts.get(item);
        return v == null ? 0L : v;
    }

    /**
     * Adds {@code delta} to the stored amount. Delta may be negative (withdrawal).
     * The stored value will never go below zero; excess withdrawal is silently clamped.
     *
     * @return the actual amount added (positive) or removed (negative as negative value)
     */
    public long add(ItemStackWrapper item, long delta) {
        long current = getAmount(item);
        if (delta < 0) {
            long actual = Math.max(delta, -current);
            long newValue = current + actual;
            if (newValue == 0) {
                amounts.remove(item);
            } else {
                amounts.put(item, newValue);
            }
            totalItemAmount += actual;
            return actual;
        }
        amounts.put(item, current + delta);
        totalItemAmount += delta;
        return delta;
    }

    /**
     * Attempts to remove exactly {@code amount} units. Returns {@code true} only if
     * the buffer holds at least that many units, in which case they are consumed.
     */
    public boolean tryConsume(ItemStackWrapper item, long amount) {
        if (amount <= 0) return true;
        long current = getAmount(item);
        if (current < amount) return false;
        long newValue = current - amount;
        if (newValue == 0) {
            amounts.remove(item);
        } else {
            amounts.put(item, newValue);
        }
        totalItemAmount -= amount;
        return true;
    }

    /** Returns an unmodifiable snapshot of the full inventory contents. */
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
            if (newValue == 0) {
                fluidAmounts.remove(fluidName);
            } else {
                fluidAmounts.put(fluidName, newValue);
            }
            return actual;
        }
        fluidAmounts.put(fluidName, current + delta);
        return delta;
    }

    public boolean keepsFluidLowerBoundAfterConsume(String fluidName, long consumed, long lowerBound) {
        return getFluidAmount(fluidName) - Math.max(0L, consumed) >= lowerBound;
    }

    public boolean isFluidBelowUpperBound(String fluidName, long upperBound) {
        return getFluidAmount(fluidName) < upperBound;
    }

    public boolean hasFluidLowerBound(String fluidName) {
        return fluidLowerBounds.containsKey(fluidName);
    }

    public boolean hasFluidUpperBound(String fluidName) {
        return fluidUpperBounds.containsKey(fluidName);
    }

    public long fluidLowerBoundOrDefault(String fluidName) {
        return fluidLowerBounds.getOrDefault(fluidName, 0L);
    }

    public long fluidUpperBoundOrDefault(String fluidName) {
        return fluidUpperBounds.getOrDefault(fluidName, Long.MAX_VALUE);
    }

    public void setFluidLowerBound(String fluidName, long amount) {
        setFluidBound(fluidLowerBounds, fluidName, amount);
    }

    public void setFluidUpperBound(String fluidName, long amount) {
        setFluidBound(fluidUpperBounds, fluidName, amount);
    }

    public void clearFluidLowerBound(String fluidName) {
        fluidLowerBounds.remove(fluidName);
    }

    public void clearFluidUpperBound(String fluidName) {
        fluidUpperBounds.remove(fluidName);
    }

    public void setBound(BoundKind kind, String resourceKey, long amount) {
        if (kind == null || resourceKey == null || resourceKey.isEmpty()) return;
        switch (kind) {
            case ITEM_LOWER -> setItemLowerBound(ItemStackWrapper.fromKey(resourceKey), amount);
            case ITEM_UPPER -> setItemUpperBound(ItemStackWrapper.fromKey(resourceKey), amount);
            case FLUID_LOWER -> setFluidLowerBound(resourceKey, amount);
            case FLUID_UPPER -> setFluidUpperBound(resourceKey, amount);
        }
    }

    public void clearBound(BoundKind kind, String resourceKey) {
        if (kind == null || resourceKey == null || resourceKey.isEmpty()) return;
        switch (kind) {
            case ITEM_LOWER -> clearItemLowerBound(ItemStackWrapper.fromKey(resourceKey));
            case ITEM_UPPER -> clearItemUpperBound(ItemStackWrapper.fromKey(resourceKey));
            case FLUID_LOWER -> clearFluidLowerBound(resourceKey);
            case FLUID_UPPER -> clearFluidUpperBound(resourceKey);
        }
    }

    public @Nonnull Map<String, Long> fluidSnapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(fluidAmounts));
    }

    public @Nonnull Map<ItemStackWrapper, Long> itemLowerBoundsSnapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(itemLowerBounds));
    }

    public @Nonnull Map<ItemStackWrapper, Long> itemUpperBoundsSnapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(itemUpperBounds));
    }

    public @Nonnull Map<String, Long> fluidLowerBoundsSnapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(fluidLowerBounds));
    }

    public @Nonnull Map<String, Long> fluidUpperBoundsSnapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(fluidUpperBounds));
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
            if (e.getKey() != null && !e.getKey()
                .isEmpty() && e.getValue() > 0) {
                fluidAmounts.put(e.getKey(), e.getValue());
            }
        }
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

    /** Sets the exact amount for a resource (used by client-side delta updates). */
    public void setAmount(ItemStackWrapper item, long amount) {
        long current = getAmount(item);
        if (amount <= 0) {
            amounts.remove(item);
        } else {
            amounts.put(item, amount);
        }
        totalItemAmount += amount > 0 ? amount - current : -current;
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

    private static void setFluidBound(Map<String, Long> bounds, String fluidName, long amount) {
        if (fluidName == null || fluidName.isEmpty()) return;
        if (amount < 0L) throw new IllegalArgumentException("bound amount must be >= 0: " + amount);
        bounds.put(fluidName, amount);
    }

    private static void loadBounds(Map<ItemStackWrapper, Long> bounds, Map<ItemStackWrapper, Long> snapshot) {
        bounds.clear();
        for (Map.Entry<ItemStackWrapper, Long> e : snapshot.entrySet()) {
            if (e.getKey() != null && e.getValue() >= 0L) bounds.put(e.getKey(), e.getValue());
        }
    }

    private static void loadFluidBounds(Map<String, Long> bounds, Map<String, Long> snapshot) {
        bounds.clear();
        for (Map.Entry<String, Long> e : snapshot.entrySet()) {
            if (e.getKey() != null && !e.getKey()
                .isEmpty() && e.getValue() >= 0L) {
                bounds.put(e.getKey(), e.getValue());
            }
        }
    }

    public enum BoundKind {
        ITEM_LOWER,
        ITEM_UPPER,
        FLUID_LOWER,
        FLUID_UPPER
    }
}
