package com.gtnewhorizons.galaxia.registry.outpost.recipe;

import javax.annotation.Nullable;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

/**
 * Stable reference to a GT5 recipe, identified by recipe-map ordinal,
 * recipe index, and content hash. No direct GT5 imports — resolution
 * happens through {@code GT5RecipeBridge} which is loaded conditionally.
 */
public record GT5RecipeRef(byte recipeMapOrdinal, int recipeIndex, long contentHash) {

    public static GT5RecipeRef of(int mapIdOrdinal, int recipeIndex, Object gtRecipe) {
        long hash = GT5RecipeBridge.computeContentHash(gtRecipe);
        return new GT5RecipeRef((byte) mapIdOrdinal, recipeIndex, hash);
    }

    /** Returns the resolved GTRecipe Object, or null if GT5 is absent. */
    @Nullable
    public Object resolve() {
        return GT5RecipeBridge.resolve(this);
    }

    // ── Content hash helpers (no GT5 deps) ──

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
}
