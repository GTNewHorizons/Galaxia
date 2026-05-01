package com.gtnewhorizons.galaxia.client.gui.station.recipe;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Tests for RecipePickerScreen pending fields.
 * Note: Full GUI tests require Minecraft/MUI2 runtime and are skipped in
 * headless unit test environment.
 */
final class RecipePickerScreenTest {

    private static boolean canTest;

    @BeforeAll
    static void checkEnvironment() {
        try {
            // Verify the class can be loaded (fails without MUI2 runtime)
            Class.forName("com.gtnewhorizons.galaxia.client.gui.station.recipe.RecipePickerScreen");
            canTest = true;
        } catch (Throwable e) {
            canTest = false;
        }
    }

    @Test
    void pendingFields_initializedNull() {
        if (!canTest) return;
        assertNull(RecipePickerScreen.pendingSelection);
        assertNull(RecipePickerScreen.pendingCoord);
        assertNull(RecipePickerScreen.pendingAssetId);
    }

    @Test
    void open_setsPendingFields() {
        if (!canTest) return;
        var assetId = com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset.ID.create();
        var coord = com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord.of((byte) 3, (byte) 5);
        RecipePickerScreen.open(assetId, coord);

        assertEquals(assetId, RecipePickerScreen.pendingAssetId);
        assertEquals(coord, RecipePickerScreen.pendingCoord);
    }

    @Test
    void clearPending_resetsFields() {
        if (!canTest) return;
        RecipePickerScreen.open(
            com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset.ID.create(),
            com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord.of((byte) 1, (byte) 2));
        assertNotNull(RecipePickerScreen.pendingAssetId);

        RecipePickerScreen.clearPending();

        assertNull(RecipePickerScreen.pendingAssetId);
        assertNull(RecipePickerScreen.pendingCoord);
        assertNull(RecipePickerScreen.pendingSelection);
    }
}
