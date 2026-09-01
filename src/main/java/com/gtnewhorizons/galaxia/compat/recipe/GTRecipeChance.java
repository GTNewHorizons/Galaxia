package com.gtnewhorizons.galaxia.compat.recipe;

import java.util.Locale;
import java.util.Random;

import javax.annotation.Nullable;

import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSnapshot.Resource;

/**
 * Mirrors GTRecipe chance semantics: recipe chances are stored in 1/10000 units and missing chances mean guaranteed.
 */
public final class GTRecipeChance {

    public static final int GUARANTEED = 10_000;

    private GTRecipeChance() {}

    public static boolean shouldProduce(Resource output, Random random) {
        if (!output.hasChance()) return true;
        int chance = output.effectiveChance();
        if (chance == 0) return false;
        if (chance >= GUARANTEED) return true;
        return random.nextInt(GUARANTEED) < chance;
    }

    public static @Nullable String optionalOutputLabel(@Nullable Resource output) {
        if (output == null || !output.hasChance()) return null;
        int chance = output.effectiveChance();
        if (chance >= GUARANTEED) return null;
        if (chance == 0) return "0%";
        if (chance % 100 == 0) return chance / 100 + "%";
        return String.format(Locale.ROOT, "%.2f%%", chance / 100.0D);
    }
}
