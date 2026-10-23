package com.gtnewhorizons.galaxia.registry.dimension.worldgen.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

class CraterProfileTest {

    @Test
    void generationAndDocumentationUseCompleteConditionalDistributions() {
        int sizeWeight = 0;
        for (CraterSize size : CraterSize.values()) {
            sizeWeight += size.weight;
            int profileWeight = 0;
            for (CraterProfile profile : CraterProfile.values()) profileWeight += profile.weight(size);
            assertEquals(100, profileWeight);
        }
        assertEquals(100, sizeWeight);
    }

    @Test
    void frackingCostUsesCompletedSharedHistory() {
        for (CraterProfile profile : CraterProfile.values()) {
            long base = profile.drillingFluidPerOperation(0);
            long increment = profile.drillingFluidPerOperation(1) - base;
            assertEquals(16000, base);
            assertTrue(increment > 0);
            assertEquals(base + 1000 * increment, profile.drillingFluidPerOperation(1000));
        }
    }

    @Test
    void reserveScalesWithDiameterAndHasOneBoundedVariation() {
        for (CraterSize size : CraterSize.values()) {
            for (CraterProfile profile : CraterProfile.values()) {
                long nominal = profile.initialReserve(size, size.meanDiameter, variation(0.5));
                assertTrue(nominal > 0);
                assertEquals(Math.round(nominal * 0.9), profile.initialReserve(size, size.meanDiameter, variation(0)));
                assertEquals(
                    Math.round(nominal * 1.1),
                    profile.initialReserve(size, size.meanDiameter, variation(Math.nextDown(1.0))));
                assertEquals(2 * nominal, profile.initialReserve(size, 2 * size.meanDiameter, variation(0.5)));
            }
        }
    }

    private static Random variation(double value) {
        return new Random(0) {

            @Override
            public double nextDouble() {
                return value;
            }
        };
    }
}
