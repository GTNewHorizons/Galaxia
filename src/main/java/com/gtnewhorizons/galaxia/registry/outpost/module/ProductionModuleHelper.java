package com.gtnewhorizons.galaxia.registry.outpost.module;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.interfaces.IModuleComponent;
import com.gtnewhorizons.galaxia.registry.interfaces.TieredModuleComponent;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.InventoryBounds;
import com.gtnewhorizons.galaxia.registry.outpost.InventoryKey;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeBook;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSnapshot;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSnapshot.Resource;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.SavedRecipe;

public final class ProductionModuleHelper {

    private ProductionModuleHelper() {}

    static IModuleComponent createRuntime() {
        return new RecipeRuntime();
    }

    private static void execute(ModuleInstance instance, AutomatedFacility outpost, Random random) {
        RecipeBook book = outpost.recipeBook(instance);
        RecipeBook.ScheduleState scheduleState = instance.recipeScheduleState();
        RecipeBook.Selection selection = book.select(scheduleState, random)
            .orElse(null);
        if (selection == null) return;

        boolean produced = tryProduce(outpost, selection.recipe(), random);
        instance.restoreRecipeScheduleState(
            produced ? book.advanceAfterSuccess(scheduleState, selection)
                : book.advanceAfterFailure(scheduleState, selection));
    }

    private static boolean tryProduce(AutomatedFacility outpost, SavedRecipe slot, Random random) {
        RecipeSnapshot recipe = slot.recipe();
        Map<InventoryKey, Long> requiredInputs = recipe.requiredInputs();
        if (!allowsInputs(outpost, requiredInputs)) return false;
        if (!matchesRequestAmount(outpost, slot, recipe.itemOutputs(), recipe.fluidOutputs())) return false;

        Map<InventoryKey, Long> selectedOutputs = selectedOutputs(recipe.itemOutputs(), recipe.fluidOutputs(), random);
        return allowsOutputs(outpost, selectedOutputs) && outpost.tryExchange(requiredInputs, selectedOutputs);
    }

    private static Map<InventoryKey, Long> selectedOutputs(List<Resource> first, List<Resource> second, Random random) {
        Map<InventoryKey, Long> selected = new HashMap<>();
        mergeSelected(selected, first, random);
        mergeSelected(selected, second, random);
        return selected.isEmpty() ? Map.of() : selected;
    }

    private static void mergeSelected(Map<InventoryKey, Long> totals, List<Resource> resources, Random random) {
        for (Resource resource : resources) {
            if (resource.shouldProduce(random)) {
                totals.merge(resource.key(), resource.amount(), Long::sum);
            }
        }
    }

    private static boolean allowsInputs(AutomatedFacility outpost, Map<InventoryKey, Long> requiredInputs) {
        for (Map.Entry<InventoryKey, Long> entry : requiredInputs.entrySet()) {
            // GT encodes non-consumed item inputs as zero, but the item must still be present.
            if (entry.getValue() == 0L && outpost.amount(entry.getKey()) == 0L) return false;
            if (!outpost.isAboveLow(entry.getKey(), entry.getValue())) return false;
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

    private static final class RecipeRuntime extends TieredModuleComponent {

        private final Random random = new Random();

        @Override
        public void runCycle(ModuleInstance module, CelestialAsset asset) {
            if (!(asset instanceof AutomatedFacility facility)) {
                throw new IllegalStateException("Recipe modules can only run in AutomatedFacilities");
            }
            execute(module, facility, random);
        }
    }
}
