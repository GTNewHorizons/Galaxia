package com.gtnewhorizons.galaxia.core.network;

import javax.annotation.Nullable;

import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.registry.outpost.module.IRecipeModule;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.GTRecipeMapId;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSnapshot;

import gregtech.api.recipe.RecipeMap;
import gregtech.api.util.GTRecipe;

final class RecipeSlotPayloadValidator {

    private RecipeSlotPayloadValidator() {}

    static @Nullable RecipeSnapshot validate(@Nullable IRecipeModule module, @Nullable RecipeSnapshot snapshot) {
        if (snapshot == null) {
            Galaxia.LOG.warn("[RecipeValidator] REJECTED: snapshot is null");
            throw new IllegalArgumentException("RecipeSlotPayloadValidator: snapshot is null");
        }

        int mapOrdinal = Byte.toUnsignedInt(snapshot.recipeMapOrdinal());
        GTRecipeMapId[] ids = GTRecipeMapId.values();
        if (mapOrdinal <= GTRecipeMapId.INVALID.ordinal() || mapOrdinal >= ids.length) {
            Galaxia.LOG.warn(
                "[RecipeValidator] REJECTED: invalid recipeMapOrdinal {} (valid range 1..{})",
                mapOrdinal,
                ids.length - 1);
            throw new IllegalArgumentException("RecipeSlotPayloadValidator: invalid recipeMapOrdinal " + mapOrdinal);
        }

        if (module == null) {
            Galaxia.LOG.error("[RecipeValidator] REJECTED: module is null — server-side invariant broken");
            throw new IllegalStateException("RecipeSlotPayloadValidator: module is null");
        }

        GTRecipeMapId mapId = ids[mapOrdinal];
        RecipeMap<?> expectedMap = GTRecipeMapId.findRecipeMap(mapId);
        RecipeMap<?> moduleMap = module.getRecipeMap();
        if (expectedMap == null) {
            Galaxia.LOG.error(
                "[RecipeValidator] REJECTED: GTRecipeMapId.findRecipeMap({}) returned null — RecipeMap not registered",
                mapId);
            throw new IllegalStateException("RecipeSlotPayloadValidator: expectedMap is null for " + mapId);
        }
        if (moduleMap == null) {
            Galaxia.LOG.error(
                "[RecipeValidator] REJECTED: module.getRecipeMap() returned null — server-side invariant broken");
            throw new IllegalStateException("RecipeSlotPayloadValidator: moduleMap is null");
        }
        if (!expectedMap.unlocalizedName.equals(moduleMap.unlocalizedName)) {
            Galaxia.LOG.warn(
                "[RecipeValidator] REJECTED: recipe map name mismatch — expected='{}' actual='{}'",
                expectedMap.unlocalizedName,
                moduleMap.unlocalizedName);
            throw new IllegalArgumentException(
                "RecipeSlotPayloadValidator: recipe map name mismatch — expected='" + expectedMap.unlocalizedName
                    + "' actual='"
                    + moduleMap.unlocalizedName
                    + "'");
        }

        GTRecipe[] recipes = GTRecipeMapId.getRecipes(mapId);
        int recipeIndex = snapshot.recipeIndex();
        if (recipes == null) {
            Galaxia.LOG.error(
                "[RecipeValidator] REJECTED: GTRecipeMapId.getRecipes({}) returned null — RecipeMap has no recipes",
                mapId);
            throw new IllegalStateException("RecipeSlotPayloadValidator: recipes array is null for " + mapId);
        }
        if (recipeIndex < 0 || recipeIndex >= recipes.length) {
            Galaxia.LOG
                .warn("[RecipeValidator] REJECTED: recipeIndex {} out of range [0, {})", recipeIndex, recipes.length);
            throw new IllegalArgumentException(
                "RecipeSlotPayloadValidator: recipeIndex " + recipeIndex + " out of range [0, " + recipes.length + ")");
        }

        GTRecipe recipe = recipes[recipeIndex];
        if (recipe == null) {
            Galaxia.LOG.error(
                "[RecipeValidator] REJECTED: recipe at index {} is null — RecipeMap data corruption",
                recipeIndex);
            throw new IllegalStateException("RecipeSlotPayloadValidator: recipe at index " + recipeIndex + " is null");
        }
        if (recipe.mHidden) {
            Galaxia.LOG
                .warn("[RecipeValidator] REJECTED: recipe at index {} ({}) is hidden", recipeIndex, recipe.mHidden);
            throw new IllegalArgumentException(
                "RecipeSlotPayloadValidator: recipe at index " + recipeIndex + " is hidden");
        }
        if (recipe.mFakeRecipe) {
            Galaxia.LOG.warn("[RecipeValidator] REJECTED: recipe at index {} is a fake recipe", recipeIndex);
            throw new IllegalArgumentException(
                "RecipeSlotPayloadValidator: recipe at index " + recipeIndex + " is a fake recipe");
        }

        long expectedHash = RecipeSnapshot.computeContentHash(
            recipe.mInputs,
            recipe.mOutputs,
            recipe.mFluidInputs,
            recipe.mFluidOutputs,
            recipe.mDuration,
            recipe.mEUt);
        long clientHash = snapshot.contentHash();
        if (expectedHash != clientHash) {
            Galaxia.LOG.warn(
                "[RecipeValidator] REJECTED: contentHash mismatch — client={} server={} (map={} index={})",
                clientHash,
                expectedHash,
                mapId,
                recipeIndex);
            throw new IllegalArgumentException(
                "RecipeSlotPayloadValidator: contentHash mismatch — client=" + clientHash
                    + " server="
                    + expectedHash
                    + " map="
                    + mapId
                    + " index="
                    + recipeIndex);
        }

        return RecipeSnapshot.resolved(snapshot.recipeMapOrdinal(), recipeIndex, recipe);
    }
}
