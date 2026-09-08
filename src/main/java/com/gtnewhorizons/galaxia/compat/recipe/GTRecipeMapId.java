package com.gtnewhorizons.galaxia.compat.recipe;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;

import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeBook;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSnapshot;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.SavedRecipe;

import gregtech.api.recipe.RecipeMap;
import gregtech.api.util.GTRecipe;

public enum GTRecipeMapId {

    INVALID(""),
    MACERATOR("gt.recipe.macerator"),
    CENTRIFUGE("gt.recipe.centrifuge"),
    ELECTROLYZER("gt.recipe.electrolyzer"),
    CHEMICAL_REACTOR("gt.recipe.chemicalreactor"),
    ASSEMBLER("gt.recipe.assembler"),
    DISTILLERY("gt.recipe.distillery");

    private static final Map<GTRecipeMapId, RecipeMap<?>> MAP_CACHE = new EnumMap<>(GTRecipeMapId.class);
    private static final Map<GTRecipeMapId, GTRecipe[]> RECIPE_CACHE = new EnumMap<>(GTRecipeMapId.class);

    private final String recipeMapUnlocalizedName;

    GTRecipeMapId(String recipeMapUnlocalizedName) {
        this.recipeMapUnlocalizedName = recipeMapUnlocalizedName;
    }

    public String getRecipeMapUnlocalizedName() {
        return recipeMapUnlocalizedName;
    }

    /** Resolves client selections against this server's catalog before they can become production settings. */
    public RecipeBook resolveBook(RecipeBook submitted) {
        var resolved = new ArrayList<SavedRecipe>();
        for (SavedRecipe slot : submitted.recipes()) {
            RecipeSnapshot requested = slot.recipe();
            GTRecipe[] recipes = getRecipes(this);
            int index = requested.recipeIndex();
            if (Byte.toUnsignedInt(requested.recipeMapOrdinal()) != ordinal() || recipes == null
                || index < 0
                || index >= recipes.length
                || recipes[index] == null
                || recipes[index].mHidden
                || recipes[index].mFakeRecipe) {
                throw new IllegalArgumentException("Recipe does not belong to the module's visible catalog");
            }
            RecipeSnapshot canonical = snapshot(index, recipes[index]);
            if (!canonical.equals(requested)) {
                throw new IllegalArgumentException("Recipe content differs from the server catalog");
            }
            resolved.add(
                new SavedRecipe(
                    canonical,
                    slot.enabled(),
                    slot.requestAmount(),
                    slot.priority(),
                    slot.orderSize(),
                    slot.displayName()));
        }
        return new RecipeBook(resolved, submitted.mode(), submitted.notDoablePolicy());
    }

    public RecipeSnapshot snapshot(int index, GTRecipe recipe) {
        return RecipeSnapshot.resolved(
            (byte) ordinal(),
            index,
            recipe.mInputs,
            recipe.mOutputs,
            recipe.mFluidInputs,
            recipe.mFluidOutputs,
            recipe.mOutputChances,
            recipe.mFluidOutputChances,
            recipe.mDuration,
            recipe.mEUt);
    }

    @javax.annotation.Nullable
    public static GTRecipeMapId fromRecipeMapName(String name) {
        if (name == null) return null;
        for (GTRecipeMapId id : values()) {
            if (id.recipeMapUnlocalizedName.equals(name)) {
                return id;
            }
        }
        return null;
    }

    @javax.annotation.Nullable
    public static RecipeMap<?> findRecipeMap(GTRecipeMapId id) {
        if (id == null || id == INVALID) return null;
        RecipeMap<?> cached = MAP_CACHE.get(id);
        if (cached != null) return cached;
        RecipeMap<?> map = RecipeMap.ALL_RECIPE_MAPS.get(id.recipeMapUnlocalizedName);
        if (map != null) {
            MAP_CACHE.put(id, map);
        }
        return map;
    }

    @javax.annotation.Nullable
    public static GTRecipe[] getRecipes(GTRecipeMapId id) {
        if (id == null || id == INVALID) return null;
        GTRecipe[] cached = RECIPE_CACHE.get(id);
        if (cached != null) return cached;
        RecipeMap<?> map = findRecipeMap(id);
        if (map == null) return null;
        Collection<GTRecipe> allRecipes = map.getAllRecipes();
        GTRecipe[] recipes = allRecipes.toArray(new GTRecipe[0]);
        RECIPE_CACHE.put(id, recipes);
        return recipes;
    }
}
