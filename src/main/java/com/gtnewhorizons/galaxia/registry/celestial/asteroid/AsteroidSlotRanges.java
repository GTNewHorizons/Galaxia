package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

/**
 * Stable id ranges for asteroid slots.
 *
 * Saves reference slot numbers, so authored ranges stay bounded and generated
 * slots grow upward without ever reusing lore/unique ids.
 */
public final class AsteroidSlotRanges {

    public static final int LORE_SLOT_MIN = 0;
    public static final int LORE_SLOT_MAX = 1999;
    public static final int UNIQUE_SLOT_MIN = 2000;
    public static final int UNIQUE_SLOT_MAX = 3999;
    public static final int GENERATED_SLOT_MIN = 4000;

    private AsteroidSlotRanges() {}

    public static boolean isLoreSlot(int slot) {
        return slot >= LORE_SLOT_MIN && slot <= LORE_SLOT_MAX;
    }

    public static boolean isUniqueSlot(int slot) {
        return slot >= UNIQUE_SLOT_MIN && slot <= UNIQUE_SLOT_MAX;
    }

    public static boolean isGeneratedSlot(int slot) {
        return slot >= GENERATED_SLOT_MIN;
    }

    public static int generatedSlot(int ordinal) {
        if (ordinal < 0) throw new IllegalArgumentException("generated ordinal must be non-negative");
        return GENERATED_SLOT_MIN + ordinal;
    }

    public static int generatedOrdinal(int slot) {
        if (!isGeneratedSlot(slot)) throw new IllegalArgumentException("slot is not in the generated asteroid range");
        return slot - GENERATED_SLOT_MIN;
    }
}
