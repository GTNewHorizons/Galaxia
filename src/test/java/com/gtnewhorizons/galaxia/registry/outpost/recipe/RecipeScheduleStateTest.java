package com.gtnewhorizons.galaxia.registry.outpost.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class RecipeScheduleStateTest {

    @Test
    void resetIsTheCanonicalInitialSchedule() {
        assertEquals(new RecipeScheduleState((byte) 0, (byte) 0), RecipeScheduleState.RESET);
    }

    @Test
    void cursorMustAddressTheBoundedRecipeBook() {
        assertThrows(IllegalArgumentException.class, () -> new RecipeScheduleState((byte) -1, (byte) 0));
        assertThrows(
            IllegalArgumentException.class,
            () -> new RecipeScheduleState((byte) RecipeBook.MAX_RECIPES, (byte) 0));
    }

    @Test
    void remainingExecutionCountCannotBeNegative() {
        assertThrows(IllegalArgumentException.class, () -> new RecipeScheduleState((byte) 0, (byte) -1));
    }
}
