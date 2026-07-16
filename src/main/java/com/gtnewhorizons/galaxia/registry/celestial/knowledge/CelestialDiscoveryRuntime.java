package com.gtnewhorizons.galaxia.registry.celestial.knowledge;

import java.util.List;
import java.util.function.Supplier;

/**
 * Server lifecycle boundary for generic celestial discovery scans.
 */
public final class CelestialDiscoveryRuntime {

    private final Supplier<List<CelestialDiscoveryWorkerContribution>> workerSource;
    private final CelestialDiscoveryScanService scanService;

    public CelestialDiscoveryRuntime(Supplier<List<CelestialDiscoveryWorkerContribution>> workerSource,
        CelestialDiscoveryScanService scanService) {
        this.workerSource = workerSource;
        this.scanService = scanService;
    }

    public void tick(int elapsedTicks) {
        if (elapsedTicks < 0) throw new IllegalArgumentException("elapsedTicks must be non-negative");
        scanService.tick(workerSource.get(), elapsedTicks);
    }

    public void clear() {
        scanService.clear();
    }
}
