package com.gtnewhorizons.galaxia.registry.outpost.module;

import java.util.Collections;
import java.util.List;
import java.util.Random;

import com.gtnewhorizons.galaxia.registry.interfaces.IModuleComponent;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeConfig;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeScheduler;
import com.gtnewhorizons.galaxia.registry.outpost.station.settings.ModuleSettings;
import com.gtnewhorizons.galaxia.registry.outpost.station.settings.RecipeModuleSettings;

public interface IRecipeModule extends IModuleComponent {

    String getRecipeMapName();

    /**
     * Returns additional NEI recipe transfer idents beyond the main RecipeMap's
     * unlocalizedName. Override to support category-filtered NEI pages (e.g.
     * macerator recycling).
     */
    default List<String> getAdditionalNeiTransferIdents() {
        return Collections.emptyList();
    }

    @javax.annotation.Nullable
    RecipeConfig getRecipeConfig();

    void setRecipeConfig(@javax.annotation.Nullable RecipeConfig config);

    @Override
    default ModuleSettings createPrivateSettings(ModuleInstance module) {
        return new RecipeModuleSettings(getRecipeConfig());
    }

    @Override
    default void applySettings(ModuleInstance module, ModuleSettings settings) {
        if (!(settings instanceof RecipeModuleSettings recipeSettings)) {
            throw new IllegalStateException("Recipe module received non-recipe settings for module " + module.id);
        }
        recipeSettings.applyTo(module);
    }

    default int getNextSlot(Random random) {
        RecipeConfig cfg = getRecipeConfig();
        if (cfg == null) return -1;
        return RecipeScheduler.nextSlot(cfg, random);
    }
}
