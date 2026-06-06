package com.gtnewhorizons.galaxia.client.gui.station;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

final class StationTilePickerControllerTest {

    @Test
    void pickerTogglesOnlyCompatibleTiles() {
        StationTileCoord compatible = StationTileCoord.of(1, 0);
        StationTileCoord incompatible = StationTileCoord.of(2, 0);
        StationTilePickerController controller = new StationTilePickerController();
        controller.start("Copy", "Confirm", coord -> coord.equals(compatible), coord -> coord, selected -> {});

        assertTrue(controller.toggle(compatible));
        assertFalse(controller.toggle(incompatible));
        assertTrue(controller.isSelected(compatible));
        assertFalse(controller.isSelected(incompatible));

        assertTrue(controller.toggle(compatible));
        assertFalse(controller.isSelected(compatible));
    }

    @Test
    void pickerNormalizesClickedTileBeforeSelection() {
        StationTileCoord clicked = StationTileCoord.of(2, 0);
        StationTileCoord anchor = StationTileCoord.of(1, 0);
        StationTilePickerController controller = new StationTilePickerController();
        controller.start("Copy", "Confirm", coord -> coord.equals(anchor), coord -> anchor, selected -> {});

        assertTrue(controller.toggle(clicked));

        assertTrue(controller.isSelected(anchor));
        assertTrue(controller.isSelected(clicked));
    }

    @Test
    void confirmReturnsSelectedTilesAndExitsPicker() {
        StationTileCoord first = StationTileCoord.of(1, 0);
        StationTileCoord second = StationTileCoord.of(2, 0);
        List<StationTileCoord> confirmed = new ArrayList<>();
        StationTilePickerController controller = new StationTilePickerController();
        controller.start("Copy", "Apply", coord -> true, coord -> coord, confirmed::addAll);

        controller.toggle(first);
        controller.toggle(second);

        assertTrue(controller.canConfirm());
        controller.confirm();

        assertEquals(List.of(first, second), confirmed);
        assertFalse(controller.isActive());
    }

    @Test
    void cancelExitsWithoutCallingConfirm() {
        StationTilePickerController controller = new StationTilePickerController();
        controller.start("Copy", "Apply", coord -> true, coord -> coord, selected -> fail("confirm should not run"));
        controller.toggle(StationTileCoord.of(1, 0));

        controller.cancel();

        assertFalse(controller.isActive());
        assertFalse(controller.canConfirm());
    }

    @Test
    void prunesSelectionAfterDeselectedTargetDisconnectsOthers() {
        StationTilePickerController controller = new StationTilePickerController();
        StationTileCoord first = StationTileCoord.of(1, 0);
        StationTileCoord second = StationTileCoord.of(2, 0);
        StationTileCoord third = StationTileCoord.of(3, 0);

        controller.start(
            "Build",
            "Confirm",
            (coord, selected) -> true,
            coord -> coord,
            targets -> {},
            targets -> targets.contains(first) ? targets : List.of());

        assertTrue(controller.toggle(first));
        assertTrue(controller.toggle(second));
        assertTrue(controller.toggle(third));
        assertEquals(3, controller.selectedCount());

        assertTrue(controller.toggle(first));

        assertEquals(0, controller.selectedCount());
        assertFalse(controller.isSelected(second));
        assertFalse(controller.isSelected(third));
    }

    @Test
    void rotatesConfiguredFootprintWithRActionOnlyWhenEnabled() {
        StationTilePickerController controller = new StationTilePickerController();
        controller.start("Build", "Confirm", coord -> true, coord -> coord, selected -> {});

        assertFalse(controller.rotateSelectionFootprint());
        assertEquals(0, controller.footprintRotation());

        controller.setSelectionFootprint(ModuleShape.QUAD_2x2, true);

        assertTrue(controller.rotateSelectionFootprint());
        assertEquals(1, controller.footprintRotation());
        assertTrue(controller.rotateSelectionFootprint());
        assertTrue(controller.rotateSelectionFootprint());
        assertTrue(controller.rotateSelectionFootprint());
        assertEquals(0, controller.footprintRotation());
    }
}
