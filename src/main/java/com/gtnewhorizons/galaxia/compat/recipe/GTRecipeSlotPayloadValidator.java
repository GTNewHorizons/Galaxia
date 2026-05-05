package com.gtnewhorizons.galaxia.compat.recipe;

import javax.annotation.Nullable;

import org.apache.logging.log4j.Logger;

import com.gtnewhorizons.galaxia.registry.outpost.module.IRecipeModule;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSnapshot;

import gregtech.api.recipe.RecipeMap;
import gregtech.api.util.GTRecipe;

public final class GTRecipeSlotPayloadValidator {

    private GTRecipeSlotPayloadValidator() {}

    public static @Nullable RecipeSnapshot validate(@Nullable IRecipeModule module, @Nullable RecipeSnapshot snapshot,
        Logger log) {
        if (snapshot == null) {
            log.warn("[RecipeValidator] REJECTED: snapshot is null");
            return null;
        }

        int mapOrdinal = Byte.toUnsignedInt(snapshot.recipeMapOrdinal());
        GTRecipeMapId[] ids = GTRecipeMapId.values();
        if (mapOrdinal <= GTRecipeMapId.INVALID.ordinal() || mapOrdinal >= ids.length) {
            log.warn(
                "[RecipeValidator] REJECTED: invalid recipeMapOrdinal {} (valid range 1..{})",
                mapOrdinal,
                ids.length - 1);
            return null;
        }

        if (module == null) {
            log.error("[RecipeValidator] REJECTED: module is null; server-side invariant broken");
            return null;
        }

        GTRecipeMapId mapId = ids[mapOrdinal];
        RecipeMap<?> expectedMap = GTRecipeMapId.findRecipeMap(mapId);
        String moduleMapName = module.getRecipeMapName();
        if (expectedMap == null) {
            log.error(
                "[RecipeValidator] REJECTED: GTRecipeMapId.findRecipeMap({}) returned null; RecipeMap not registered",
                mapId);
            return null;
        }
        if (moduleMapName == null || moduleMapName.isEmpty()) {
            log.error(
                "[RecipeValidator] REJECTED: module.getRecipeMapName() returned blank; server-side invariant broken");
            return null;
        }
        if (!expectedMap.unlocalizedName.equals(moduleMapName)) {
            log.warn(
                "[RecipeValidator] REJECTED: recipe map name mismatch; expected='{}' actual='{}'",
                expectedMap.unlocalizedName,
                moduleMapName);
            return null;
        }

        GTRecipe[] recipes = GTRecipeMapId.getRecipes(mapId);
        int recipeIndex = snapshot.recipeIndex();
        if (recipes == null) {
            log.error(
                "[RecipeValidator] REJECTED: GTRecipeMapId.getRecipes({}) returned null; RecipeMap has no recipes",
                mapId);
            return null;
        }
        if (recipeIndex < 0 || recipeIndex >= recipes.length) {
            log.warn("[RecipeValidator] REJECTED: recipeIndex {} out of range [0, {})", recipeIndex, recipes.length);
            return null;
        }

        GTRecipe recipe = recipes[recipeIndex];
        if (recipe == null) {
            log.error("[RecipeValidator] REJECTED: recipe at index {} is null; RecipeMap data corruption", recipeIndex);
            return null;
        }
        if (recipe.mHidden) {
            log.warn("[RecipeValidator] REJECTED: recipe at index {} ({}) is hidden", recipeIndex, recipe.mHidden);
            return null;
        }
        if (recipe.mFakeRecipe) {
            log.warn("[RecipeValidator] REJECTED: recipe at index {} is a fake recipe", recipeIndex);
            return null;
        }

        long expectedHash = RecipeSnapshot.computeContentHash(
            recipe.mInputs,
            recipe.mOutputs,
            recipe.mFluidInputs,
            recipe.mFluidOutputs,
            recipe.mOutputChances,
            recipe.mFluidOutputChances,
            recipe.mDuration,
            recipe.mEUt);
        long legacyHash = RecipeSnapshot.computeContentHash(
            recipe.mInputs,
            recipe.mOutputs,
            recipe.mFluidInputs,
            recipe.mFluidOutputs,
            recipe.mDuration,
            recipe.mEUt);
        long clientHash = snapshot.contentHash();
        if (expectedHash != clientHash && legacyHash != clientHash) {
            log.warn(
                "[RecipeValidator] REJECTED: contentHash mismatch; client={} server={} legacyServer={} (map={} index={})",
                clientHash,
                expectedHash,
                legacyHash,
                mapId,
                recipeIndex);
            return null;
        }

        return RecipeSnapshot.resolved(
            snapshot.recipeMapOrdinal(),
            recipeIndex,
            recipe.mInputs,
            recipe.mOutputs,
            recipe.mFluidInputs,
            recipe.mFluidOutputs,
            recipe.mOutputChances,
            recipe.mFluidOutputChances,
            recipe.mDuration,
            recipe.mEUt);
    }
}
