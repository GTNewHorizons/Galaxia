package com.gtnewhorizons.galaxia.client.gui.station.recipe;

import com.gtnewhorizons.galaxia.compat.GregTechCompat;
import com.gtnewhorizons.galaxia.compat.recipe.GTRecipePickerScreen;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

public final class RecipePickerScreen {

    private RecipePickerScreen() {}

    public static void open(CelestialAsset.ID assetId, StationTileCoord coord) {
        if (!GregTechCompat.isGregTechLoaded()) return;
        GTRecipePickerScreen.open(assetId, coord);
    }

    public static void clearPending() {
        if (!GregTechCompat.isGregTechLoaded()) return;
        GTRecipePickerScreen.clearPending();
    }
}
