package com.gtnewhorizons.galaxia.registry.outpost.recipe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public class RecipeSlotList {

    public static final int MAX_RECIPE_SLOTS = 32;

    private final RecipeSlot[] slots;

    public RecipeSlotList() {
        this.slots = new RecipeSlot[MAX_RECIPE_SLOTS];
    }

    public void add(RecipeSlot slot) {
        Objects.requireNonNull(slot, "slot must not be null");
        for (int i = 0; i < MAX_RECIPE_SLOTS; i++) {
            if (slots[i] == null) {
                slots[i] = slot;
                return;
            }
        }
        throw new IllegalStateException("Recipe slot list is full (" + MAX_RECIPE_SLOTS + " slots)");
    }

    public RecipeSlot get(int index) {
        if (index < 0 || index >= MAX_RECIPE_SLOTS) {
            throw new IndexOutOfBoundsException("Index: " + index + " out of bounds [0, " + MAX_RECIPE_SLOTS + ")");
        }
        RecipeSlot slot = slots[index];
        if (slot == null) {
            throw new IndexOutOfBoundsException("No recipe at index " + index);
        }
        return slot;
    }

    public void set(int index, RecipeSlot slot) {
        Objects.requireNonNull(slot, "slot must not be null");
        if (index < 0 || index >= MAX_RECIPE_SLOTS) {
            throw new IndexOutOfBoundsException("Index: " + index + " out of bounds [0, " + MAX_RECIPE_SLOTS + ")");
        }
        slots[index] = slot;
    }

    public void remove(int index) {
        if (index < 0 || index >= MAX_RECIPE_SLOTS) {
            throw new IndexOutOfBoundsException("Index: " + index + " out of bounds [0, " + MAX_RECIPE_SLOTS + ")");
        }
        RecipeSlot slot = slots[index];
        if (slot == null) {
            throw new IndexOutOfBoundsException("No recipe at index " + index);
        }
        slots[index] = null;
    }

    public int size() {
        int count = 0;
        for (RecipeSlot slot : slots) {
            if (slot != null) count++;
        }
        return count;
    }

    public boolean isEmpty() {
        for (RecipeSlot slot : slots) {
            if (slot != null) return false;
        }
        return true;
    }

    public void forEach(Consumer<? super RecipeSlot> action) {
        Objects.requireNonNull(action, "action must not be null");
        for (RecipeSlot slot : slots) {
            if (slot != null) {
                action.accept(slot);
            }
        }
    }

    // Package-private: null-safe access for RecipeScheduler
    RecipeSlot getOrNull(int index) {
        if (index < 0 || index >= MAX_RECIPE_SLOTS) return null;
        return slots[index];
    }

    public List<RecipeSlot> toList() {
        List<RecipeSlot> result = new ArrayList<>(size());
        for (RecipeSlot slot : slots) {
            if (slot != null) {
                result.add(slot);
            }
        }
        return Collections.unmodifiableList(result);
    }
}
