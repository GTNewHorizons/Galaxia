package com.gtnewhorizons.galaxia.registry.celestial;

import java.util.List;
import java.util.function.Supplier;

import com.gtnewhorizons.galaxia.core.network.CelestialDiscoverySyncAdapter;
import com.gtnewhorizons.galaxia.core.network.CelestialKnowledgeStateSyncAdapter;
import com.gtnewhorizons.galaxia.core.network.CelestialKnowledgeSyncRegistry;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldOrbitResolver;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryScanService;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryWorkerContribution;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeService;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalMechanics;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteDiscoveryWorkerSource;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteNetworkService;

/** Server composition and lifecycle for celestial assets, knowledge, discovery, and satellites. */
public record CelestialServerRuntime(CelestialDiscoveryScanService scans,
    Supplier<List<CelestialDiscoveryWorkerContribution>> discoveryWorkers) {

    public static CelestialServerRuntime create() {
        CelestialKnowledgeService.clearDiscoveryDomains();
        CelestialKnowledgeService.registerDiscoveryDomain(new AsteroidFieldDiscoveryPolicy());
        OrbitalMechanics.registerMinorBodyResolver(AsteroidFieldOrbitResolver.INSTANCE);
        CelestialDiscoveryScanService scans = new CelestialDiscoveryScanService(
            CelestialKnowledgeService::discoveryDomain);
        CelestialKnowledgeSyncRegistry.register(new CelestialKnowledgeStateSyncAdapter());
        CelestialKnowledgeSyncRegistry.register(new CelestialDiscoverySyncAdapter(scans));
        return new CelestialServerRuntime(
            scans,
            () -> SatelliteDiscoveryWorkerSource.prospectingWorkers(CelestialKnowledgeService::discoveryScopeRevision));
    }

    public void tick() {
        SatelliteNetworkService.tickDataJobs();
        scans.tick(discoveryWorkers.get(), 1);
    }

    /** Clears per-world state. Discovery domains are process-wide registrations from {@link #create()}. */
    public void reset() {
        CelestialAssetStore.SERVER.clearInternal();
        SatelliteNetworkService.clear();
        CelestialKnowledgeService.clearFacts();
        scans.clear();
    }
}
