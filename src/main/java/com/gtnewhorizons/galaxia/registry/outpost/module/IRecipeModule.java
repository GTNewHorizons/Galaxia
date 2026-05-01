package com.gtnewhorizons.galaxia.registry.outpost.module;

import java.util.Random;

import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeConfig;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeScheduler;

import gregtech.api.recipe.RecipeMap;

public interface IRecipeModule extends ModuleComponent {

    RecipeMap<?> getRecipeMap();

    @javax.annotation.Nullable
    RecipeConfig getRecipeConfig();

    void setRecipeConfig(@javax.annotation.Nullable RecipeConfig config);

    default int getNextSlot(Random random) {
        RecipeConfig cfg = getRecipeConfig();
        if (cfg == null) return -1;
        return RecipeScheduler.nextSlot(cfg, random);
    }
}
