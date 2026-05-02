package com.gtnewhorizons.galaxia.registry.outpost.recipe;

public record RecipeSlot(RecipeSnapshot recipe, boolean enabled, int inputGuard, int outputGuard, byte priority,
    byte orderSize) {

    public RecipeSlot {
        if (recipe == null) throw new NullPointerException("recipe must not be null");
        if (inputGuard < 0) throw new IllegalArgumentException("inputGuard must be >= 0: " + inputGuard);
        if (outputGuard < 0) throw new IllegalArgumentException("outputGuard must be >= 0: " + outputGuard);
        if (priority < 0) throw new IllegalArgumentException("priority must be >= 0: " + priority);
        if (orderSize < 1) throw new IllegalArgumentException("orderSize must be >= 1: " + orderSize);
    }
}
