package com.gtnewhorizons.galaxia.testing;

import java.lang.reflect.Field;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import gregtech.api.util.GTRecipe;
import sun.misc.Unsafe;

public final class TestGTRecipes {

    private static final Unsafe UNSAFE = unsafe();

    private TestGTRecipes() {}

    public static GTRecipe recipe(ItemStack[] itemInputs, ItemStack[] itemOutputs, FluidStack[] fluidInputs,
        FluidStack[] fluidOutputs, int[] outputChances, int duration, int eut) {
        try {
            GTRecipe recipe = (GTRecipe) UNSAFE.allocateInstance(GTRecipe.class);
            recipe.mInputs = itemInputs;
            recipe.mOutputs = itemOutputs;
            recipe.mOutputChances = outputChances;
            recipe.mFluidInputs = fluidInputs;
            recipe.mFluidOutputs = fluidOutputs;
            recipe.mDuration = duration;
            recipe.mEUt = eut;
            recipe.mHidden = false;
            recipe.mFakeRecipe = false;
            return recipe;
        } catch (InstantiationException e) {
            throw new AssertionError(e);
        }
    }

    private static Unsafe unsafe() {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            return (Unsafe) field.get(null);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
