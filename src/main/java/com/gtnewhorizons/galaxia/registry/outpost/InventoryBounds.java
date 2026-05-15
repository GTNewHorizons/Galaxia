package com.gtnewhorizons.galaxia.registry.outpost;

public record InventoryBounds(long low, long high) {

    public InventoryBounds {
        if (low > high) {
            throw new IllegalStateException("Can't have a low bound higher than the high one");
        }
    }

    public boolean inBounds(long amount) {
        return low <= amount && amount <= high;
    }
}
