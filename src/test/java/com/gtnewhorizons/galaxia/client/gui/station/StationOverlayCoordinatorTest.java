package com.gtnewhorizons.galaxia.client.gui.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class StationOverlayCoordinatorTest {

    @Test
    void closeOthersClosesOnlyOtherOpenOverlays() {
        StationOverlayCoordinator coordinator = new StationOverlayCoordinator();
        TestOverlay active = new TestOverlay();
        TestOverlay other = new TestOverlay();
        coordinator.register(active);
        coordinator.register(other);
        active.open = true;
        other.open = true;

        coordinator.closeOthers(active);

        assertTrue(active.open);
        assertFalse(other.open);
        assertEquals(0, active.closeCalls);
        assertEquals(1, other.closeCalls);
    }

    @Test
    void containsMouseOnlyMatchesOpenOverlays() {
        StationOverlayCoordinator coordinator = new StationOverlayCoordinator();
        TestOverlay overlay = new TestOverlay();
        overlay.open = true;
        overlay.contains = true;
        coordinator.register(overlay);

        assertTrue(coordinator.containsMouse(10, 20));

        overlay.open = false;

        assertFalse(coordinator.containsMouse(10, 20));
    }

    @Test
    void blocksInputOnlyMatchesBlockingOverlays() {
        StationOverlayCoordinator coordinator = new StationOverlayCoordinator();
        TestOverlay overlay = new TestOverlay();
        overlay.open = true;
        overlay.contains = true;
        overlay.blocksInput = false;
        coordinator.register(overlay);

        assertTrue(coordinator.containsMouse(10, 20));
        assertFalse(coordinator.blocksInputAt(10, 20));
    }

    private static final class TestOverlay implements StationOverlayCoordinator.Overlay {

        private boolean open;
        private boolean contains;
        private boolean blocksInput = true;
        private int closeCalls;

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public void close() {
            closeCalls++;
            open = false;
        }

        @Override
        public boolean containsMouse(int mouseX, int mouseY) {
            return contains;
        }

        @Override
        public boolean blocksInput() {
            return blocksInput;
        }
    }
}
