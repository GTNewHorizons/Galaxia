package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidSlotRanges;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.MinorCelestialBodyId;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;
import com.gtnewhorizons.horizonqa.api.client.ClientTest;

@GameTestHolder(value = "galaxia", clientOnly = true)
public final class OrbitalTransferGameTests {

    private OrbitalTransferGameTests() {}

    /** Bug regression: the client planner produces finite asteroid/planet previews in both directions. */
    @GameTest(timeoutTicks = 200)
    public static void previewsAsteroidPlanetTransfers(GameTestHelper helper) {
        ClientTest.scenario(helper)
            .client("compute both asteroid and planet flight previews", c -> {
                var root = GalaxiaCelestialAPI.getPrimaryRoot();
                var planet = CelestialRegistry.get(CelestialObjectId.OVERWORLD)
                    .orElseThrow();
                var asteroid = CelestialRegistry.get(
                    CelestialObjectKey.minorBody(
                        new MinorCelestialBodyId(CelestialObjectId.FROZEN_BELT, AsteroidSlotRanges.generatedSlot(9))))
                    .orElseThrow();
                for (boolean fromAsteroid : new boolean[] { true, false }) {
                    var state = new InterplanetaryTransferSystem.OrbitalTransferSimulatorState();
                    state.open();
                    state.beginPick(InterplanetaryTransferSystem.TransferPickMode.ORIGIN);
                    state.applyPickedBody(fromAsteroid ? asteroid : planet);
                    state.beginPick(InterplanetaryTransferSystem.TransferPickMode.DESTINATION);
                    state.applyPickedBody(fromAsteroid ? planet : asteroid);
                    InterplanetaryTransferSystem.updatePreview(state, root, 100.0);
                    if (!state.hasPreview() || !Double.isFinite(state.previewTof()) || state.previewTof() <= 0)
                        throw new AssertionError("Missing flight preview, fromAsteroid=" + fromAsteroid);
                    for (int i = 0; i < state.previewPointCount(); i++) {
                        if (!Double.isFinite(state.previewX(i)) || !Double.isFinite(state.previewY(i)))
                            throw new AssertionError("Non-finite flight preview point");
                    }
                    var flight = new InterplanetaryTransferSystem.OrbitalTransferSupport().createTransferJob(
                        root,
                        state.originBody(),
                        state.destinationBody(),
                        "Horizon flight",
                        "",
                        100.0,
                        state.previewTof());
                    if (flight == null || flight.trajectoryPointCount() < 2)
                        throw new AssertionError("Preview cannot be dispatched as a simulated flight");
                }
            })
            .succeed();
    }
}
