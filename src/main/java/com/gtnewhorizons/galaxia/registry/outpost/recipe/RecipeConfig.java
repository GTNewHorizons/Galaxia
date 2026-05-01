package com.gtnewhorizons.galaxia.registry.outpost.recipe;

import java.util.Objects;

public record RecipeConfig(RecipeSlotList slots, RecipeSchedulerMode mode, NotDoablePolicy notDoablePolicy,
    byte orderCursor, byte orderRemaining) {

    public RecipeConfig {
        Objects.requireNonNull(slots, "slots must not be null");
        Objects.requireNonNull(mode, "mode must not be null");
        Objects.requireNonNull(notDoablePolicy, "notDoablePolicy must not be null");
        if (orderRemaining < 0) {
            throw new IllegalArgumentException("orderRemaining must be >= 0: " + orderRemaining);
        }
    }

    public static RecipeConfig empty() {
        return new RecipeConfig(
            new RecipeSlotList(),
            RecipeSchedulerMode.PRIORITY,
            NotDoablePolicy.SKIP,
            (byte) 0,
            (byte) 0);
    }
}
