package com.gtnewhorizons.galaxia.registry.outpost.module;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizons.galaxia.compat.recipe.GTRecipeChance;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacilityInventory;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeConfig;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeScheduler;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSchedulerMode;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSnapshot;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.SavedRecipe;

public final class ProductionModuleHelper {

    private static final ItemStackWrapper[] EMPTY_WRAPPERS = new ItemStackWrapper[0];

    private ProductionModuleHelper() {}

    public static void execute(ModuleInstance instance, AutomatedFacility outpost, IRecipeModule recipeModule,
        Random random, Map<RecipeSnapshot, ItemStackWrapper[]> inputWrapperCache,
        Map<RecipeSnapshot, ItemStackWrapper[]> outputWrapperCache) {
        RecipeConfig config = recipeModule.getRecipeConfig();
        if (config == null) return;

        int slotIdx = recipeModule.getNextSlot(random);
        if (slotIdx < 0) return;

        SavedRecipe slot = config.savedRecipes()
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

        Map<ItemStackWrapper, Long> requiredInputs = requiredInputs(inputWrappers, inputs);
        Map<String, Long> requiredFluidInputs = requiredFluidInputs(fluidInputs);
        if (!hasRequiredInputs(inv, requiredInputs, requiredFluidInputs)
            || !allowsInputs(inv, requiredInputs, requiredFluidInputs)) {
            advanceScheduler(config, recipeModule);
            return;
        }

        ItemStackWrapper[] outputWrappers = cachedWrappers(outputWrapperCache, recipe, outputs);
        if (!matchesRequestAmount(inv, slot, outputWrappers, fluidOutputs)) {
            advanceScheduler(config, recipeModule);
            return;
        }
        SelectedItemOutputs selectedItemOutputs = selectedOutputs(outputWrappers, outputs, outputChances, random);
        SelectedFluidOutputs selectedFluidOutputs = selectedFluidOutputs(fluidOutputs, fluidOutputChances, random);
        if (!allowsOutputs(inv, selectedItemOutputs, selectedFluidOutputs)) {
            advanceScheduler(config, recipeModule);
            return;
        }
        if (!canFitSelectedItemOutputs(outpost, selectedItemOutputs.totals(), requiredInputs)) {
            advanceScheduler(config, recipeModule);
            return;
        }

        // Consume inputs
        for (Map.Entry<ItemStackWrapper, Long> e : requiredInputs.entrySet()) {
            outpost.addInventory(e.getKey(), -e.getValue());
        }

        if (fluidInputs != null) {
            for (FluidStack fluid : fluidInputs) {
                String fluidName = fluidName(fluid);
                if (fluidName != null) inv.addFluid(fluidName, -fluid.amount);
            }
        }

        // Produce outputs
        for (Map.Entry<ItemStackWrapper, Long> e : selectedItemOutputs.totals()
            .entrySet()) {
            outpost.insertInventory(e.getKey(), e.getValue());
        }

        if (fluidOutputs != null) {
            for (Map.Entry<String, Long> e : selectedFluidOutputs.totals()
                .entrySet()) {
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
        if (stacks == null || stacks.length == 0) return Map.of();
        Map<ItemStackWrapper, Long> required = new HashMap<>();
        for (int i = 0; i < wrappers.length && i < stacks.length; i++) {
            if (wrappers[i] == null || stacks[i] == null || stacks[i].stackSize <= 0) continue;
            required.merge(wrappers[i], (long) stacks[i].stackSize, Long::sum);
        }
        return required.isEmpty() ? Map.of() : required;
    }

    private static Map<String, Long> requiredFluidInputs(FluidStack[] stacks) {
        if (stacks == null || stacks.length == 0) return Map.of();
        Map<String, Long> required = new HashMap<>();
        for (FluidStack stack : stacks) {
            String fluidName = fluidName(stack);
            if (fluidName == null || stack.amount <= 0) continue;
            required.merge(fluidName, (long) stack.amount, Long::sum);
        }
        return required.isEmpty() ? Map.of() : required;
    }

    private static long totalAmount(Map<?, Long> amounts) {
        long total = 0L;
        for (long amount : amounts.values()) {
            total += amount;
        }
        return total;
    }

    private static boolean hasRequiredInputs(AutomatedFacilityInventory inv, Map<ItemStackWrapper, Long> itemInputs,
        Map<String, Long> fluidInputs) {
        for (Map.Entry<ItemStackWrapper, Long> entry : itemInputs.entrySet()) {
            if (inv.getAmount(entry.getKey()) < entry.getValue()) return false;
        }
        for (Map.Entry<String, Long> entry : fluidInputs.entrySet()) {
            if (inv.getFluidAmount(entry.getKey()) < entry.getValue()) return false;
        }
        return true;
    }

    private static boolean canFitSelectedItemOutputs(AutomatedFacility outpost, Map<ItemStackWrapper, Long> outputs,
        Map<ItemStackWrapper, Long> inputs) {
        long outputAmount = totalAmount(outputs);
        if (outputAmount <= 0L) return true;
        long usedAfterInputs = Math.max(0L, outpost.inventory.totalItems() - totalAmount(inputs));
        return usedAfterInputs + outputAmount <= outpost.itemInventoryCapacity();
    }

    private static boolean allowsInputs(AutomatedFacilityInventory inv, Map<ItemStackWrapper, Long> requiredInputs,
        Map<String, Long> requiredFluidInputs) {
        for (Map.Entry<ItemStackWrapper, Long> entry : requiredInputs.entrySet()) {
            if (inv.hasItemLowerBound(entry.getKey()) && !inv.keepsItemLowerBoundAfterConsume(
                entry.getKey(),
                entry.getValue(),
                inv.itemLowerBoundOrDefault(entry.getKey()))) {
                return false;
            }
        }
        for (Map.Entry<String, Long> entry : requiredFluidInputs.entrySet()) {
            if (inv.hasFluidLowerBound(entry.getKey()) && !inv.keepsFluidLowerBoundAfterConsume(
                entry.getKey(),
                entry.getValue(),
                inv.fluidLowerBoundOrDefault(entry.getKey()))) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchesRequestAmount(AutomatedFacilityInventory inv, SavedRecipe slot,
        ItemStackWrapper[] outputWrappers, FluidStack[] fluidOutputs) {
        long requestAmount = slot.requestAmount();
        if (requestAmount <= 0L) return true;
        boolean hasOutputs = false;
        for (ItemStackWrapper output : outputWrappers) {
            if (output == null) continue;
            hasOutputs = true;
            if (inv.getAmount(output) < requestAmount) return true;
        }
        if (fluidOutputs != null) {
            for (FluidStack stack : fluidOutputs) {
                String fluidName = fluidName(stack);
                if (fluidName == null) continue;
                hasOutputs = true;
                if (inv.getFluidAmount(fluidName) < requestAmount) return true;
            }
        }
        return !hasOutputs;
    }

    private static boolean allowsOutputs(AutomatedFacilityInventory inv, SelectedItemOutputs selectedItemOutputs,
        SelectedFluidOutputs selectedFluidOutputs) {
        for (Map.Entry<ItemStackWrapper, Long> entry : selectedItemOutputs.totals()
            .entrySet()) {
            if (entry.getValue() <= 0L) continue;
            if (inv.hasItemUpperBound(entry.getKey())
                && !inv.isItemBelowUpperBound(entry.getKey(), inv.itemUpperBoundOrDefault(entry.getKey()))) {
                return false;
            }
        }
        for (Map.Entry<String, Long> entry : selectedFluidOutputs.totals()
            .entrySet()) {
            if (entry.getValue() <= 0L) continue;
            if (inv.hasFluidUpperBound(entry.getKey())
                && !inv.isFluidBelowUpperBound(entry.getKey(), inv.fluidUpperBoundOrDefault(entry.getKey()))) {
                return false;
            }
        }
        return true;
    }

    private static SelectedItemOutputs selectedOutputs(ItemStackWrapper[] wrappers, ItemStack[] stacks, int[] chances,
        Random random) {
        if (stacks == null || stacks.length == 0) return SelectedItemOutputs.empty();
        Map<ItemStackWrapper, Long> selected = new HashMap<>();
        long[] slotAmounts = new long[stacks.length];
        for (int i = 0; i < wrappers.length && i < stacks.length; i++) {
            if (wrappers[i] == null || stacks[i] == null || stacks[i].stackSize <= 0) continue;
            if (!shouldProduceOutput(chances, i, random)) continue;
            slotAmounts[i] = stacks[i].stackSize;
            selected.merge(wrappers[i], (long) stacks[i].stackSize, Long::sum);
        }
        return new SelectedItemOutputs(selected.isEmpty() ? Map.of() : selected, slotAmounts);
    }

    private static SelectedFluidOutputs selectedFluidOutputs(FluidStack[] stacks, int[] chances, Random random) {
        if (stacks == null || stacks.length == 0) return SelectedFluidOutputs.empty();
        Map<String, Long> selected = new HashMap<>();
        String[] slotFluidNames = new String[stacks.length];
        long[] slotAmounts = new long[stacks.length];
        for (int i = 0; i < stacks.length; i++) {
            FluidStack stack = stacks[i];
            String fluidName = fluidName(stack);
            if (fluidName == null || stack.amount <= 0) continue;
            if (!shouldProduceOutput(chances, i, random)) continue;
            slotFluidNames[i] = fluidName;
            slotAmounts[i] = stack.amount;
            selected.merge(fluidName, (long) stack.amount, Long::sum);
        }
        return new SelectedFluidOutputs(selected.isEmpty() ? Map.of() : selected, slotFluidNames, slotAmounts);
    }

    private static ItemStackWrapper wrapperAt(ItemStackWrapper[] wrappers, int index) {
        return index >= 0 && index < wrappers.length ? wrappers[index] : null;
    }

    private static String fluidNameAt(FluidStack[] stacks, int index) {
        if (stacks == null || index < 0 || index >= stacks.length) return null;
        return fluidName(stacks[index]);
    }

    private static boolean shouldProduceOutput(int[] chances, int index, Random random) {
        return GTRecipeChance.shouldProduce(chances, index, random);
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

    private record SelectedItemOutputs(Map<ItemStackWrapper, Long> totals, long[] slotAmounts) {

        private static SelectedItemOutputs empty() {
            return new SelectedItemOutputs(Map.of(), new long[0]);
        }

        private long slotAmount(int slotIndex) {
            return slotIndex >= 0 && slotIndex < slotAmounts.length ? slotAmounts[slotIndex] : 0L;
        }
    }

    private record SelectedFluidOutputs(Map<String, Long> totals, String[] slotFluidNames, long[] slotAmounts) {

        private static SelectedFluidOutputs empty() {
            return new SelectedFluidOutputs(Map.of(), new String[0], new long[0]);
        }

        private String slotFluidName(int slotIndex) {
            return slotIndex >= 0 && slotIndex < slotFluidNames.length ? slotFluidNames[slotIndex] : null;
        }

        private long slotAmount(int slotIndex) {
            return slotIndex >= 0 && slotIndex < slotAmounts.length ? slotAmounts[slotIndex] : 0L;
        }
    }
}
