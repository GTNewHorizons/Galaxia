package com.gtnewhorizons.galaxia.registry.outpost.recipe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RecipeSlotList extends ArrayList<RecipeSlot> {

    public static final int MAX_RECIPE_SLOTS = 32;

    public RecipeSlotList() {
        super(MAX_RECIPE_SLOTS);
    }

    @Override
    public boolean add(RecipeSlot slot) {
        if (size() >= MAX_RECIPE_SLOTS) {
            throw new IllegalStateException("Recipe slot list is full (" + MAX_RECIPE_SLOTS + " slots)");
        }
        return super.add(slot);
    }

    @Override
    public void add(int index, RecipeSlot element) {
        if (size() >= MAX_RECIPE_SLOTS) {
            throw new IllegalStateException("Recipe slot list is full (" + MAX_RECIPE_SLOTS + " slots)");
        }
        super.add(index, element);
    }

    public void setOrAppend(int index, RecipeSlot slot) {
        if (index < size()) {
            super.set(index, slot);
        } else if (index == size()) {
            add(slot);
        } else {
            throw new IndexOutOfBoundsException("Index: " + index + " > size: " + size());
        }
    }

    /** Null-safe access: returns the slot at {@code index}, or {@code null} if out of bounds. */
    public RecipeSlot getOrNull(int index) {
        return index >= 0 && index < size() ? get(index) : null;
    }

    public List<RecipeSlot> toList() {
        return Collections.unmodifiableList(new ArrayList<>(this));
    }
}
