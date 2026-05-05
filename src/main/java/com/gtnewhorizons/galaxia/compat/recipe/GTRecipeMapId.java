package com.gtnewhorizons.galaxia.compat.recipe;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;

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
