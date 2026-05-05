package com.gtnewhorizons.galaxia.registry.outpost.recipe;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

/**
 * Self-contained recipe data snapshot. Created by the picker GUI when
 * the player selects a recipe. The execution pipeline reads directly
 * from this record — zero GT5 imports.
 *
 * <p>
 * {@link #contentHash} enables validation on server restart:
 * if the hash changed, the recipe was modified by a mod update.
 */
public record RecipeSnapshot(byte recipeMapOrdinal, int recipeIndex, long contentHash, ItemStack[] inputs,
    ItemStack[] outputs, FluidStack[] fluidInputs, FluidStack[] fluidOutputs, int[] outputChances,
    int[] fluidOutputChances, int duration, int eut) {

    public RecipeSnapshot {
        if (duration < 0) duration = 0;
        if (eut < 0) eut = 0;
    }

    public RecipeSnapshot(byte recipeMapOrdinal, int recipeIndex, long contentHash, ItemStack[] inputs,
        ItemStack[] outputs, FluidStack[] fluidInputs, FluidStack[] fluidOutputs, int duration, int eut) {
        this(
            recipeMapOrdinal,
            recipeIndex,
            contentHash,
            inputs,
            outputs,
            fluidInputs,
            fluidOutputs,
            null,
            null,
            duration,
            eut);
    }

    public RecipeSnapshot(byte recipeMapOrdinal, int recipeIndex, long contentHash, ItemStack[] inputs,
        ItemStack[] outputs, FluidStack[] fluidInputs, FluidStack[] fluidOutputs, int[] outputChances, int duration,
        int eut) {
        this(
            recipeMapOrdinal,
            recipeIndex,
            contentHash,
            inputs,
            outputs,
            fluidInputs,
            fluidOutputs,
            outputChances,
            null,
            duration,
            eut);
    }

    public RecipeSnapshot(byte recipeMapOrdinal, int recipeIndex, long contentHash, ItemStack[] inputs,
        ItemStack[] outputs, int duration, int eut) {
        this(recipeMapOrdinal, recipeIndex, contentHash, inputs, outputs, null, null, null, null, duration, eut);
    }

    /** Backward-compat: creates a snapshot without resolved inputs/outputs (loaded from old persistence). */
    public static RecipeSnapshot unresolved(byte recipeMapOrdinal, int recipeIndex, long contentHash) {
        return new RecipeSnapshot(recipeMapOrdinal, recipeIndex, contentHash, null, null, null, null, null, null, 0, 0);
    }

    public static RecipeSnapshot resolved(byte recipeMapOrdinal, int recipeIndex, ItemStack[] inputs,
        ItemStack[] outputs, FluidStack[] fluidInputs, FluidStack[] fluidOutputs, int duration, int eut) {
        return resolved(recipeMapOrdinal, recipeIndex, inputs, outputs, fluidInputs, fluidOutputs, null, duration, eut);
    }

    public static RecipeSnapshot resolved(byte recipeMapOrdinal, int recipeIndex, ItemStack[] inputs,
        ItemStack[] outputs, FluidStack[] fluidInputs, FluidStack[] fluidOutputs, int[] outputChances, int duration,
        int eut) {
        return resolved(
            recipeMapOrdinal,
            recipeIndex,
            inputs,
            outputs,
            fluidInputs,
            fluidOutputs,
            outputChances,
            null,
            duration,
            eut);
    }

    public static RecipeSnapshot resolved(byte recipeMapOrdinal, int recipeIndex, ItemStack[] inputs,
        ItemStack[] outputs, FluidStack[] fluidInputs, FluidStack[] fluidOutputs, int[] outputChances,
        int[] fluidOutputChances, int duration, int eut) {
        return new RecipeSnapshot(
            recipeMapOrdinal,
            recipeIndex,
            computeContentHash(
                inputs,
                outputs,
                fluidInputs,
                fluidOutputs,
                outputChances,
                fluidOutputChances,
                duration,
                eut),
            inputs,
            outputs,
            fluidInputs,
            fluidOutputs,
            outputChances,
            fluidOutputChances,
            duration,
            eut);
    }

    public static long computeContentHash(ItemStack[] inputs, ItemStack[] outputs, FluidStack[] fluidInputs,
        FluidStack[] fluidOutputs, int duration, int eut) {
        return computeContentHash(inputs, outputs, fluidInputs, fluidOutputs, null, duration, eut);
    }

    public static long computeContentHash(ItemStack[] inputs, ItemStack[] outputs, FluidStack[] fluidInputs,
        FluidStack[] fluidOutputs, int[] outputChances, int duration, int eut) {
        return computeContentHash(inputs, outputs, fluidInputs, fluidOutputs, outputChances, null, duration, eut);
    }

    public static long computeContentHash(ItemStack[] inputs, ItemStack[] outputs, FluidStack[] fluidInputs,
        FluidStack[] fluidOutputs, int[] outputChances, int[] fluidOutputChances, int duration, int eut) {
        long hash = 1L;
        hash = hashItems(hash, inputs);
        hash = hashItems(hash, outputs);
        hash = hashOutputChances(hash, outputChances);
        hash = hashFluids(hash, fluidInputs);
        hash = hashFluids(hash, fluidOutputs);
        hash = hashOutputChances(hash, fluidOutputChances);
        hash = hash * 31 + duration;
        hash = hash * 31 + eut;
        return hash;
    }

    public static long computeContentHash(ItemStack[] inputs, ItemStack[] outputs, int duration, int eut) {
        return computeContentHash(inputs, outputs, null, null, duration, eut);
    }

    private static long hashItems(long hash, ItemStack[] stacks) {
        if (stacks == null) return hash;
        for (ItemStack stack : stacks) {
            if (stack == null) continue;
            hash = hash * 31 + Item.getIdFromItem(stack.getItem());
            hash = hash * 31 + stack.getItemDamage();
            hash = hash * 31 + stack.stackSize;
        }
        return hash;
    }

    private static long hashOutputChances(long hash, int[] chances) {
        if (chances == null) return hash;
        for (int chance : chances) {
            hash = hash * 31 + chance;
        }
        return hash;
    }

    private static long hashFluids(long hash, FluidStack[] fluids) {
        if (fluids == null) return hash;
        for (FluidStack fluid : fluids) {
            if (fluid == null) continue;
            Fluid fluidType = fluidType(fluid);
            hash = hash * 31 + (fluidType != null ? fluidType.getName()
                .hashCode() : fluid.getFluidID());
            hash = hash * 31 + fluid.amount;
        }
        return hash;
    }

    private static Fluid fluidType(FluidStack stack) {
        try {
            return stack.getFluid();
        } catch (RuntimeException ignored) {
            try {
                var field = FluidStack.class.getDeclaredField("fluid");
                field.setAccessible(true);
                return (Fluid) field.get(stack);
            } catch (ReflectiveOperationException e) {
                return null;
            }
        }
    }
}
