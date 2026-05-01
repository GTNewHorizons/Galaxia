package com.gtnewhorizons.galaxia.registry.outpost.recipe;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * All GT5-specific recipe resolution isolated in one file.
 * Every method safely returns null when GT5 is absent.
 */
public final class GT5RecipeBridge {

    private static boolean gt5Loaded = true; // default for tests; set during mod init

    public static void setAvailable(boolean available) {
        gt5Loaded = available;
    }

    private static Map<GTRecipeMapId, Object> mapCache;
    private static Map<GTRecipeMapId, Object[]> recipeCache;

    private GT5RecipeBridge() {}

    // ── RecipeMap lookup ──

    @Nullable
    static Object findRecipeMap(GTRecipeMapId id) {
        if (!gt5Loaded || id == null || id == GTRecipeMapId.INVALID) return null;
        if (mapCache == null) mapCache = new EnumMap<>(GTRecipeMapId.class);
        Object cached = mapCache.get(id);
        if (cached != null) return cached;
        try {
            Class<?> recipeMapClass = Class.forName("gregtech.api.recipe.RecipeMap");
            Object allMaps = recipeMapClass.getField("ALL_RECIPE_MAPS")
                .get(null);
            Object map = ((Map<?, ?>) allMaps).get(id.getRecipeMapUnlocalizedName());
            if (map != null) mapCache.put(id, map);
            return map;
        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    static Object[] getRecipes(GTRecipeMapId id) {
        if (!gt5Loaded || id == null || id == GTRecipeMapId.INVALID) return null;
        if (recipeCache == null) recipeCache = new EnumMap<>(GTRecipeMapId.class);
        Object[] cached = recipeCache.get(id);
        if (cached != null) return cached;
        Object map = findRecipeMap(id);
        if (map == null) return null;
        try {
            Collection<?> allRecipes = (Collection<?>) map.getClass()
                .getMethod("getAllRecipes")
                .invoke(map);
            Object[] recipes = allRecipes.toArray();
            recipeCache.put(id, recipes);
            return recipes;
        } catch (Exception e) {
            return null;
        }
    }

    // ── Content hash ──

    static long computeContentHash(Object gtRecipe) {
        if (!gt5Loaded || gtRecipe == null) return 0;
        try {
            Class<?> c = gtRecipe.getClass();
            return GT5RecipeRef.computeContentHash(
                (net.minecraft.item.ItemStack[]) c.getField("mInputs")
                    .get(gtRecipe),
                (net.minecraft.item.ItemStack[]) c.getField("mOutputs")
                    .get(gtRecipe),
                (net.minecraftforge.fluids.FluidStack[]) c.getField("mFluidInputs")
                    .get(gtRecipe),
                (net.minecraftforge.fluids.FluidStack[]) c.getField("mFluidOutputs")
                    .get(gtRecipe),
                c.getField("mDuration")
                    .getInt(gtRecipe),
                c.getField("mEUt")
                    .getInt(gtRecipe));
        } catch (Exception e) {
            return 0;
        }
    }

    // ── Resolution ──

    @Nullable
    static Object resolve(GT5RecipeRef ref) {
        if (!gt5Loaded || ref == null) return null;
        GTRecipeMapId[] values = GTRecipeMapId.values();
        int ordinal = Byte.toUnsignedInt(ref.recipeMapOrdinal());
        if (ordinal >= values.length) return null;
        GTRecipeMapId mapId = values[ordinal];
        if (mapId == GTRecipeMapId.INVALID) return null;

        Object[] recipes = getRecipes(mapId);
        if (recipes == null) return null;

        int idx = ref.recipeIndex();
        if (idx >= 0 && idx < recipes.length) {
            Object candidate = recipes[idx];
            if (computeContentHash(candidate) == ref.contentHash()) return candidate;
        }
        for (Object r : recipes) {
            if (computeContentHash(r) == ref.contentHash()) return r;
        }
        return null;
    }
}
