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
            return actual;
        }
        amounts.put(item, current + delta);
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
        return true;
    }

    /** Returns an unmodifiable snapshot of the full inventory contents. */
    public @Nonnull Map<ItemStackWrapper, Long> snapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(amounts));
    }

    /** Replaces the entire inventory contents (used during deserialization and migration). */
    public void loadFromSnapshot(@Nonnull Map<ItemStackWrapper, Long> snapshot) {
        amounts.clear();
        for (Map.Entry<ItemStackWrapper, Long> e : snapshot.entrySet()) {
            if (e.getValue() > 0) amounts.put(e.getKey(), e.getValue());
        }
    }

    /** Returns {@code true} if the inventory contains no resources. */
    public boolean isEmpty() {
        return amounts.isEmpty();
    }

    /** Sets the exact amount for a resource (used by client-side delta updates). */
    public void setAmount(ItemStackWrapper item, long amount) {
        if (amount <= 0) {
            amounts.remove(item);
        } else {
            amounts.put(item, amount);
        }
    }

    public void clear() {
        amounts.clear();
    }
}
