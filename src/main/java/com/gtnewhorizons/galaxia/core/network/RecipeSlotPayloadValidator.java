package com.gtnewhorizons.galaxia.core.network;

import javax.annotation.Nullable;

import com.gtnewhorizons.galaxia.registry.outpost.module.IRecipeModule;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.GTRecipeMapId;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSnapshot;

import gregtech.api.recipe.RecipeMap;
import gregtech.api.util.GTRecipe;

final class RecipeSlotPayloadValidator {

    private RecipeSlotPayloadValidator() {}

    static @Nullable RecipeSnapshot validate(@Nullable IRecipeModule module, @Nullable RecipeSnapshot snapshot) {
        if (snapshot == null) return null;

        int mapOrdinal = Byte.toUnsignedInt(snapshot.recipeMapOrdinal());
        GTRecipeMapId[] ids = GTRecipeMapId.values();
        if (mapOrdinal <= GTRecipeMapId.INVALID.ordinal() || mapOrdinal >= ids.length) return null;

        if (module == null) return null;
        GTRecipeMapId mapId = ids[mapOrdinal];
        RecipeMap<?> expectedMap = GTRecipeMapId.findRecipeMap(mapId);
        RecipeMap<?> moduleMap = module.getRecipeMap();
        if (expectedMap == null || moduleMap == null) return null;
        if (!expectedMap.unlocalizedName.equals(moduleMap.unlocalizedName)) return null;

        GTRecipe[] recipes = GTRecipeMapId.getRecipes(mapId);
        int recipeIndex = snapshot.recipeIndex();
        if (recipes == null || recipeIndex < 0 || recipeIndex >= recipes.length) return null;

        GTRecipe recipe = recipes[recipeIndex];
        if (recipe == null || recipe.mHidden || recipe.mFakeRecipe) return null;

        long expectedHash = RecipeSnapshot.computeContentHash(
            recipe.mInputs,
            recipe.mOutputs,
            recipe.mFluidInputs,
            recipe.mFluidOutputs,
            recipe.mDuration,
            recipe.mEUt);
        if (expectedHash != snapshot.contentHash()) return null;

        return RecipeSnapshot.resolved(snapshot.recipeMapOrdinal(), recipeIndex, recipe);
    }
}
