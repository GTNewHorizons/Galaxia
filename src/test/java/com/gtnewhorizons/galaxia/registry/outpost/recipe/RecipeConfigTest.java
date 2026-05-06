package com.gtnewhorizons.galaxia.registry.outpost.recipe;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

final class RecipeConfigTest {

    @Test
    void constructionWithValidValues() {
        RecipeSlotList slots = new RecipeSlotList();
        RecipeConfig config = new RecipeConfig(
            slots,
            RecipeSchedulerMode.PRIORITY,
            NotDoablePolicy.SKIP,
            (byte) 0,
            (byte) 0);
        assertSame(slots, config.slots());
        assertEquals(RecipeSchedulerMode.PRIORITY, config.mode());
        assertEquals(NotDoablePolicy.SKIP, config.notDoablePolicy());
        assertEquals((byte) 0, config.orderCursor());
        assertEquals((byte) 0, config.orderRemaining());
    }

    @Test
    void constructionWithNonZeroOrderCursor() {
        RecipeSlotList slots = new RecipeSlotList();
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
        RecipeSlotList slots = new RecipeSlotList();
        byte maxIndex = (byte) (RecipeSlotList.MAX_RECIPE_SLOTS - 1);
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
                    new RecipeSlotList(),
                    RecipeSchedulerMode.PRIORITY,
                    NotDoablePolicy.SKIP,
                    (byte) 0,
                    (byte) -1);
            });
    }

    @Test
    void orderRemainingZeroAllowed() {
        RecipeSlotList slots = new RecipeSlotList();
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
        assertNotNull(config.slots());
        assertTrue(
            config.slots()
                .isEmpty());
        assertEquals(RecipeSchedulerMode.PRIORITY, config.mode());
        assertEquals(NotDoablePolicy.SKIP, config.notDoablePolicy());
        assertEquals((byte) 0, config.orderCursor());
        assertEquals((byte) 0, config.orderRemaining());
    }

    @Test
    void recordEqualityByValue() {
        RecipeSlotList slots = new RecipeSlotList();
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
        RecipeSlotList slots = new RecipeSlotList();
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
