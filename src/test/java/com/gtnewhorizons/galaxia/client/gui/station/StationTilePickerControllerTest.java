package com.gtnewhorizons.galaxia.client.gui.station;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.outpost.station.ModulePlacement;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

final class StationTilePickerControllerTest {

    @Test
    void startingAnotherPickerReplacesConfigurationAndClearsSelection() {
        StationTilePickerController controller = new StationTilePickerController();
        controller.start("Build", "Build", coord -> true, coord -> coord, selected -> {});
        toggle(controller, StationTileCoord.of(1, 0));

        controller.start(
            "Destroy",
            "Destroy",
            coord -> coord.equals(StationTileCoord.of(2, 0)),
            coord -> coord,
            selected -> {});

        assertTrue(controller.isActive());
        assertEquals(0, controller.selectedCount());
        assertFalse(controller.isSelected(StationTileCoord.of(1, 0)));
        assertTrue(controller.isCompatibleNormalized(controller.normalize(StationTileCoord.of(2, 0))));
    }

    @Test
    void emptyConfirmLeavesEditModeActive() {
        StationTilePickerController controller = new StationTilePickerController();
        controller.start("Build", "Build", coord -> true, coord -> coord, selected -> {});

        controller.confirm();

        assertTrue(controller.isActive());
    }

    @Test
    void pickerTogglesOnlyCompatibleTiles() {
        StationTileCoord compatible = StationTileCoord.of(1, 0);
        StationTileCoord incompatible = StationTileCoord.of(2, 0);
        StationTilePickerController controller = new StationTilePickerController();
        controller.start("Copy", "Confirm", coord -> coord.equals(compatible), coord -> coord, selected -> {});

        assertTrue(toggle(controller, compatible));
        assertFalse(toggle(controller, incompatible));
        assertTrue(controller.isSelected(compatible));
        assertFalse(controller.isSelected(incompatible));

        assertTrue(toggle(controller, compatible));
        assertFalse(controller.isSelected(compatible));
    }

    @Test
    void pickerNormalizesClickedTileBeforeSelection() {
        StationTileCoord clicked = StationTileCoord.of(2, 0);
        StationTileCoord anchor = StationTileCoord.of(1, 0);
        StationTilePickerController controller = new StationTilePickerController();
        controller.start("Copy", "Confirm", coord -> coord.equals(anchor), coord -> anchor, selected -> {});

        assertTrue(toggle(controller, clicked));

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

        toggle(controller, first);
        toggle(controller, second);

        assertTrue(controller.canConfirm());
        controller.confirm();

        assertEquals(List.of(first, second), confirmed);
        assertFalse(controller.isActive());
    }

    @Test
    void cancelExitsWithoutCallingConfirm() {
        StationTilePickerController controller = new StationTilePickerController();
        controller.start("Copy", "Apply", coord -> true, coord -> coord, selected -> fail("confirm should not run"));
        toggle(controller, StationTileCoord.of(1, 0));

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

        assertTrue(toggle(controller, first));
        assertTrue(toggle(controller, second));
        assertTrue(toggle(controller, third));
        assertEquals(3, controller.selectedCount());

        assertTrue(toggle(controller, first));

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

    @Test
    void selectedTargetKeepsRotationWhenCurrentFootprintRotates() {
        StationTileCoord first = StationTileCoord.of(1, 0);
        StationTileCoord second = StationTileCoord.of(2, 0);
        StationTilePickerController controller = new StationTilePickerController();
        controller.start("Build", "Confirm", coord -> true, coord -> coord, selected -> {});
        controller.setSelectionFootprint(ModuleShape.L_2x2, true);

        assertTrue(toggle(controller, first));
        assertEquals(0, controller.selectedTargetRotation(first));

        assertTrue(controller.rotateSelectionFootprint());
        assertEquals(1, controller.footprintRotation());
        assertEquals(0, controller.selectedTargetRotation(first));

        assertTrue(toggle(controller, second));
        assertEquals(1, controller.selectedTargetRotation(second));
    }

    @Test
    void confirmCanReturnSelectedTargetsWithTheirRotations() {
        StationTileCoord first = StationTileCoord.of(1, 0);
        StationTileCoord second = StationTileCoord.of(2, 0);
        List<ModulePlacement> confirmed = new ArrayList<>();
        StationTilePickerController controller = new StationTilePickerController();
        controller.startWithPlacements(
            "Build",
            "Confirm",
            (coord, selected) -> true,
            coord -> coord,
            confirmed::addAll,
            targets -> targets);
        controller.setSelectionFootprint(ModuleShape.L_2x2, true);

        toggle(controller, first);
        controller.rotateSelectionFootprint();
        toggle(controller, second);
        controller.confirm();

        assertEquals(List.of(new ModulePlacement(first, 0), new ModulePlacement(second, 1)), confirmed);
    }

    private static boolean toggle(StationTilePickerController controller, StationTileCoord coord) {
        return controller.toggleNormalized(controller.normalize(coord));
    }
}
