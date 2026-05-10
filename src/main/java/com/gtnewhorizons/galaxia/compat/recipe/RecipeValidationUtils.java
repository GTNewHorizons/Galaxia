package com.gtnewhorizons.galaxia.compat.recipe;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

/**
 * Utility methods for recipe validation to reduce boilerplate null-checking
 * and provide type-safe operations on recipe components.
 */
public final class RecipeValidationUtils {

    private RecipeValidationUtils() {}

    /**
     * Checks if any item stack in an array is non-null.
     * @param stacks the item stack array to check
     * @return true if at least one non-null stack exists, false otherwise or if array is null
     */
    public static boolean hasAnyItem(ItemStack[] stacks) {
        if (stacks == null) return false;
        for (ItemStack stack : stacks) if (stack != null) return true;
        return false;
    }

    /**
     * Checks if any fluid stack in an array is non-null.
     * @param stacks the fluid stack array to check
     * @return true if at least one non-null stack exists, false otherwise or if array is null
     */
    public static boolean hasAnyFluid(FluidStack[] stacks) {
        if (stacks == null) return false;
        for (FluidStack stack : stacks) if (stack != null) return true;
        return false;
    }

    /**
     * Checks if any component (item or fluid) in the provided arrays is present.
     * @return true if any component is non-null
     */
    public static boolean hasAnyComponent(ItemStack[] itemInputs, ItemStack[] itemOutputs,
        FluidStack[] fluidInputs, FluidStack[] fluidOutputs) {
        return hasAnyItem(itemInputs) || hasAnyItem(itemOutputs)
            || hasAnyFluid(fluidInputs) || hasAnyFluid(fluidOutputs);
    }

    /**
     * Safely gets the length of an array, returning 0 if the array is null.
     */
    public static int getLength(Object[] array) {
        return array == null ? 0 : array.length;
    }

    /**
     * Safely checks if an array is null or empty.
     */
    public static boolean isNullOrEmpty(Object[] array) {
        return array == null || array.length == 0;
    }
}
