package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidSlotRanges;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.MinorCelestialBodyId;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalTransferPlanner;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class OrbitalTransferSimulatorStateTest {

    @Test
    void previewsGeneratedAsteroidTransfer() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();
        var root = GalaxiaCelestialAPI.getPrimaryRoot();
        var asteroid = CelestialRegistry
            .get(
                CelestialObjectKey.minorBody(
                    new MinorCelestialBodyId(CelestialObjectId.FROZEN_BELT, AsteroidSlotRanges.generatedSlot(9))))
            .orElseThrow();
        var planet = CelestialRegistry.get(CelestialObjectId.OVERWORLD)
            .orElseThrow();
        var star = GalaxiaCelestialAPI.findStar(root, asteroid);
        var route = OrbitalTransferPlanner
            .computeRoute(root, star, asteroid, planet, 100.0, OrbitalTransferPlanner.RoutePriority.PRIORITIZE_DV);
        var state = new InterplanetaryTransferSystem.OrbitalTransferSimulatorState();
        state.open();
        state.beginPick(InterplanetaryTransferSystem.TransferPickMode.ORIGIN);
        state.applyPickedBody(asteroid);
        state.beginPick(InterplanetaryTransferSystem.TransferPickMode.DESTINATION);
        state.applyPickedBody(planet);
        InterplanetaryTransferSystem.updatePreview(state, root, 100.0);
        assertTrue(state.hasPreview(), "route=" + route + ", budget=" + state.sliderDv());
        state.setMaxDv(route.totalDv() * 0.5);
        InterplanetaryTransferSystem.updatePreview(state, root, 100.0);
        assertFalse(state.hasPreview(), "A manual limit below the cheapest route must be respected");
        state.beginPick(InterplanetaryTransferSystem.TransferPickMode.DESTINATION);
        state.applyPickedBody(planet);
        InterplanetaryTransferSystem.updatePreview(state, root, 100.0);
        assertTrue(state.hasPreview(), "Selecting a route again must restore a usable automatic budget");
    }

    @Test
    void closingPanelPreservesRouteForGuiReopen() {
        InterplanetaryTransferSystem.OrbitalTransferSimulatorState state = new InterplanetaryTransferSystem.OrbitalTransferSimulatorState();
        CelestialObject origin = body(CelestialObjectId.OVERWORLD);
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
        CelestialObject origin = body(CelestialObjectId.OVERWORLD);

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
            .key(id)
            .objectClass(CelestialObject.Class.PLANET)
            .build();
    }
}
