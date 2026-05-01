package com.gtnewhorizons.galaxia.registry.outpost.recipe;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

final class NotDoablePolicyTest {

    @Test
    void ordinalStability() {
        assertEquals(0, NotDoablePolicy.SKIP.ordinal());
        assertEquals(1, NotDoablePolicy.BACK_TO_BEGINNING.ordinal());
    }

    @Test
    void count() {
        assertEquals(2, NotDoablePolicy.values().length);
    }

    @Test
    void valueOfRoundTrip() {
        assertEquals(NotDoablePolicy.SKIP, NotDoablePolicy.valueOf("SKIP"));
        assertEquals(NotDoablePolicy.BACK_TO_BEGINNING, NotDoablePolicy.valueOf("BACK_TO_BEGINNING"));
    }
}
