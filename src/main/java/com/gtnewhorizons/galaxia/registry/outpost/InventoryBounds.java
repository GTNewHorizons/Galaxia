package com.gtnewhorizons.galaxia.registry.outpost;

import java.util.Objects;

public final class InventoryBounds {

    private static final long NO_BOUNDS = -1;

    public static InventoryBounds invalid() {
        return new InventoryBounds(NO_BOUNDS, NO_BOUNDS);
    }

    private final long low;
    private final long up;

    public InventoryBounds(long low, long up) {
        if (low != NO_BOUNDS && up != NO_BOUNDS && low > up) {
            throw new IllegalStateException("Can't have a low bound higher than the high one");
        }
        this.low = low;
        this.up = up;
    }

    public static InventoryBounds lowBound(long low) {
        return new InventoryBounds(low, NO_BOUNDS);
    }

    public static InventoryBounds upperBound(long high) {
        return new InventoryBounds(NO_BOUNDS, high);
    }

    public boolean inBounds(long amount) {
        return lowOrDefault() <= amount && amount <= upperOrDefault();
    }

    public long upper() {
        return up;
    }

    public long upperOrDefault() {
        return up != NO_BOUNDS ? up : Long.MAX_VALUE;
    }

    public long low() {
        return low;
    }

    public long lowOrDefault() {
        return low != NO_BOUNDS ? low : 0;
    }

    public boolean hasLow() {
        return low != NO_BOUNDS;
    }

    public boolean hasUpper() {
        return up != NO_BOUNDS;
    }

    public boolean isInvalid() {
        return low == NO_BOUNDS && up == NO_BOUNDS;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (InventoryBounds) obj;
        return this.low == that.low && this.up == that.up;
    }

    @Override
    public int hashCode() {
        return Objects.hash(low, up);
    }
}
