package com.gtnewhorizons.galaxia.client.gui.station;

import com.gtnewhorizons.galaxia.registry.outpost.recipe.SavedRecipe;

final class RecipeRenameFormModel {

    private boolean open;
    private String input = "";

    void open(SavedRecipe slot) {
        open = true;
        input = slot.displayName() == null || slot.displayName()
            .isBlank() ? RecipeSlotUiModel.slotTitle(slot) : slot.displayName();
    }

    boolean isOpen() {
        return open;
    }

    String input() {
        return input;
    }

    void setInput(String input) {
        this.input = input == null ? "" : input;
    }

    boolean canSave() {
        return open && !input.trim()
            .isEmpty();
    }

    SavedRecipe save(SavedRecipe slot) {
        SavedRecipe updated = slot.withDisplayName(input);
        close();
        return updated;
    }

    SavedRecipe clear(SavedRecipe slot) {
        SavedRecipe updated = slot.withDisplayName("");
        close();
        return updated;
    }

    void close() {
        open = false;
        input = "";
    }
}
