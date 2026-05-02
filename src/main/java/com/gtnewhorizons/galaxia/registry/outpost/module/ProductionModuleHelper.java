package com.gtnewhorizons.galaxia.registry.outpost.module;

import java.util.Map;
import java.util.Random;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

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

        AutomatedFacilityInventory inv = outpost.inventory;
        ItemStack[] inputs = recipe.inputs();
        ItemStack[] outputs = recipe.outputs();
        FluidStack[] fluidInputs = recipe.fluidInputs();
        FluidStack[] fluidOutputs = recipe.fluidOutputs();

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

        for (FluidStack fluid : fluidInputs == null ? new FluidStack[0] : fluidInputs) {
            String fluidName = fluidName(fluid);
            if (fluidName == null) continue;
            if (inv.getFluidAmount(fluidName) < fluid.amount) {
                advanceScheduler(config, recipeModule);
                return;
            }
        }

        for (FluidStack fluid : fluidOutputs == null ? new FluidStack[0] : fluidOutputs) {
            String fluidName = fluidName(fluid);
            if (fluidName == null) continue;
            if (inv.getFluidAmount(fluidName) + fluid.amount > slot.outputGuard()) {
                advanceScheduler(config, recipeModule);
                return;
            }
        }

        // Consume inputs
        for (int i = 0; i < inputWrappers.length; i++) {
            if (inputWrappers[i] == null || inputs[i] == null) continue;
            inv.add(inputWrappers[i], -inputs[i].stackSize);
        }

        if (fluidInputs != null) {
            for (FluidStack fluid : fluidInputs) {
                String fluidName = fluidName(fluid);
                if (fluidName != null) inv.addFluid(fluidName, -fluid.amount);
            }
        }

        // Produce outputs
        for (int i = 0; i < outputWrappers.length; i++) {
            if (outputWrappers[i] == null || outputs[i] == null) continue;
            inv.add(outputWrappers[i], outputs[i].stackSize);
        }

        if (fluidOutputs != null) {
            for (FluidStack fluid : fluidOutputs) {
                String fluidName = fluidName(fluid);
                if (fluidName != null) inv.addFluid(fluidName, fluid.amount);
            }
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
                var field = FluidStack.class.getDeclaredField("fluid");
                field.setAccessible(true);
                return (Fluid) field.get(stack);
            } catch (ReflectiveOperationException e) {
                return null;
            }
        }
    }
}
