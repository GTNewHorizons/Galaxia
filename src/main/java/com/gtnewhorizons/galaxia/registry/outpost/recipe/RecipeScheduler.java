package com.gtnewhorizons.galaxia.registry.outpost.recipe;

import java.util.Random;

public final class RecipeScheduler {

    private RecipeScheduler() {}

    public static int nextSlot(RecipeConfig config, Random random) {
        switch (config.mode()) {
            case PRIORITY:
                return nextSlotPriority(config.slots());
            case ORDER:
                return nextSlotOrder(config);
            case RANDOM:
                return nextSlotRandom(config.slots(), random);
            default:
                return -1;
        }
    }

    public static int nextSlotPriority(RecipeSlotList slots) {
        int bestIndex = -1;
        byte bestPriority = -1;
        for (int i = 0; i < RecipeSlotList.MAX_RECIPE_SLOTS; i++) {
            RecipeSlot slot = slots.getOrNull(i);
            if (slot != null && slot.enabled() && slot.priority() > bestPriority) {
                bestIndex = i;
                bestPriority = slot.priority();
            }
        }
        return bestIndex;
    }

    public static int nextSlotOrder(RecipeConfig config) {
        byte cursor = config.orderCursor();
        byte remaining = config.orderRemaining();
        if (remaining > 0) {
            // Verify the cursor's slot still exists (player may have removed it)
            if (config.slots()
                .getOrNull(cursor) != null) {
                return cursor;
            }
            // Slot was removed; fall through to advance-logic
        }

        // remaining == 0 or cursor slot removed: find next enabled slot after cursor
        RecipeSlotList slots = config.slots();
        for (int i = 1; i <= RecipeSlotList.MAX_RECIPE_SLOTS; i++) {
            int idx = (cursor + i) % RecipeSlotList.MAX_RECIPE_SLOTS;
            RecipeSlot slot = slots.getOrNull(idx);
            if (slot != null && slot.enabled()) {
                return idx;
            }
        }
        return -1;
    }

    public static int nextSlotRandom(RecipeSlotList slots, Random random) {
        int enabledCount = 0;
        for (int i = 0; i < RecipeSlotList.MAX_RECIPE_SLOTS; i++) {
            RecipeSlot slot = slots.getOrNull(i);
            if (slot != null && slot.enabled()) {
                enabledCount++;
            }
        }
        if (enabledCount == 0) return -1;

        int pick = random.nextInt(enabledCount);
        int count = 0;
        for (int i = 0; i < RecipeSlotList.MAX_RECIPE_SLOTS; i++) {
            RecipeSlot slot = slots.getOrNull(i);
            if (slot != null && slot.enabled()) {
                if (count == pick) return i;
                count++;
            }
        }
        return -1;
    }

    public static RecipeConfig advanceOrder(RecipeConfig config) {
        byte remaining = config.orderRemaining();
        byte cursor = config.orderCursor();

        if (remaining > 1) {
            // Decrement remaining, stay on same cursor
            return new RecipeConfig(
                config.slots(),
                config.mode(),
                config.notDoablePolicy(),
                cursor,
                (byte) (remaining - 1));
        }

        // remaining <= 1: advance to next enabled slot
        RecipeSlotList slots = config.slots();
        for (int i = 1; i <= RecipeSlotList.MAX_RECIPE_SLOTS; i++) {
            int idx = (cursor + i) % RecipeSlotList.MAX_RECIPE_SLOTS;
            RecipeSlot slot = slots.getOrNull(idx);
            if (slot != null && slot.enabled()) {
                return new RecipeConfig(slots, config.mode(), config.notDoablePolicy(), (byte) idx, slot.orderSize());
            }
        }

        // No enabled slots — return same config
        return config;
    }
}
