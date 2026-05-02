package com.gtnewhorizons.galaxia.registry.outpost.module;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Random;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeConfig;

import gregtech.api.recipe.RecipeMap;

final class IRecipeModuleTest {

    // Stub implementation for testing
    static final class StubRecipeModule implements IRecipeModule {

        private final RecipeMap<?> recipeMap;
        private RecipeConfig recipeConfig;

        StubRecipeModule(RecipeMap<?> recipeMap) {
            this.recipeMap = recipeMap;
        }

        @Override
        public RecipeMap<?> getRecipeMap() {
            return recipeMap;
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
    void getRecipeMap_returnsSetValue() {
        // RecipeMap is a GT5 type; we can't construct one in test environment.
        // Verify the contract: getRecipeMap returns what was passed to constructor.
        StubRecipeModule module = new StubRecipeModule(null);
        assertNull(module.getRecipeMap());
    }

    @Test
    void getRecipeConfig_returnsNull_byDefault() {
        StubRecipeModule module = new StubRecipeModule(null);
        assertNull(module.getRecipeConfig());
    }

    @Test
    void setRecipeConfig_roundTrip() {
        StubRecipeModule module = new StubRecipeModule(null);
        RecipeConfig config = RecipeConfig.empty();
        module.setRecipeConfig(config);
        assertSame(config, module.getRecipeConfig());
    }

    @Test
    void setRecipeConfig_overwritesPrevious() {
        StubRecipeModule module = new StubRecipeModule(null);
        module.setRecipeConfig(RecipeConfig.empty());
        RecipeConfig second = RecipeConfig.empty();
        module.setRecipeConfig(second);
        assertSame(second, module.getRecipeConfig());
    }

    @Test
    void getNextSlot_returnsNegativeOne_whenConfigNull() {
        StubRecipeModule module = new StubRecipeModule(null);
        assertEquals(-1, module.getNextSlot(new Random(0)));
    }

    @Test
    void getNextSlot_delegatesToRecipeScheduler() {
        StubRecipeModule module = new StubRecipeModule(null);
        RecipeConfig config = RecipeConfig.empty();
        module.setRecipeConfig(config);
        // Empty config → no slots → should return -1
        assertEquals(-1, module.getNextSlot(new Random(0)));
    }

    @Test
    void getNextSlot_returnsSlotIndex_whenSlotsPresent() {
        StubRecipeModule module = new StubRecipeModule(null);
        module.setRecipeConfig(RecipeConfig.empty());
        RecipeConfig config = module.getRecipeConfig();
        // Add a slot to the config's slot list
        com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSlot slot = new com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSlot(
            com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSnapshot.unresolved((byte) 1, 0, 42L),
            true,
            0,
            0,
            (byte) 5,
            (byte) 1);
        config.slots()
            .add(slot);
        // PRIORITY mode with one enabled slot → should return its index (0)
        assertEquals(0, module.getNextSlot(new Random(0)));
    }
}
