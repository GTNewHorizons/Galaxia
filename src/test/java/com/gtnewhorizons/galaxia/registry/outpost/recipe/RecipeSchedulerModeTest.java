package com.gtnewhorizons.galaxia.registry.outpost.recipe;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

final class RecipeSchedulerModeTest {

    @Test
    void ordinalStability() {
        assertEquals(0, RecipeSchedulerMode.PRIORITY.ordinal());
        assertEquals(1, RecipeSchedulerMode.ORDER.ordinal());
        assertEquals(2, RecipeSchedulerMode.RANDOM.ordinal());
    }

    @Test
    void count() {
        assertEquals(3, RecipeSchedulerMode.values().length);
    }

    @Test
    void valueOfRoundTrip() {
        assertEquals(RecipeSchedulerMode.PRIORITY, RecipeSchedulerMode.valueOf("PRIORITY"));
        assertEquals(RecipeSchedulerMode.ORDER, RecipeSchedulerMode.valueOf("ORDER"));
        assertEquals(RecipeSchedulerMode.RANDOM, RecipeSchedulerMode.valueOf("RANDOM"));
    }
}
