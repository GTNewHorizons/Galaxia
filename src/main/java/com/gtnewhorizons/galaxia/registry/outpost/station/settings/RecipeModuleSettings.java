package com.gtnewhorizons.galaxia.registry.outpost.station.settings;

import java.util.Objects;

import javax.annotation.Nullable;

import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeConfig;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.SavedRecipe;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.SavedRecipeList;

public final class RecipeModuleSettings implements ModuleSettings {

    private final RecipeConfig config;

    public RecipeModuleSettings(@Nullable RecipeConfig config) {
        this.config = copyOptionalConfig(config);
    }

    @Nullable
    public RecipeConfig config() {
        return copyOptionalConfig(config);
    }

    public static RecipeConfig copyConfig(@Nullable RecipeConfig source) {
        if (source == null) return RecipeConfig.empty();
        return copyOptionalConfig(source);
    }

    @Nullable
    public static RecipeConfig copyOptionalConfig(@Nullable RecipeConfig source) {
        if (source == null) return null;
        SavedRecipeList savedRecipes = new SavedRecipeList();
        for (SavedRecipe recipe : source.savedRecipes()) {
            savedRecipes.add(recipe);
        }
        return new RecipeConfig(savedRecipes, source.mode(), source.notDoablePolicy(), (byte) 0, (byte) 0);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof RecipeModuleSettings settings && Objects.equals(config, settings.config);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(config);
    }
}
