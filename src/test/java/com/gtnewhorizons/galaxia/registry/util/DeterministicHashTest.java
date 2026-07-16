package com.gtnewhorizons.galaxia.registry.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class DeterministicHashTest {

    @Test
    void mixProducesStableValuesForProceduralContent() {
        assertEquals(957592227727159326L, DeterministicHash.mix(123L, 456L, 789L));
        assertEquals(-2152535657050944081L, DeterministicHash.mix(0L));
        assertEquals(2541777941706220057L, DeterministicHash.mix(-42L, 17L, 999L));

        assertNotEquals(DeterministicHash.mix(1L, 2L), DeterministicHash.mix(2L, 1L));
    }

    @Test
    void unitDoubleMapsHashBitsIntoHalfOpenUnitRange() {
        double value = DeterministicHash.unitDouble(DeterministicHash.mix(123L, 456L, 789L));

        assertTrue(value >= 0.0);
        assertTrue(value < 1.0);
        assertEquals(0.051911178682851, value, 1.0e-15);
    }
}
