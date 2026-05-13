package com.gtnewhorizons.galaxia.client.gui.station;

import java.util.ArrayList;
import java.util.List;

final class StationOverlayCoordinator {

    interface Overlay {

        boolean isOpen();

        void close();

        boolean containsMouse(int mouseX, int mouseY);

        default boolean blocksInput() {
            return true;
        }

        default void processDeferredActions() {}
    }

    private final List<Overlay> overlays = new ArrayList<>();

    void register(Overlay overlay) {
        if (!overlays.contains(overlay)) {
            overlays.add(overlay);
        }
    }

    void closeOthers(Overlay activeOverlay) {
        for (Overlay overlay : overlays) {
            if (overlay != activeOverlay) {
                overlay.close();
            }
        }
    }

    boolean containsMouse(int mouseX, int mouseY) {
        for (Overlay overlay : overlays) {
            if (overlay.isOpen() && overlay.containsMouse(mouseX, mouseY)) {
                return true;
            }
        }
        return false;
    }

    boolean blocksInputAt(int mouseX, int mouseY) {
        for (Overlay overlay : overlays) {
            if (overlay.isOpen() && overlay.blocksInput() && overlay.containsMouse(mouseX, mouseY)) {
                return true;
            }
        }
        return false;
    }

    void processDeferredActions() {
        for (Overlay overlay : overlays) {
            overlay.processDeferredActions();
        }
    }
}
