package com.gtnewhorizons.galaxia.registry.outpost.module;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeConfig;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSnapshot;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.SavedRecipe;

final class IRecipeModuleTest {

    static final class StubRecipeModule implements IRecipeModule {

        private final String recipeMapName;
        private RecipeConfig recipeConfig;

        StubRecipeModule(String recipeMapName) {
            this.recipeMapName = recipeMapName;
        }

        @Override
        public String getRecipeMapName() {
            return recipeMapName;
        }

        @Override
        public RecipeConfig getRecipeConfig() {
            return recipeConfig;
        }

        @Override
        public void setRecipeConfig(RecipeConfig config) {
            this.recipeConfig = config;
        }
    }

    @Test
    void defaultAdditionalNeiTransferIdentsIsEmpty() {
        StubRecipeModule module = new StubRecipeModule(null);

        assertEquals(List.of(), module.getAdditionalNeiTransferIdents());
    }

    @Test
    void getNextSlotReturnsNegativeOneWhenConfigNull() {
        StubRecipeModule module = new StubRecipeModule(null);

        assertEquals(-1, module.getNextSlot(new Random(0)));
    }

    @Test
    void getNextSlotReturnsNegativeOneWhenConfigHasNoSlots() {
        StubRecipeModule module = new StubRecipeModule(null);
        module.setRecipeConfig(RecipeConfig.empty());

        assertEquals(-1, module.getNextSlot(new Random(0)));
    }

    @Test
    void getNextSlotDelegatesToRecipeSchedulerPrioritySelection() {
        StubRecipeModule module = new StubRecipeModule(null);
        module.setRecipeConfig(RecipeConfig.empty());
        RecipeConfig config = module.getRecipeConfig();
        config.savedRecipes()
            .add(slot((byte) 1));
        config.savedRecipes()
            .add(slot((byte) 5));

        assertEquals(1, module.getNextSlot(new Random(0)));
    }

    private static SavedRecipe slot(byte priority) {
        return new SavedRecipe(RecipeSnapshot.unresolved((byte) 1, 0, 42L), true, 0L, priority, (byte) 1);
    }
}
