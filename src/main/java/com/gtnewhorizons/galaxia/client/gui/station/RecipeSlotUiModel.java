package com.gtnewhorizons.galaxia.client.gui.station;

import java.util.List;

import javax.annotation.Nullable;

import com.gtnewhorizons.galaxia.registry.outpost.FluidKey;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSchedulerMode;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSnapshot;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSnapshot.Resource;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.SavedRecipe;

final class RecipeSlotUiModel {

    static final int MAX_BYTE_SETTING = Byte.MAX_VALUE;

    private RecipeSlotUiModel() {}

    static String modeLabel(RecipeSchedulerMode mode) {
        return "Mode: " + mode.name();
    }

    static String slotTitle(SavedRecipe slot) {
        if (slot.displayName() != null && !slot.displayName()
            .isBlank()) {
            return slot.displayName();
        }
        RecipeSnapshot recipe = slot.recipe();
        String input = resourceSummary(recipe.itemInputs(), recipe.fluidInputs());
        String output = resourceSummary(recipe.itemOutputs(), recipe.fluidOutputs());
        if (input != null || output != null) {
            return (input != null ? input : "?") + " -> " + (output != null ? output : "?");
        }
        return "Recipe #" + recipe.recipeIndex();
    }

    static int parseIntOrCurrent(String text, int current, int min, int max) {
        int parsed;
        try {
            parsed = Integer.parseInt(text);
        } catch (NumberFormatException ignored) {
            parsed = current;
        }
        return Math.max(min, Math.min(max, parsed));
    }

    static @Nullable String fluidSlotAmountText(@Nullable Resource resource) {
        return resource != null && resource.key() instanceof FluidKey ? resource.amount() + "L" : null;
    }

    private static @Nullable String resourceSummary(List<Resource> items, List<Resource> fluids) {
        String item = itemSummary(items);
        String fluid = fluidSummary(fluids);
        if (item == null) return fluid;
        if (fluid == null) return item;
        return item + " + " + fluid;
    }

    private static @Nullable String itemSummary(List<Resource> resources) {
        for (Resource resource : resources) {
            if (!(resource.key() instanceof ItemStackWrapper item)) continue;
            String name = item.toStack(1)
                .getDisplayName();
            if (name == null || name.isBlank()) continue;
            return resource.amount() > 1 ? resource.amount() + "x " + name : name;
        }
        return null;
    }

    private static @Nullable String fluidSummary(List<Resource> resources) {
        for (Resource resource : resources) {
            if (!(resource.key() instanceof FluidKey fluid) || fluid.fluid() == null) continue;
            String name = fluid.fluid()
                .getName();
            if (name == null || name.isBlank()) continue;
            return resource.amount() + "L " + name;
        }
        return null;
    }
}
