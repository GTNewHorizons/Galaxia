package com.gtnewhorizons.galaxia.registry.outpost.upkeep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class UpkeepAmountTest {

    @Test
    void parsesFractionalPerMinuteAmountsExactly() {
        UpkeepAmount amount = UpkeepAmount.parse("0.01");

        assertEquals(10_000L, amount.microUnitsPerMinute());
        assertEquals("0.01", amount.toDisplayString());
    }

    @Test
    void scalesFractionalAmountsWithoutRoundingToWholeItems() {
        UpkeepAmount amount = UpkeepAmount.parse("0.01");

        assertEquals(
            8_000L,
            amount.multiplyPercent(80)
                .microUnitsPerMinute());
        assertEquals(
            "0.008",
            amount.multiplyPercent(80)
                .toDisplayString());
    }

    @Test
    void rejectsValuesBelowMicroUnitPrecision() {
        assertThrows(IllegalArgumentException.class, () -> UpkeepAmount.parse("0.0000001"));
    }
}
