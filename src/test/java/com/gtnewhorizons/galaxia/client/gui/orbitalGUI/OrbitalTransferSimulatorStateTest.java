package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;

final class OrbitalTransferSimulatorStateTest {

    @Test
    void closingPanelPreservesRouteForGuiReopen() {
        InterplanetaryTransferSystem.OrbitalTransferSimulatorState state = new InterplanetaryTransferSystem.OrbitalTransferSimulatorState();
        CelestialObject origin = body(CelestialObjectId.PANSPIRA);
        CelestialObject destination = body(CelestialObjectId.MARS);

        state.open();
        state.beginPick(InterplanetaryTransferSystem.TransferPickMode.ORIGIN);
        state.applyPickedBody(origin);
        state.beginPick(InterplanetaryTransferSystem.TransferPickMode.DESTINATION);
        state.applyPickedBody(destination);

        state.close();
        state.open();

        assertTrue(state.isOpen());
        assertSame(origin, state.originBody());
        assertSame(destination, state.destinationBody());
    }

    @Test
    void resetSelectionClearsPersistedRoute() {
        InterplanetaryTransferSystem.OrbitalTransferSimulatorState state = new InterplanetaryTransferSystem.OrbitalTransferSimulatorState();
        CelestialObject origin = body(CelestialObjectId.PANSPIRA);

        state.open();
        state.beginPick(InterplanetaryTransferSystem.TransferPickMode.ORIGIN);
        state.applyPickedBody(origin);

        state.resetSelection();

        assertFalse(state.isWaitingForPick());
        assertNull(state.originBody());
        assertNull(state.destinationBody());
    }

    private static CelestialObject body(CelestialObjectId id) {
        return CelestialObject.builder()
            .id(id)
            .objectClass(CelestialObject.Class.PLANET)
            .build();
    }
}
