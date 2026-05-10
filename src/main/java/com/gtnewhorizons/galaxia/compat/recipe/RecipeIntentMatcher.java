package com.gtnewhorizons.galaxia.compat.recipe;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.oredict.OreDictionary;

import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSnapshot;

import gregtech.api.util.GTRecipe;

public final class RecipeIntentMatcher {

    public enum Status {
        NO_INPUT,
        NO_MATCH,
        MULTIPLE_MATCHES,
        SINGLE_MATCH
    }

    public record Result(Status status, int matchCount, int recipeIndex, @Nullable GTRecipe recipe,
        @Nullable RecipeSnapshot snapshot) {}

    private RecipeIntentMatcher() {}

    public static Result match(@Nullable GTRecipeMapId mapId, @Nullable GTRecipe[] recipes,
        @Nullable ItemStack[] itemInputs, @Nullable ItemStack[] itemOutputs, @Nullable FluidStack[] fluidInputs,
        @Nullable FluidStack[] fluidOutputs) {
        if (!hasAnyHardSlot(itemInputs, itemOutputs, fluidInputs, fluidOutputs)) {
            return new Result(Status.NO_INPUT, 0, -1, null, null);
        }
        if (recipes == null || recipes.length == 0) {
            return new Result(Status.NO_MATCH, 0, -1, null, null);
        }

        List<Integer> matches = new ArrayList<>();
        for (int i = 0; i < recipes.length; i++) {
            GTRecipe recipe = recipes[i];
            if (recipe == null || recipe.mHidden || recipe.mFakeRecipe) continue;
            if (!matchesItems(itemInputs, recipe.mInputs)) continue;
            if (!matchesItems(itemOutputs, recipe.mOutputs)) continue;
            if (!matchesFluids(fluidInputs, recipe.mFluidInputs)) continue;
            if (!matchesFluids(fluidOutputs, recipe.mFluidOutputs)) continue;
            matches.add(i);
        }

        if (matches.isEmpty()) {
            return new Result(Status.NO_MATCH, 0, -1, null, null);
        }
        if (matches.size() > 1) {
            return new Result(Status.MULTIPLE_MATCHES, matches.size(), -1, null, null);
        }

        int recipeIndex = matches.get(0);
        GTRecipe recipe = recipes[recipeIndex];
        byte mapOrdinal = (byte) (mapId != null ? mapId.ordinal() : GTRecipeMapId.INVALID.ordinal());
        return new Result(
            Status.SINGLE_MATCH,
            1,
            recipeIndex,
            recipe,
            RecipeSnapshot.resolved(
                mapOrdinal,
                recipeIndex,
                recipe.mInputs,
                recipe.mOutputs,
                recipe.mFluidInputs,
                recipe.mFluidOutputs,
                recipe.mOutputChances,
                recipe.mFluidOutputChances,
                recipe.mDuration,
                recipe.mEUt));
    }

    private static boolean hasAnyHardSlot(ItemStack[] itemInputs, ItemStack[] itemOutputs, FluidStack[] fluidInputs,
        FluidStack[] fluidOutputs) {
        return RecipeValidationUtils.hasAnyComponent(itemInputs, itemOutputs, fluidInputs, fluidOutputs);
    }

    // Removed: hasAnyItem and hasAnyFluid - use RecipeValidationUtils instead

    private static boolean matchesItems(ItemStack[] hardSlots, ItemStack[] recipeStacks) {
        if (RecipeValidationUtils.isNullOrEmpty(hardSlots)) return true;
        boolean[] used = new boolean[RecipeValidationUtils.getLength(recipeStacks)];
        for (ItemStack hard : hardSlots) {
            if (hard == null) continue;
            int matchIndex = findMatchingItem(hard, recipeStacks, used);
            if (matchIndex < 0) return false;
            used[matchIndex] = true;
        }
        return true;
    }

    private static int findMatchingItem(ItemStack hard, ItemStack[] recipeStacks, boolean[] used) {
        if (recipeStacks == null) return -1;
        for (int i = 0; i < recipeStacks.length; i++) {
            if (used[i] || !matchesItem(hard, recipeStacks[i])) continue;
            return i;
        }
        return -1;
    }

    private static boolean matchesItem(ItemStack hard, ItemStack recipeStack) {
        if (hard == null || recipeStack == null) return false;
        if (hard.getItem() != recipeStack.getItem()) return false;
        int hardMeta = hard.getItemDamage();
        int recipeMeta = recipeStack.getItemDamage();
        return hardMeta == recipeMeta || hardMeta == OreDictionary.WILDCARD_VALUE
            || recipeMeta == OreDictionary.WILDCARD_VALUE;
    }

    private static boolean matchesFluids(FluidStack[] hardSlots, FluidStack[] recipeStacks) {
        if (RecipeValidationUtils.isNullOrEmpty(hardSlots)) return true;
        boolean[] used = new boolean[RecipeValidationUtils.getLength(recipeStacks)];
        for (FluidStack hard : hardSlots) {
            if (hard == null) continue;
            int matchIndex = findMatchingFluid(hard, recipeStacks, used);
            if (matchIndex < 0) return false;
            used[matchIndex] = true;
        }
        return true;
    }

    private static int findMatchingFluid(FluidStack hard, FluidStack[] recipeStacks, boolean[] used) {
        if (recipeStacks == null) return -1;
        String hardName = fluidName(hard);
        if (hardName == null) return -1;
        for (int i = 0; i < recipeStacks.length; i++) {
            if (used[i]) continue;
            String recipeName = fluidName(recipeStacks[i]);
            if (hardName.equals(recipeName)) return i;
        }
        return -1;
    }

    private static String fluidName(FluidStack stack) {
        if (stack == null) return null;
        Fluid fluid = fluidType(stack);
        return fluid != null ? fluid.getName() : null;
    }

    private static Fluid fluidType(FluidStack stack) {
        try {
            return stack.getFluid();
        } catch (RuntimeException ignored) {
            try {
                Field field = FluidStack.class.getDeclaredField("fluid");
                field.setAccessible(true);
                return (Fluid) field.get(stack);
            } catch (ReflectiveOperationException e) {
                return null;
            }
        }
    }
}
