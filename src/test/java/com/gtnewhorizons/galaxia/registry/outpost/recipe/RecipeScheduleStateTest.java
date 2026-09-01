package com.gtnewhorizons.galaxia.registry.outpost.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class RecipeScheduleStateTest {

    @Test
    void resetIsTheCanonicalInitialSchedule() {
        assertEquals(new RecipeBook.ScheduleState((byte) 0, (byte) 0), RecipeBook.ScheduleState.RESET);
    }

    @Test
    void cursorMustAddressTheBoundedRecipeBook() {
        assertThrows(IllegalArgumentException.class, () -> new RecipeBook.ScheduleState((byte) -1, (byte) 0));
        assertThrows(
            IllegalArgumentException.class,
            () -> new RecipeBook.ScheduleState((byte) RecipeBook.MAX_RECIPES, (byte) 0));
    }

    @Test
    void remainingExecutionCountCannotBeNegative() {
        assertThrows(IllegalArgumentException.class, () -> new RecipeBook.ScheduleState((byte) 0, (byte) -1));
    }
}
