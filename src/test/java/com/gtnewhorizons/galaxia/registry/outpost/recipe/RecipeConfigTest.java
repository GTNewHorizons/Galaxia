package com.gtnewhorizons.galaxia.registry.outpost.recipe;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

final class RecipeConfigTest {

    @Test
    void constructionWithValidValues() {
        SavedRecipeList slots = new SavedRecipeList();
        RecipeConfig config = new RecipeConfig(
            slots,
            RecipeSchedulerMode.PRIORITY,
            NotDoablePolicy.SKIP,
            (byte) 0,
            (byte) 0);
        assertSame(slots, config.savedRecipes());
        assertEquals(RecipeSchedulerMode.PRIORITY, config.mode());
        assertEquals(NotDoablePolicy.SKIP, config.notDoablePolicy());
        assertEquals((byte) 0, config.orderCursor());
        assertEquals((byte) 0, config.orderRemaining());
    }

    @Test
    void constructionWithNonZeroOrderCursor() {
        SavedRecipeList slots = new SavedRecipeList();
        RecipeConfig config = new RecipeConfig(
            slots,
            RecipeSchedulerMode.ORDER,
            NotDoablePolicy.BACK_TO_BEGINNING,
            (byte) 5,
            (byte) 3);
        assertEquals((byte) 5, config.orderCursor());
        assertEquals((byte) 3, config.orderRemaining());
    }

    @Test
    void orderCursorAtMaxMinusOneAllowed() {
        SavedRecipeList slots = new SavedRecipeList();
        byte maxIndex = (byte) (SavedRecipeList.MAX_SAVED_RECIPES - 1);
        RecipeConfig config = new RecipeConfig(
            slots,
            RecipeSchedulerMode.PRIORITY,
            NotDoablePolicy.SKIP,
            maxIndex,
            (byte) 0);
        assertEquals(maxIndex, config.orderCursor());
    }

    @Test
    void orderRemainingNegativeThrows() {
        assertThrows(
            IllegalArgumentException.class,
            () -> {
                new RecipeConfig(
                    new SavedRecipeList(),
                    RecipeSchedulerMode.PRIORITY,
                    NotDoablePolicy.SKIP,
                    (byte) 0,
                    (byte) -1);
            });
    }

    @Test
    void orderRemainingZeroAllowed() {
        SavedRecipeList slots = new SavedRecipeList();
        RecipeConfig config = new RecipeConfig(
            slots,
            RecipeSchedulerMode.PRIORITY,
            NotDoablePolicy.SKIP,
            (byte) 0,
            (byte) 0);
        assertEquals((byte) 0, config.orderRemaining());
    }

    @Test
    void emptyFactoryProducesValidDefault() {
        RecipeConfig config = RecipeConfig.empty();
        assertNotNull(config.savedRecipes());
        assertTrue(
            config.savedRecipes()
                .isEmpty());
        assertEquals(RecipeSchedulerMode.PRIORITY, config.mode());
        assertEquals(NotDoablePolicy.SKIP, config.notDoablePolicy());
        assertEquals((byte) 0, config.orderCursor());
        assertEquals((byte) 0, config.orderRemaining());
    }

    @Test
    void recordEqualityByValue() {
        SavedRecipeList slots = new SavedRecipeList();
        RecipeConfig a = new RecipeConfig(
            slots,
            RecipeSchedulerMode.ORDER,
            NotDoablePolicy.BACK_TO_BEGINNING,
            (byte) 3,
            (byte) 7);
        RecipeConfig b = new RecipeConfig(
            slots,
            RecipeSchedulerMode.ORDER,
            NotDoablePolicy.BACK_TO_BEGINNING,
            (byte) 3,
            (byte) 7);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void recordInequalityWhenFieldDiffers() {
        SavedRecipeList slots = new SavedRecipeList();
        RecipeConfig a = new RecipeConfig(
            slots,
            RecipeSchedulerMode.ORDER,
            NotDoablePolicy.BACK_TO_BEGINNING,
            (byte) 3,
            (byte) 7);
        RecipeConfig b = new RecipeConfig(
            slots,
            RecipeSchedulerMode.PRIORITY,
            NotDoablePolicy.BACK_TO_BEGINNING,
            (byte) 3,
            (byte) 7);
        assertNotEquals(a, b);
    }
}
