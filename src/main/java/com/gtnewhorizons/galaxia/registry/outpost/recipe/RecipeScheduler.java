package com.gtnewhorizons.galaxia.registry.outpost.recipe;

import java.util.Random;

public final class RecipeScheduler {

    private RecipeScheduler() {}

    public static int nextSlot(RecipeConfig config, Random random) {
        switch (config.mode()) {
            case PRIORITY:
                return nextSlotPriority(config.savedRecipes());
            case ORDER:
                return nextSlotOrder(config);
            case RANDOM:
                return nextSlotRandom(config.savedRecipes(), random);
            default:
                return -1;
        }
    }

    static int nextSlotPriority(SavedRecipeList slots) {
        int bestIndex = -1;
        byte bestPriority = -1;
        for (int i = 0; i < slots.size(); i++) {
            SavedRecipe slot = slots.get(i);
            if (slot.enabled() && slot.priority() > bestPriority) {
                bestIndex = i;
                bestPriority = slot.priority();
            }
        }
        return bestIndex;
    }

    static int nextSlotOrder(RecipeConfig config) {
        byte cursor = config.orderCursor();
        byte remaining = config.orderRemaining();
        SavedRecipeList slots = config.savedRecipes();
        int size = slots.size();
        if (size == 0) return -1;

        if (remaining > 0 && cursor < size
            && slots.get(cursor)
                .enabled()) {
            return cursor;
        }

        for (int i = 1; i <= size; i++) {
            int idx = (cursor + i) % size;
            SavedRecipe slot = slots.get(idx);
            if (slot.enabled()) return idx;
        }
        return -1;
    }

    static int nextSlotRandom(SavedRecipeList slots, Random random) {
        int size = slots.size();
        // Build the list of enabled indices in one pass
        int[] enabled = new int[size];
        int count = 0;
        for (int i = 0; i < size; i++) {
            if (slots.get(i)
                .enabled()) enabled[count++] = i;
        }
        if (count == 0) return -1;
        return enabled[random.nextInt(count)];
    }

    public static RecipeConfig advanceOrder(RecipeConfig config) {
        byte remaining = config.orderRemaining();
        byte cursor = config.orderCursor();
        SavedRecipeList slots = config.savedRecipes();
        int size = slots.size();
        if (size == 0) return config;

        if (remaining > 1) {
            return new RecipeConfig(slots, config.mode(), config.notDoablePolicy(), cursor, (byte) (remaining - 1));
        }

        for (int i = 1; i <= size; i++) {
            int idx = (cursor + i) % size;
            SavedRecipe slot = slots.get(idx);
            if (slot.enabled()) {
                return new RecipeConfig(slots, config.mode(), config.notDoablePolicy(), (byte) idx, slot.orderSize());
            }
        }
        return config;
    }
}
