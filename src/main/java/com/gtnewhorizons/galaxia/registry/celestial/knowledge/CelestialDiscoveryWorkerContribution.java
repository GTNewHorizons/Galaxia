package com.gtnewhorizons.galaxia.registry.celestial.knowledge;

import java.util.UUID;

import javax.annotation.Nonnull;

public record CelestialDiscoveryWorkerContribution(@Nonnull UUID teamId, @Nonnull CelestialDiscoveryScanScope scope,
    @Nonnull CelestialDiscoveryCapability capability, int workerCount, double effectPerWorker) {

    public CelestialDiscoveryWorkerContribution {
        if (workerCount < 0) throw new IllegalArgumentException("worker count must be non-negative");
        if (!Double.isFinite(effectPerWorker) || effectPerWorker < 0.0) {
            throw new IllegalArgumentException("worker effect must be finite and non-negative");
        }
    }

    long effectiveTicks(int elapsedTicks) {
        double ticks = (double) elapsedTicks * workerCount * effectPerWorker;
        if (!Double.isFinite(ticks) || ticks > Long.MAX_VALUE) {
            throw new IllegalArgumentException("effective discovery ticks overflow");
        }
        return Math.round(ticks);
    }
}
