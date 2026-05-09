package com.gtnewhorizons.galaxia.registry.outpost.module;

import java.util.LinkedHashMap;
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

public final class ProductionModuleHelper {

    private static final ItemStackWrapper[] EMPTY_WRAPPERS = new ItemStackWrapper[0];
    private static final int GUARANTEED_OUTPUT_CHANCE = 10_000;

    private ProductionModuleHelper() {}

    public static void execute(ModuleInstance instance, AutomatedFacility outpost, IRecipeModule recipeModule,
        Random random, Map<RecipeSnapshot, ItemStackWrapper[]> inputWrapperCache,
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
        int[] outputChances = recipe.outputChances();
        FluidStack[] fluidInputs = recipe.fluidInputs();
        FluidStack[] fluidOutputs = recipe.fluidOutputs();
        int[] fluidOutputChances = recipe.fluidOutputChances();

        ItemStackWrapper[] inputWrappers = cachedWrappers(inputWrapperCache, recipe, inputs);

        // Check input guard
        Map<ItemStackWrapper, Long> requiredInputs = requiredInputs(inputWrappers, inputs);
        for (Map.Entry<ItemStackWrapper, Long> e : requiredInputs.entrySet()) {
            if (inv.getAmount(e.getKey()) < e.getValue() + slot.inputGuard()) {
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

        ItemStackWrapper[] outputWrappers = cachedWrappers(outputWrapperCache, recipe, outputs);

        // Output guard is a recipe start threshold, not a hard post-production cap.
        if (!allowsItemOutputs(inv, outputWrappers, outputs, slot.outputGuard())) {
            advanceScheduler(config, recipeModule);
            return;
        }
        if (!allowsFluidOutputs(inv, fluidOutputs, slot.outputGuard())) {
            advanceScheduler(config, recipeModule);
            return;
        }

        // Consume inputs
        for (Map.Entry<ItemStackWrapper, Long> e : requiredInputs.entrySet()) {
            inv.add(e.getKey(), -e.getValue());
        }

        if (fluidInputs != null) {
            for (FluidStack fluid : fluidInputs) {
                String fluidName = fluidName(fluid);
                if (fluidName != null) inv.addFluid(fluidName, -fluid.amount);
            }
        }

        Map<ItemStackWrapper, Long> selectedOutputs = selectedOutputs(outputWrappers, outputs, outputChances, random);
        Map<String, Long> selectedFluidOutputs = selectedFluidOutputs(fluidOutputs, fluidOutputChances, random);

        // Produce outputs
        for (Map.Entry<ItemStackWrapper, Long> e : selectedOutputs.entrySet()) {
            inv.add(e.getKey(), e.getValue());
        }

        if (fluidOutputs != null) {
            for (Map.Entry<String, Long> e : selectedFluidOutputs.entrySet()) {
                inv.addFluid(e.getKey(), e.getValue());
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

    private static Map<ItemStackWrapper, Long> requiredInputs(ItemStackWrapper[] wrappers, ItemStack[] stacks) {
        Map<ItemStackWrapper, Long> required = new LinkedHashMap<>();
        if (stacks == null) return required;
        for (int i = 0; i < wrappers.length && i < stacks.length; i++) {
            if (wrappers[i] == null || stacks[i] == null || stacks[i].stackSize <= 0) continue;
            required.merge(wrappers[i], (long) stacks[i].stackSize, Long::sum);
        }
        return required;
    }

    private static boolean allowsItemOutputs(AutomatedFacilityInventory inv, ItemStackWrapper[] wrappers,
        ItemStack[] stacks, int outputGuard) {
        if (stacks == null) return true;
        for (int i = 0; i < wrappers.length && i < stacks.length; i++) {
            if (wrappers[i] == null || stacks[i] == null || stacks[i].stackSize <= 0) continue;
            if (inv.getAmount(wrappers[i]) >= outputGuard) return false;
        }
        return true;
    }

    private static boolean allowsFluidOutputs(AutomatedFacilityInventory inv, FluidStack[] stacks, int outputGuard) {
        if (stacks == null) return true;
        for (FluidStack stack : stacks) {
            String fluidName = fluidName(stack);
            if (fluidName == null || stack.amount <= 0) continue;
            if (inv.getFluidAmount(fluidName) >= outputGuard) return false;
        }
        return true;
    }

    private static Map<ItemStackWrapper, Long> selectedOutputs(ItemStackWrapper[] wrappers, ItemStack[] stacks,
        int[] chances, Random random) {
        Map<ItemStackWrapper, Long> selected = new LinkedHashMap<>();
        if (stacks == null) return selected;
        for (int i = 0; i < wrappers.length && i < stacks.length; i++) {
            if (wrappers[i] == null || stacks[i] == null || stacks[i].stackSize <= 0) continue;
            if (!shouldProduceOutput(chances, i, random)) continue;
            selected.merge(wrappers[i], (long) stacks[i].stackSize, Long::sum);
        }
        return selected;
    }

    private static Map<String, Long> selectedFluidOutputs(FluidStack[] stacks, int[] chances, Random random) {
        Map<String, Long> selected = new LinkedHashMap<>();
        if (stacks == null) return selected;
        for (int i = 0; i < stacks.length; i++) {
            FluidStack stack = stacks[i];
            String fluidName = fluidName(stack);
            if (fluidName == null || stack.amount <= 0) continue;
            if (!shouldProduceOutput(chances, i, random)) continue;
            selected.merge(fluidName, (long) stack.amount, Long::sum);
        }
        return selected;
    }

    private static boolean shouldProduceOutput(int[] chances, int index, Random random) {
        if (chances == null || index >= chances.length) return true;
        int chance = chances[index];
        if (chance < 0) return true;
        if (chance == 0) return false;
        if (chance >= GUARANTEED_OUTPUT_CHANCE) return true;
        return random.nextInt(GUARANTEED_OUTPUT_CHANCE) < chance;
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
