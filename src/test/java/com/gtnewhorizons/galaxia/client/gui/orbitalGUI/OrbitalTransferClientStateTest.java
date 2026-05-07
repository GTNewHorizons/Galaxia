package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class OrbitalTransferClientStateTest {

    @Test
    void simulatedTransfersPruneOnlyAfterArrivalTime() {
        InterplanetaryTransferSystem.OrbitalTransferState state = new InterplanetaryTransferSystem.OrbitalTransferState();
        state.addTransfer(transfer("sim:one", 10.0, 20.0));

        state.pruneFinishedTransfers(19.9, 0.0);

        assertEquals(
            1,
            state.transfers()
                .size());

        state.pruneFinishedTransfers(20.0, 0.0);

        assertEquals(
            0,
            state.transfers()
                .size());
    }

    private static InterplanetaryTransferJob transfer(String id, double departureTime, double arrivalTime) {
        return new InterplanetaryTransferJob(
            id,
            "Simulation",
            "Simulation",
            null,
            null,
            null,
            null,
            departureTime,
            arrivalTime,
            new double[] { 0.0, 1.0 },
            new double[] { 0.0, 1.0 },
            2,
            TransferPackageKind.HAMMER);
    }
}
