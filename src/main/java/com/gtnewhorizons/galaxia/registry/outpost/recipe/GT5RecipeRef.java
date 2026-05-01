package com.gtnewhorizons.galaxia.registry.outpost.recipe;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import gregtech.api.util.GTRecipe;

public record GT5RecipeRef(byte recipeMapOrdinal, int recipeIndex, long contentHash) {

    public static GT5RecipeRef of(GTRecipeMapId mapId, int recipeIndex, GTRecipe recipe) {
        long hash = computeContentHash(recipe);
        return new GT5RecipeRef((byte) mapId.ordinal(), recipeIndex, hash);
    }

    public static long computeContentHash(GTRecipe recipe) {
        return computeContentHash(
            recipe.mInputs,
            recipe.mOutputs,
            recipe.mFluidInputs,
            recipe.mFluidOutputs,
            recipe.mDuration,
            recipe.mEUt);
    }

    static long computeContentHash(ItemStack[] inputs, ItemStack[] outputs, FluidStack[] fluidInputs,
        FluidStack[] fluidOutputs, int duration, int eut) {
        long hash = 1L;
        hash = hashContentItems(hash, inputs);
        hash = hashContentItems(hash, outputs);
        hash = hashContentFluids(hash, fluidInputs);
        hash = hashContentFluids(hash, fluidOutputs);
        hash = hash * 31 + duration;
        hash = hash * 31 + eut;
        return hash;
    }

    private static long hashContentItems(long hash, ItemStack[] stacks) {
        if (stacks == null) return hash;
        for (ItemStack stack : stacks) {
            if (stack == null) continue;
            hash = hash * 31 + Item.getIdFromItem(stack.getItem());
            hash = hash * 31 + stack.getItemDamage();
            hash = hash * 31 + stack.stackSize;
        }
        return hash;
    }

    private static long hashContentFluids(long hash, FluidStack[] fluids) {
        if (fluids == null) return hash;
        for (FluidStack fluid : fluids) {
            if (fluid == null) continue;
            hash = hash * 31 + fluid.getFluidID();
            hash = hash * 31 + fluid.amount;
        }
        return hash;
    }

    @javax.annotation.Nullable
    public GTRecipe resolve() {
        GTRecipeMapId[] values = GTRecipeMapId.values();
        if (recipeMapOrdinal < 0 || recipeMapOrdinal >= values.length) return null;
        GTRecipeMapId mapId = values[recipeMapOrdinal];
        if (mapId == GTRecipeMapId.INVALID) return null;

        GTRecipe[] recipes = GTRecipeMapId.getRecipes(mapId);
        if (recipes == null) return null;

        if (recipeIndex >= 0 && recipeIndex < recipes.length) {
            GTRecipe candidate = recipes[recipeIndex];
            if (computeContentHash(candidate) == this.contentHash) {
                return candidate;
            }
        }

        // Hash mismatch or index out of range: scan entire map for matching hash
        for (GTRecipe r : recipes) {
            if (computeContentHash(r) == this.contentHash) {
                return r;
            }
        }

        return null;
    }
}
