package com.gtnewhorizons.galaxia.client.gui.station;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class ModulePanelActionLayoutTest {

    @Test
    void laysOutTwoActionsPerRow() {
        assertEquals(new ModulePanelActionLayout.Cell(0, 0), ModulePanelActionLayout.cellForIndex(0));
        assertEquals(new ModulePanelActionLayout.Cell(0, 1), ModulePanelActionLayout.cellForIndex(1));
        assertEquals(new ModulePanelActionLayout.Cell(1, 0), ModulePanelActionLayout.cellForIndex(2));
        assertEquals(new ModulePanelActionLayout.Cell(1, 1), ModulePanelActionLayout.cellForIndex(3));
    }
}
