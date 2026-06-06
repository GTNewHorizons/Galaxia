package com.gtnewhorizons.galaxia.client.gui.station.recipe;

import static com.gtnewhorizons.galaxia.api.GalaxiaAPI.isGregTechLoaded;

import com.gtnewhorizons.galaxia.compat.recipe.GTRecipeInputScreen;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;

public final class RecipeInputScreen {

    private RecipeInputScreen() {}

    public static void open(CelestialAsset.ID assetId, int moduleIndex, ModuleInstance module) {
        if (!isGregTechLoaded()) return;
        GTRecipeInputScreen.open(assetId, moduleIndex, module);
    }
}
