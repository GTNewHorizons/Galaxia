package com.gtnewhorizons.galaxia.registry.celestial.knowledge;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Server lifecycle boundary for generic celestial discovery scans.
 */
public final class CelestialDiscoveryRuntime {

    private final Supplier<List<CelestialDiscoveryWorkerContribution>> workerSource;
    private final CelestialDiscoveryScanService scanService;
    private final Function<CelestialDiscoveryScanScope, CelestialDiscoveryDomain> domainResolver;

    public CelestialDiscoveryRuntime(Supplier<List<CelestialDiscoveryWorkerContribution>> workerSource,
        CelestialDiscoveryScanService scanService,
        Function<CelestialDiscoveryScanScope, CelestialDiscoveryDomain> domainResolver) {
        this.workerSource = workerSource;
        this.scanService = scanService;
        this.domainResolver = domainResolver;
    }

    public void tick(int elapsedTicks) {
        if (elapsedTicks < 0) throw new IllegalArgumentException("elapsedTicks must be non-negative");
        scanService.tick(workerSource.get(), elapsedTicks, domainResolver);
    }

    public void clear() {
        scanService.clear();
    }
}
