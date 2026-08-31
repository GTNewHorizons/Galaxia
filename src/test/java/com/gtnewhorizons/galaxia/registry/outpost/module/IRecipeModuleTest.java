package com.gtnewhorizons.galaxia.registry.outpost.module;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

final class IRecipeModuleTest {

    @Test
    void recipeModulesExposeOnlyRecipeMapMetadataByDefault() {
        IRecipeModule module = new IRecipeModule() {

            @Override
            public String getRecipeMapName() {
                return "gt.recipe.test";
            }
        };

        assertEquals("gt.recipe.test", module.getRecipeMapName());
        assertEquals(List.of(), module.getAdditionalNeiTransferIdents());
    }
}
