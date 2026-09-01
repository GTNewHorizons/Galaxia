package com.gtnewhorizons.galaxia.registry.outpost.module;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.gtnewhorizons.galaxia.compat.recipe.GTRecipeChance;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.InventoryBounds;
import com.gtnewhorizons.galaxia.registry.outpost.InventoryExchange;
import com.gtnewhorizons.galaxia.registry.outpost.InventoryKey;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeBook;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeScheduleState;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSnapshot;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSnapshot.Resource;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.SavedRecipe;

public final class ProductionModuleHelper {

    private ProductionModuleHelper() {}

    public static void execute(ModuleInstance instance, CelestialAsset asset, Random random) {
        if (!(asset instanceof AutomatedFacility outpost)) {
            throw new IllegalStateException("This method should only be called by AutomatedFacilities");
        }
        RecipeBook book = outpost.recipeBook(instance);
        RecipeScheduleState scheduleState = outpost.recipeScheduleState(instance);
        RecipeBook.Selection selection = book.select(scheduleState, random)
            .orElse(null);
        if (selection == null) return;

        SavedRecipe slot = selection.recipe();
        RecipeSnapshot recipe = slot.recipe();
        Map<InventoryKey, Long> requiredInputs = totals(recipe.itemInputs(), recipe.fluidInputs());
        if (!allowsInputs(outpost, requiredInputs)) return;
        if (!matchesRequestAmount(outpost, slot, recipe.itemOutputs(), recipe.fluidOutputs())) return;

        Map<InventoryKey, Long> selectedOutputs = selectedOutputs(recipe.itemOutputs(), recipe.fluidOutputs(), random);
        if (!allowsOutputs(outpost, selectedOutputs)) return;
        if (!outpost.tryExchange(new InventoryExchange(requiredInputs, selectedOutputs))) return;

        outpost.installRecipeScheduleState(instance, book.advanceAfterSuccess(scheduleState, selection));
    }

    private static Map<InventoryKey, Long> totals(List<Resource> first, List<Resource> second) {
        if (first.isEmpty() && second.isEmpty()) return Map.of();
        Map<InventoryKey, Long> totals = new HashMap<>();
        merge(totals, first);
        merge(totals, second);
        return totals;
    }

    private static Map<InventoryKey, Long> selectedOutputs(List<Resource> first, List<Resource> second, Random random) {
        Map<InventoryKey, Long> selected = new HashMap<>();
        mergeSelected(selected, first, random);
        mergeSelected(selected, second, random);
        return selected.isEmpty() ? Map.of() : selected;
    }

    private static void merge(Map<InventoryKey, Long> totals, List<Resource> resources) {
        for (Resource resource : resources) totals.merge(resource.key(), resource.amount(), Long::sum);
    }

    private static void mergeSelected(Map<InventoryKey, Long> totals, List<Resource> resources, Random random) {
        for (Resource resource : resources) {
            if (GTRecipeChance.shouldProduce(resource, random)) {
                totals.merge(resource.key(), resource.amount(), Long::sum);
            }
        }
    }

    private static boolean allowsInputs(AutomatedFacility outpost, Map<InventoryKey, Long> requiredInputs) {
        for (Map.Entry<InventoryKey, Long> entry : requiredInputs.entrySet()) {
            InventoryBounds bound = outpost.getBound(entry.getKey());
            if (bound.hasLow() && !outpost.isAboveLow(entry.getKey(), entry.getValue())) return false;
        }
        return true;
    }

    private static boolean allowsOutputs(AutomatedFacility outpost, Map<InventoryKey, Long> selectedOutputs) {
        for (InventoryKey output : selectedOutputs.keySet()) {
            InventoryBounds bound = outpost.getBound(output);
            if (bound.hasUpper() && !outpost.isBelowUpper(output)) return false;
        }
        return true;
    }

    private static boolean matchesRequestAmount(AutomatedFacility outpost, SavedRecipe slot, List<Resource> itemOutputs,
        List<Resource> fluidOutputs) {
        long requestAmount = slot.requestAmount();
        if (requestAmount <= 0L || itemOutputs.isEmpty() && fluidOutputs.isEmpty()) return true;
        for (Resource output : itemOutputs) {
            if (outpost.amount(output.key()) < requestAmount) return true;
        }
        for (Resource output : fluidOutputs) {
            if (outpost.amount(output.key()) < requestAmount) return true;
        }
        return false;
    }
}
