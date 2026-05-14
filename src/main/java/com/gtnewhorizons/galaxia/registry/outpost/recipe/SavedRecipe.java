package com.gtnewhorizons.galaxia.registry.outpost.recipe;

public record SavedRecipe(RecipeSnapshot recipe, boolean enabled, long requestAmount, byte priority, byte orderSize,
    String displayName) {

    public SavedRecipe {
        if (recipe == null) throw new NullPointerException("recipe must not be null");
        if (requestAmount < 0L) throw new IllegalArgumentException("requestAmount must be >= 0: " + requestAmount);
        if (priority < 0) throw new IllegalArgumentException("priority must be >= 0: " + priority);
        if (orderSize < 1) throw new IllegalArgumentException("orderSize must be >= 1: " + orderSize);
        displayName = displayName == null ? "" : displayName.trim();
    }

    public SavedRecipe(RecipeSnapshot recipe, boolean enabled, long requestAmount, byte priority, byte orderSize) {
        this(recipe, enabled, requestAmount, priority, orderSize, "");
    }

    public SavedRecipe withDisplayName(String displayName) {
        return new SavedRecipe(recipe, enabled, requestAmount, priority, orderSize, displayName);
    }
}
