package com.gtnewhorizons.galaxia.client.gui.station.recipe;

import static com.gtnewhorizons.galaxia.api.GalaxiaAPI.isGregTech5UnofficialNewHorizonsLoaded;

import java.util.function.Predicate;

import com.gtnewhorizons.galaxia.compat.recipe.GTRecipeInputScreen;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSnapshot;

public final class RecipeInputScreen {

    private RecipeInputScreen() {}

    public static void open(ModuleInstance module, Predicate<RecipeSnapshot> onConfirm) {
        if (!isGregTech5UnofficialNewHorizonsLoaded()) return;
        GTRecipeInputScreen.open(module, onConfirm);
    }
}
