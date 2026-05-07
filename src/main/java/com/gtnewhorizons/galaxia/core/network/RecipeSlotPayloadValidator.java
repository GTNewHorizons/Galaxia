package com.gtnewhorizons.galaxia.core.network;

import javax.annotation.Nullable;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.gtnewhorizons.galaxia.compat.recipe.GTRecipeSlotPayloadValidator;
import com.gtnewhorizons.galaxia.registry.outpost.module.IRecipeModule;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSnapshot;

final class RecipeSlotPayloadValidator {

    private static final Logger LOG = LogManager.getLogger("Galaxia");

    private RecipeSlotPayloadValidator() {}

    static @Nullable RecipeSnapshot validate(@Nullable IRecipeModule module, @Nullable RecipeSnapshot snapshot) {
        return GTRecipeSlotPayloadValidator.validate(module, snapshot, LOG);
    }
}
