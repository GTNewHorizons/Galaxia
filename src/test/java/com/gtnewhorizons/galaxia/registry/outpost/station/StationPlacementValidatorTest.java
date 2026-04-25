package com.gtnewhorizons.galaxia.registry.outpost.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

final class StationPlacementValidatorTest {

    @Test
    void collectExpansionSlotsReturnsOnlyEmptyOrthogonalNeighbours() {
        StationLayout layout = new StationLayout();
        StationTileCoord east = StationTileCoord.of(1, 0);
        layout.place(east, new PlacedTile(null, StationTileState.UNDER_CONSTRUCTION));

        Set<StationTileCoord> slots = new LinkedHashSet<>();

        StationPlacementValidator.collectExpansionSlots(layout, slots);

        assertEquals(6, slots.size());
        assertTrue(slots.contains(StationTileCoord.of(-1, 0)));
        assertTrue(slots.contains(StationTileCoord.of(0, -1)));
        assertTrue(slots.contains(StationTileCoord.of(0, 1)));
        assertTrue(slots.contains(StationTileCoord.of(2, 0)));
        assertTrue(slots.contains(StationTileCoord.of(1, -1)));
        assertTrue(slots.contains(StationTileCoord.of(1, 1)));
    }

    @Test
    void collectExpansionSlotsClearsPreviousScratchContents() {
        StationLayout layout = new StationLayout();
        Set<StationTileCoord> slots = new LinkedHashSet<>();
        slots.add(StationTileCoord.of(5, 5));

        StationPlacementValidator.collectExpansionSlots(layout, slots);

        assertEquals(
            Set.of(
                StationTileCoord.of(-1, 0),
                StationTileCoord.of(1, 0),
                StationTileCoord.of(0, -1),
                StationTileCoord.of(0, 1)),
            slots);
    }
}
