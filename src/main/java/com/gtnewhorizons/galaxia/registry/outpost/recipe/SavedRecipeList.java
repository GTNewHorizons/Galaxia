package com.gtnewhorizons.galaxia.registry.outpost.recipe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SavedRecipeList extends ArrayList<SavedRecipe> {

    public static final int MAX_SAVED_RECIPES = 32;

    public SavedRecipeList() {
        super(MAX_SAVED_RECIPES);
    }

    @Override
    public boolean add(SavedRecipe slot) {
        if (size() >= MAX_SAVED_RECIPES) {
            throw new IllegalStateException("Recipe slot list is full (" + MAX_SAVED_RECIPES + " slots)");
        }
        return super.add(slot);
    }

    @Override
    public void add(int index, SavedRecipe element) {
        if (size() >= MAX_SAVED_RECIPES) {
            throw new IllegalStateException("Recipe slot list is full (" + MAX_SAVED_RECIPES + " slots)");
        }
        super.add(index, element);
    }

    public void setOrAppend(int index, SavedRecipe slot) {
        if (index < size()) {
            super.set(index, slot);
        } else if (index == size()) {
            add(slot);
        } else {
            throw new IndexOutOfBoundsException("Index: " + index + " > size: " + size());
        }
    }

    /** Null-safe access: returns the slot at {@code index}, or {@code null} if out of bounds. */
    public SavedRecipe getOrNull(int index) {
        return index >= 0 && index < size() ? get(index) : null;
    }

    public List<SavedRecipe> toList() {
        return Collections.unmodifiableList(new ArrayList<>(this));
    }
}
