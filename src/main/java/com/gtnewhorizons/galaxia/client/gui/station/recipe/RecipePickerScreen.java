package com.gtnewhorizons.galaxia.client.gui.station.recipe;

import static com.gtnewhorizons.galaxia.api.GalaxiaAPI.isGregTech5UnofficialNewHorizonsLoaded;

import com.gtnewhorizons.galaxia.compat.recipe.GTRecipePickerScreen;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

public final class RecipePickerScreen {

    private RecipePickerScreen() {}

    public static void open(CelestialAsset.ID assetId, StationTileCoord coord) {
        if (!isGregTech5UnofficialNewHorizonsLoaded()) return;
        GTRecipePickerScreen.open(assetId, coord);
    }

    public static void clearPending() {
        if (!isGregTech5UnofficialNewHorizonsLoaded()) return;
        GTRecipePickerScreen.clearPending();
    }
}
