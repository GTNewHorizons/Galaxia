package com.gtnewhorizons.galaxia.client.gui.station.recipe;

import static com.gtnewhorizons.galaxia.api.GalaxiaAPI.isGregTech5UnofficialNewHorizonsLoaded;

import com.gtnewhorizons.galaxia.compat.recipe.GTRecipeInputScreen;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;

public final class RecipeInputScreen {

    private RecipeInputScreen() {}

    public static void open(CelestialAsset.ID assetId, int moduleIndex, ModuleInstance module) {
        if (!isGregTech5UnofficialNewHorizonsLoaded()) return;
        GTRecipeInputScreen.open(assetId, moduleIndex, module);
    }
}
