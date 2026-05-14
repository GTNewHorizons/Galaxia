package com.gtnewhorizons.galaxia.compat.recipe;

import java.util.Locale;
import java.util.Random;

import javax.annotation.Nullable;

/**
 * Mirrors GTRecipe chance semantics: recipe chances are stored in 1/10000 units and missing chances mean guaranteed.
 */
public final class GTRecipeChance {

    public static final int GUARANTEED = 10_000;

    private GTRecipeChance() {}

    public static boolean shouldProduce(@Nullable int[] chances, int index, Random random) {
        if (chances == null || index < 0 || index >= chances.length) return true;
        int chance = chances[index];
        if (chance < 0) return true;
        if (chance == 0) return false;
        if (chance >= GUARANTEED) return true;
        return random.nextInt(GUARANTEED) < chance;
    }

    public static @Nullable String optionalOutputLabel(@Nullable int[] chances, int index) {
        if (chances == null || index < 0 || index >= chances.length) return null;
        int chance = chances[index];
        if (chance < 0 || chance >= GUARANTEED) return null;
        if (chance == 0) return "0%";
        if (chance % 100 == 0) return chance / 100 + "%";
        return String.format(Locale.ROOT, "%.2f%%", chance / 100.0D);
    }
}
