package com.gtnewhorizons.galaxia.registry.outpost.module;

import java.util.Map;
import java.util.Random;

import net.minecraft.item.ItemStack;

import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacilityInventory;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeConfig;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeScheduler;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSchedulerMode;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSlot;

import gregtech.api.util.GTRecipe;

final class ProductionModuleHelper {

    private static final ItemStackWrapper[] EMPTY_WRAPPERS = new ItemStackWrapper[0];
    private static boolean warnedFluid = false;

    private ProductionModuleHelper() {}

    static void execute(ModuleInstance instance, AutomatedFacility outpost, IRecipeModule recipeModule, Random random,
        Map<GTRecipe, ItemStackWrapper[]> inputWrapperCache, Map<GTRecipe, ItemStackWrapper[]> outputWrapperCache) {
        RecipeConfig config = recipeModule.getRecipeConfig();
        if (config == null) return;

        int slotIdx = recipeModule.getNextSlot(random);
        if (slotIdx < 0) return;

        RecipeSlot slot = config.slots()
            .get(slotIdx);

        GTRecipe recipe = slot.recipeRef()
            .resolve();
        if (recipe == null) {
            advanceScheduler(config, recipeModule);
            return;
        }

        // V1: Fluid recipes not yet supported — skip with one-time WARN
        if ((recipe.mFluidInputs != null && recipe.mFluidInputs.length > 0)
            || (recipe.mFluidOutputs != null && recipe.mFluidOutputs.length > 0)) {
            if (!warnedFluid) {
                Galaxia.LOG.warn(
                    "[Galaxia] Production module: fluid recipe processing not yet implemented; recipe will be skipped. (Fluid support deferred to Phase 8)");
                warnedFluid = true;
            }
            return;
        }

        AutomatedFacilityInventory inv = outpost.inventory;
        ItemStackWrapper[] inputWrappers = cachedWrappers(inputWrapperCache, recipe, recipe.mInputs);
        ItemStackWrapper[] outputWrappers = cachedWrappers(outputWrapperCache, recipe, recipe.mOutputs);

        // Check input guard
        boolean inputGuardMet = true;
        for (int i = 0; i < inputWrappers.length; i++) {
            if (inputWrappers[i] == null) continue;
            if (inv.getAmount(inputWrappers[i]) < slot.inputGuard()) {
                inputGuardMet = false;
                break;
            }
        }
        if (!inputGuardMet) {
            advanceScheduler(config, recipeModule);
            return;
        }

        // Check output guard
        boolean outputGuardMet = true;
        for (int i = 0; i < outputWrappers.length; i++) {
            if (outputWrappers[i] == null) continue;
            if (recipe.mOutputs[i] == null) continue;
            long current = inv.getAmount(outputWrappers[i]);
            if (current + recipe.mOutputs[i].stackSize > slot.outputGuard()) {
                outputGuardMet = false;
                break;
            }
        }
        if (!outputGuardMet) {
            advanceScheduler(config, recipeModule);
            return;
        }

        // Consume inputs
        for (int i = 0; i < inputWrappers.length; i++) {
            if (inputWrappers[i] == null) continue;
            boolean consumed;
            if (recipe.mInputChances == null || i >= recipe.mInputChances.length || recipe.mInputChances[i] >= 10000) {
                consumed = true;
            } else if (recipe.mInputChances[i] > 0) {
                consumed = random.nextInt(10000) < recipe.mInputChances[i];
            } else {
                consumed = false;
            }
            if (consumed && recipe.mInputs[i] != null) {
                inv.add(inputWrappers[i], -recipe.mInputs[i].stackSize);
            }
        }

        // Produce outputs
        for (int i = 0; i < outputWrappers.length; i++) {
            if (outputWrappers[i] == null) continue;
            boolean produced;
            if (recipe.mOutputChances == null || i >= recipe.mOutputChances.length
                || recipe.mOutputChances[i] >= 10000) {
                produced = true;
            } else if (recipe.mOutputChances[i] > 0) {
                produced = random.nextInt(10000) < recipe.mOutputChances[i];
            } else {
                produced = false;
            }
            if (produced && recipe.mOutputs[i] != null) {
                inv.add(outputWrappers[i], recipe.mOutputs[i].stackSize);
            }
        }

        // Advance scheduler in ORDER mode
        if (config.mode() == RecipeSchedulerMode.ORDER) {
            recipeModule.setRecipeConfig(RecipeScheduler.advanceOrder(config));
        }
    }

    private static ItemStackWrapper[] cachedWrappers(Map<GTRecipe, ItemStackWrapper[]> cache, GTRecipe recipe,
        ItemStack[] stacks) {
        ItemStackWrapper[] cached = cache.get(recipe);
        if (cached != null) return cached;
        if (stacks == null) {
            cache.put(recipe, EMPTY_WRAPPERS);
            return EMPTY_WRAPPERS;
        }
        ItemStackWrapper[] wrappers = new ItemStackWrapper[stacks.length];
        for (int i = 0; i < stacks.length; i++) {
            wrappers[i] = stacks[i] != null ? ItemStackWrapper.of(stacks[i]) : null;
        }
        cache.put(recipe, wrappers);
        return wrappers;
    }

    private static void advanceScheduler(RecipeConfig config, IRecipeModule recipeModule) {
        if (config.mode() == RecipeSchedulerMode.ORDER) {
            recipeModule.setRecipeConfig(RecipeScheduler.advanceOrder(config));
        }
    }
}
