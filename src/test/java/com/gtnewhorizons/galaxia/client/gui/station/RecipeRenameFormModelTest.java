package com.gtnewhorizons.galaxia.client.gui.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSnapshot;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.SavedRecipe;

class RecipeRenameFormModelTest {

    @Test
    void opensWithDisplayNameOrFallbackTitle() {
        RecipeRenameFormModel model = new RecipeRenameFormModel();

        model.open(slot(""));

        assertEquals("Recipe #4", model.input());
        assertTrue(model.isOpen());
    }

    @Test
    void blankInputCannotBeSaved() {
        RecipeRenameFormModel model = new RecipeRenameFormModel();
        model.open(slot("Current"));
        model.setInput("   ");

        assertFalse(model.canSave());
    }

    @Test
    void saveAndClearBuildUpdatedSlotsAndCloseForm() {
        RecipeRenameFormModel model = new RecipeRenameFormModel();
        SavedRecipe slot = slot("Current");
        model.open(slot);
        model.setInput(" Renamed ");

        SavedRecipe renamed = model.save(slot);
        assertEquals("Renamed", renamed.displayName());
        assertFalse(model.isOpen());

        model.open(renamed);
        SavedRecipe cleared = model.clear(renamed);
        assertEquals("", cleared.displayName());
        assertFalse(model.isOpen());
    }

    private static SavedRecipe slot(String displayName) {
        return new SavedRecipe(RecipeSnapshot.unresolved((byte) 1, 4, 42L), true, 0L, (byte) 1, (byte) 1, displayName);
    }
}
