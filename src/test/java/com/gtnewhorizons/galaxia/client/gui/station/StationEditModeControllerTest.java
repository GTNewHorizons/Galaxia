package com.gtnewhorizons.galaxia.client.gui.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

final class StationEditModeControllerTest {

    @Test
    void startsOneEditModeAtATime() {
        StationEditModeController controller = new StationEditModeController(new StationTilePickerController());

        controller.startTileMode(
            StationEditModeController.Mode.MASS_BUILD,
            "Build",
            "Build",
            coord -> true,
            coord -> coord,
            selected -> {});
        controller.toggle(StationTileCoord.of(1, 0));

        controller.startTileMode(
            StationEditModeController.Mode.DESTROY,
            "Destroy",
            "Destroy",
            coord -> coord.equals(StationTileCoord.of(2, 0)),
            coord -> coord,
            selected -> {});

        assertEquals(StationEditModeController.Mode.DESTROY, controller.mode());
        assertTrue(controller.isActive());
        assertEquals(0, controller.selectedCount());
        assertFalse(controller.isSelected(StationTileCoord.of(1, 0)));
        assertTrue(controller.isCompatible(StationTileCoord.of(2, 0)));
    }

    @Test
    void cancelReturnsToIdleWithoutConfirming() {
        StationEditModeController controller = new StationEditModeController(new StationTilePickerController());
        controller.startTileMode(
            StationEditModeController.Mode.COPY_MODULE,
            "Copy",
            "Copy",
            coord -> true,
            coord -> coord,
            selected -> { throw new AssertionError("confirm should not run"); });
        controller.toggle(StationTileCoord.of(1, 0));

        controller.cancel();

        assertEquals(StationEditModeController.Mode.IDLE, controller.mode());
        assertFalse(controller.isActive());
        assertFalse(controller.canConfirm());
    }

    @Test
    void confirmReturnsSelectedTilesAndReturnsToIdle() {
        List<StationTileCoord> confirmed = new ArrayList<>();
        StationEditModeController controller = new StationEditModeController(new StationTilePickerController());
        controller.startTileMode(
            StationEditModeController.Mode.MASS_DECONSTRUCT,
            "Destroy",
            "Destroy",
            coord -> true,
            coord -> coord,
            confirmed::addAll);
        StationTileCoord first = StationTileCoord.of(1, 0);
        StationTileCoord second = StationTileCoord.of(2, 0);
        controller.toggle(first);
        controller.toggle(second);

        controller.confirm();

        assertEquals(List.of(first, second), confirmed);
        assertEquals(StationEditModeController.Mode.IDLE, controller.mode());
        assertFalse(controller.isActive());
    }

    @Test
    void emptyConfirmLeavesModeActive() {
        StationEditModeController controller = new StationEditModeController(new StationTilePickerController());
        controller.startTileMode(
            StationEditModeController.Mode.MASS_BUILD,
            "Build",
            "Build",
            coord -> true,
            coord -> coord,
            selected -> {});

        controller.confirm();

        assertEquals(StationEditModeController.Mode.MASS_BUILD, controller.mode());
        assertTrue(controller.isActive());
    }
}
