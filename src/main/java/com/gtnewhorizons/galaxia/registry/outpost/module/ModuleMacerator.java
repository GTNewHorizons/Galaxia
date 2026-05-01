package com.gtnewhorizons.galaxia.registry.outpost.module;

import java.util.Map;
import java.util.Random;
import java.util.WeakHashMap;

import net.minecraft.item.ItemStack;

import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacilityInventory;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeConfig;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeScheduler;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSchedulerMode;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSlot;

import gregtech.api.recipe.RecipeMaps;
import gregtech.api.util.GTRecipe;

public class ModuleMacerator implements ModuleComponent, IParallelModule, IRecipeModule {

    private byte parallel = 1;
    private RecipeConfig recipeConfig;
    private final Random random = new Random();
    // Per-recipe cache to avoid ItemStackWrapper allocations on hot path
    private final Map<GTRecipe, ItemStackWrapper[]> inputWrapperCache = new WeakHashMap<>();
    private final Map<GTRecipe, ItemStackWrapper[]> outputWrapperCache = new WeakHashMap<>();

    @Override
    public byte getParallel() {
        return parallel;
    }

    @Override
    public void setParallel(byte parallel) {
        this.parallel = parallel;
    }

    @Override
    public gregtech.api.recipe.RecipeMap<?> getRecipeMap() {
        return RecipeMaps.maceratorRecipes;
    }

    @Override
    public RecipeConfig getRecipeConfig() {
        return recipeConfig;
    }

    @Override
    public void setRecipeConfig(RecipeConfig config) {
        this.recipeConfig = config;
    }

    private static ItemStackWrapper[] cachedWrappers(Map<GTRecipe, ItemStackWrapper[]> cache, GTRecipe recipe,
        ItemStack[] stacks) {
        ItemStackWrapper[] cached = cache.get(recipe);
        if (cached != null) return cached;
        if (stacks == null) {
            cached = EMPTY_WRAPPERS;
            cache.put(recipe, cached);
            return cached;
        }
        ItemStackWrapper[] wrappers = new ItemStackWrapper[stacks.length];
        for (int i = 0; i < stacks.length; i++) {
            wrappers[i] = stacks[i] != null ? ItemStackWrapper.of(stacks[i]) : null;
        }
        cache.put(recipe, wrappers);
        return wrappers;
    }

    private static final ItemStackWrapper[] EMPTY_WRAPPERS = new ItemStackWrapper[0];

    public static void processRecipe(ModuleInstance instance, AutomatedFacility outpost) {
        ModuleMacerator macerator = (ModuleMacerator) instance.component();
        RecipeConfig config = macerator.getRecipeConfig();
        if (config == null) return;

        int slotIdx = macerator.getNextSlot(macerator.random);
        if (slotIdx < 0) return;

        RecipeSlot slot = config.slots()
            .get(slotIdx);

        GTRecipe recipe = slot.recipeRef()
            .resolve();
        if (recipe == null) {
            advanceScheduler(config, macerator);
            return;
        }

        AutomatedFacilityInventory inv = outpost.inventory;
        ItemStackWrapper[] inputWrappers = cachedWrappers(macerator.inputWrapperCache, recipe, recipe.mInputs);
        ItemStackWrapper[] outputWrappers = cachedWrappers(macerator.outputWrapperCache, recipe, recipe.mOutputs);

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
            advanceScheduler(config, macerator);
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
            advanceScheduler(config, macerator);
            return;
        }

        // Consume inputs
        for (int i = 0; i < inputWrappers.length; i++) {
            if (inputWrappers[i] == null) continue;
            boolean consumed;
            if (recipe.mInputChances == null || i >= recipe.mInputChances.length || recipe.mInputChances[i] >= 10000) {
                consumed = true;
            } else if (recipe.mInputChances[i] > 0) {
                consumed = macerator.random.nextInt(10000) < recipe.mInputChances[i];
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
                produced = macerator.random.nextInt(10000) < recipe.mOutputChances[i];
            } else {
                produced = false;
            }
            if (produced && recipe.mOutputs[i] != null) {
                inv.add(outputWrappers[i], recipe.mOutputs[i].stackSize);
            }
        }

        // Advance scheduler in ORDER mode
        if (config.mode() == RecipeSchedulerMode.ORDER) {
            macerator.setRecipeConfig(RecipeScheduler.advanceOrder(config));
        }
    }

    private static void advanceScheduler(RecipeConfig config, ModuleMacerator macerator) {
        if (config.mode() == RecipeSchedulerMode.ORDER) {
            macerator.setRecipeConfig(RecipeScheduler.advanceOrder(config));
        }
    }
}
