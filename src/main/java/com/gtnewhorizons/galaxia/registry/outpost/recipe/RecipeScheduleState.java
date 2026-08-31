package com.gtnewhorizons.galaxia.registry.outpost.recipe;

public record RecipeScheduleState(byte orderCursor, byte orderRemaining) {

    public RecipeScheduleState {
        if (orderCursor < 0) {
            throw new IllegalArgumentException("orderCursor must be >= 0: " + orderCursor);
        }
        if (orderRemaining < 0) {
            throw new IllegalArgumentException("orderRemaining must be >= 0: " + orderRemaining);
        }
    }
}
