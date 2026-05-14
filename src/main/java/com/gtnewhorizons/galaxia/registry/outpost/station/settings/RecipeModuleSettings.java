package com.gtnewhorizons.galaxia.registry.outpost.station.settings;

import javax.annotation.Nullable;

import com.gtnewhorizons.galaxia.registry.outpost.module.IRecipeModule;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeConfig;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.SavedRecipe;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.SavedRecipeList;

public final class RecipeModuleSettings implements ModuleSettings {

    private RecipeConfig config;

    public RecipeModuleSettings(@Nullable RecipeConfig config) {
        this.config = copyOptionalConfig(config);
    }

    @Nullable
    public RecipeConfig config() {
        return config;
    }

    public void setConfig(@Nullable RecipeConfig config) {
        this.config = copyOptionalConfig(config);
    }

    @Override
    public RecipeModuleSettings copy() {
        return new RecipeModuleSettings(config);
    }

    @Override
    public void applyTo(ModuleInstance instance) {
        if (!(instance.component() instanceof IRecipeModule recipeModule)) {
            throw new IllegalStateException("RecipeModuleSettings applied to non-recipe module " + instance.id);
        }
        recipeModule.setRecipeConfig(copyOptionalConfig(config));
    }

    @Override
    public ModuleSettings from(ModuleInstance instance) {
        if (!(instance.component() instanceof IRecipeModule recipeModule)) {
            throw new IllegalStateException("RecipeModuleSettings read from non-recipe module " + instance.id);
        }
        return new RecipeModuleSettings(recipeModule.getRecipeConfig());
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
        return new RecipeConfig(
            savedRecipes,
            source.mode(),
            source.notDoablePolicy(),
            source.orderCursor(),
            source.orderRemaining());
    }
}
