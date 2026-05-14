package com.gtnewhorizons.galaxia.registry.orbital;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class LambertTransferTest {

    @Test
    void solvesCircularQuarterOrbit() {
        LambertTransfer.Solution solution = LambertTransfer.between(1.0, 0.0, 0.0, 1.0)
            .mu(1.0)
            .timeOfFlight(Math.PI * 0.5)
            .prograde(true)
            .solve();

        assertTrue(solution.valid());
        assertEquals(0.0, solution.dvx1(), 1e-9);
        assertEquals(1.0, solution.dvy1(), 1e-9);
        assertEquals(-1.0, solution.dvx2(), 1e-9);
        assertEquals(0.0, solution.dvy2(), 1e-9);
    }

    @Test
    void solvesFastHyperbolicTransfer() {
        double tof = 0.1;
        LambertTransfer.Solution solution = LambertTransfer.between(1.0, 0.0, 0.0, 1.0)
            .mu(1.0)
            .timeOfFlight(tof)
            .prograde(true)
            .solve();

        assertTrue(solution.valid());
        OrbitalMechanics.OrbitalState finalState = propagate(
            new OrbitalMechanics.OrbitalState(1.0, 0.0, solution.dvx1(), solution.dvy1()),
            1.0,
            tof);
        assertEquals(0.0, finalState.x(), 2e-3);
        assertEquals(1.0, finalState.y(), 2e-3);
    }

    private static OrbitalMechanics.OrbitalState propagate(OrbitalMechanics.OrbitalState initialState, double mu,
        double tof) {
        OrbitalMechanics.OrbitalState state = initialState;
        int steps = 20_000;
        double dt = tof / steps;
        for (int i = 0; i < steps; i++) {
            state = OrbitalMechanics.propagateTwoBodyState(state, mu, dt);
        }
        return state;
    }
}
