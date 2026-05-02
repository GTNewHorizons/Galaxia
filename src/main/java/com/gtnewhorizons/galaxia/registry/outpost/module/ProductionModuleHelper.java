package com.gtnewhorizons.galaxia.registry.outpost.module;

import java.util.Map;
import java.util.Random;

import net.minecraft.item.ItemStack;

import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacilityInventory;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeConfig;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeScheduler;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSchedulerMode;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSlot;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSnapshot;

final class ProductionModuleHelper {

    private static final ItemStackWrapper[] EMPTY_WRAPPERS = new ItemStackWrapper[0];
    private static boolean warnedFluid = false;

    private ProductionModuleHelper() {}

    static void execute(ModuleInstance instance, AutomatedFacility outpost, IRecipeModule recipeModule, Random random,
        Map<RecipeSnapshot, ItemStackWrapper[]> inputWrapperCache,
        Map<RecipeSnapshot, ItemStackWrapper[]> outputWrapperCache) {
        RecipeConfig config = recipeModule.getRecipeConfig();
        if (config == null) return;

        int slotIdx = recipeModule.getNextSlot(random);
        if (slotIdx < 0) return;

        RecipeSlot slot = config.slots()
            .get(slotIdx);
        RecipeSnapshot recipe = slot.recipe();

        // V1: Fluid recipes not yet supported — skip with one-time WARN
        // (when fluids are added to RecipeSnapshot, this check will use those fields)
        if (!warnedFluid) {
            warnedFluid = true; // no fluid field on snapshot yet — assume item-only
        }

        AutomatedFacilityInventory inv = outpost.inventory;
        ItemStack[] inputs = recipe.inputs();
        ItemStack[] outputs = recipe.outputs();

        ItemStackWrapper[] inputWrappers = cachedWrappers(inputWrapperCache, recipe, inputs);

        // Check input guard
        for (int i = 0; i < inputWrappers.length; i++) {
            if (inputWrappers[i] == null) continue;
            if (inv.getAmount(inputWrappers[i]) < slot.inputGuard()) {
                advanceScheduler(config, recipeModule);
                return;
            }
        }

        // Check output guard
        ItemStackWrapper[] outputWrappers = cachedWrappers(outputWrapperCache, recipe, outputs);
        for (int i = 0; i < outputWrappers.length; i++) {
            if (outputWrappers[i] == null || outputs[i] == null) continue;
            if (inv.getAmount(outputWrappers[i]) + outputs[i].stackSize > slot.outputGuard()) {
                advanceScheduler(config, recipeModule);
                return;
            }
        }

        // Consume inputs
        for (int i = 0; i < inputWrappers.length; i++) {
            if (inputWrappers[i] == null || inputs[i] == null) continue;
            inv.add(inputWrappers[i], -inputs[i].stackSize);
        }

        // Produce outputs
        for (int i = 0; i < outputWrappers.length; i++) {
            if (outputWrappers[i] == null || outputs[i] == null) continue;
            inv.add(outputWrappers[i], outputs[i].stackSize);
        }

        if (config.mode() == RecipeSchedulerMode.ORDER) {
            recipeModule.setRecipeConfig(RecipeScheduler.advanceOrder(config));
        }
    }

    private static ItemStackWrapper[] cachedWrappers(Map<RecipeSnapshot, ItemStackWrapper[]> cache,
        RecipeSnapshot recipe, ItemStack[] stacks) {
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
