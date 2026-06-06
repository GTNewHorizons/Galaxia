package com.gtnewhorizons.galaxia.client.gui.station.recipe;

import static com.gtnewhorizons.galaxia.api.GalaxiaAPI.isGregTechLoaded;

import com.gtnewhorizons.galaxia.compat.recipe.GTRecipePickerScreen;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

public final class RecipePickerScreen {

    private RecipePickerScreen() {}

    public static void open(CelestialAsset.ID assetId, StationTileCoord coord) {
        if (!isGregTechLoaded()) return;
        GTRecipePickerScreen.open(assetId, coord);
    }

    public static void clearPending() {
        if (!isGregTechLoaded()) return;
        GTRecipePickerScreen.clearPending();
    }
}
