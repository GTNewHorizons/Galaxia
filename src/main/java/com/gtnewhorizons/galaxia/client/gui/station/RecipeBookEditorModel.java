package com.gtnewhorizons.galaxia.client.gui.station;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.gtnewhorizons.galaxia.registry.outpost.recipe.NotDoablePolicy;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeBook;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeBookOwner;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSchedulerMode;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSnapshot;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.SavedRecipe;

final class RecipeBookEditorModel {

    private final RecipeBookOwner owner;
    private final List<SavedRecipe> recipes;
    private RecipeSchedulerMode mode;
    private NotDoablePolicy notDoablePolicy;
    private int selectedIndex;

    private RecipeBookEditorModel(RecipeBookOwner owner, RecipeBook source) {
        this.owner = owner;
        this.recipes = new ArrayList<>(source.recipes());
        this.mode = source.mode();
        this.notDoablePolicy = source.notDoablePolicy();
        this.selectedIndex = recipes.isEmpty() ? -1 : 0;
    }

    static RecipeBookEditorModel edit(RecipeBookOwner owner, RecipeBook source) {
        return new RecipeBookEditorModel(owner, source);
    }

    RecipeBookOwner owner() {
        return owner;
    }

    List<SavedRecipe> recipes() {
        return List.copyOf(recipes);
    }

    RecipeSchedulerMode mode() {
        return mode;
    }

    NotDoablePolicy notDoablePolicy() {
        return notDoablePolicy;
    }

    int selectedIndex() {
        return selectedIndex;
    }

    @Nullable
    SavedRecipe selectedRecipe() {
        return selectedIndex >= 0 ? recipes.get(selectedIndex) : null;
    }

    boolean select(int index) {
        if (!contains(index)) return false;
        selectedIndex = index;
        return true;
    }

    boolean add(RecipeSnapshot snapshot) {
        if (snapshot == null || !canAdd()) return false;
        recipes.add(new SavedRecipe(snapshot, true, 0L, (byte) 1, (byte) 1));
        if (selectedIndex < 0) selectedIndex = 0;
        return true;
    }

    boolean canAdd() {
        return recipes.size() < RecipeBook.MAX_RECIPES;
    }

    boolean update(int index, SavedRecipe replacement) {
        if (!contains(index) || replacement == null) return false;
        recipes.set(index, replacement);
        return true;
    }

    boolean remove(int index) {
        if (!contains(index)) return false;
        recipes.remove(index);
        if (recipes.isEmpty()) {
            selectedIndex = -1;
        } else if (selectedIndex > index) {
            selectedIndex--;
        } else if (selectedIndex == index) {
            selectedIndex = Math.min(index, recipes.size() - 1);
        }
        return true;
    }

    boolean rename(int index, String displayName) {
        if (!contains(index)) return false;
        recipes.set(
            index,
            recipes.get(index)
                .withDisplayName(displayName));
        return true;
    }

    void cycleMode() {
        RecipeSchedulerMode[] modes = RecipeSchedulerMode.values();
        mode = modes[(mode.ordinal() + 1) % modes.length];
    }

    void cycleNotDoablePolicy() {
        NotDoablePolicy[] policies = NotDoablePolicy.values();
        notDoablePolicy = policies[(notDoablePolicy.ordinal() + 1) % policies.length];
    }

    RecipeBook replacement() {
        return new RecipeBook(recipes, mode, notDoablePolicy);
    }

    private boolean contains(int index) {
        return index >= 0 && index < recipes.size();
    }
}
