package com.gtnewhorizons.galaxia.registry.outpost.recipe;

public record RecipeScheduleState(byte orderCursor, byte orderRemaining) {

    public static final RecipeScheduleState RESET = new RecipeScheduleState((byte) 0, (byte) 0);

    public RecipeScheduleState {
        if (orderCursor < 0 || orderCursor >= RecipeBook.MAX_RECIPES) {
            throw new IllegalArgumentException("orderCursor must be in [0, " + RecipeBook.MAX_RECIPES + ")");
        }
        if (orderRemaining < 0) {
            throw new IllegalArgumentException("orderRemaining must be >= 0: " + orderRemaining);
        }
    }
}
