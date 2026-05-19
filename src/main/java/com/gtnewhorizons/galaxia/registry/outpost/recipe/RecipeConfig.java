package com.gtnewhorizons.galaxia.registry.outpost.recipe;

public record RecipeConfig(SavedRecipeList savedRecipes, RecipeSchedulerMode mode, NotDoablePolicy notDoablePolicy,
    byte orderCursor, byte orderRemaining) {

    public RecipeConfig {
        if (orderRemaining < 0) {
            throw new IllegalArgumentException("orderRemaining must be >= 0: " + orderRemaining);
        }
    }

    public static RecipeConfig empty() {
        return new RecipeConfig(
            new SavedRecipeList(),
            RecipeSchedulerMode.PRIORITY,
            NotDoablePolicy.SKIP,
            (byte) 0,
            (byte) 0);
    }
}
