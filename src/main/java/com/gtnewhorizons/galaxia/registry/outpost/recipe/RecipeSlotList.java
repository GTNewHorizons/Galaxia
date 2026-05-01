package com.gtnewhorizons.galaxia.registry.outpost.recipe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RecipeSlotList {

    public static final int MAX_RECIPE_SLOTS = 32;

    private final List<RecipeSlot> slots = new ArrayList<>(MAX_RECIPE_SLOTS);

    public void add(RecipeSlot slot) {
        if (slots.size() >= MAX_RECIPE_SLOTS) {
            throw new IllegalStateException("Recipe slot list is full (" + MAX_RECIPE_SLOTS + " slots)");
        }
        slots.add(slot);
    }

    public RecipeSlot get(int index) {
        return slots.get(index);
    }

    public void set(int index, RecipeSlot slot) {
        if (index < slots.size()) {
            slots.set(index, slot);
        } else if (index == slots.size()) {
            add(slot);
        } else {
            throw new IndexOutOfBoundsException("Index: " + index + " > size: " + slots.size());
        }
    }

    public void remove(int index) {
        slots.remove(index);
    }

    public int size() {
        return slots.size();
    }

    public boolean isEmpty() {
        return slots.isEmpty();
    }

    /** Null-safe access: returns the slot at {@code index}, or {@code null} if out of bounds. */
    public RecipeSlot getOrNull(int index) {
        return index >= 0 && index < slots.size() ? slots.get(index) : null;
    }

    public List<RecipeSlot> toList() {
        return Collections.unmodifiableList(new ArrayList<>(slots));
    }
}
