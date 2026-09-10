package com.gtnewhorizons.galaxia.client.gui.station;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class RecipeSlotUiModelTest {

    @Test
    void parseIntClampsToAllowedRange() {
        assertEquals(0, RecipeSlotUiModel.parseIntOrCurrent("-10", 5, 0, 10));
        assertEquals(10, RecipeSlotUiModel.parseIntOrCurrent("99", 5, 0, 10));
        assertEquals(5, RecipeSlotUiModel.parseIntOrCurrent("bad", 5, 0, 10));
    }

}
