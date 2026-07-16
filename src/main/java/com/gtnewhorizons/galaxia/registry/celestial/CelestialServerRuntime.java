package com.gtnewhorizons.galaxia.registry.celestial;

import com.gtnewhorizons.galaxia.core.network.AsteroidKnowledgeSyncAdapter;
import com.gtnewhorizons.galaxia.core.network.CelestialDiscoverySyncAdapter;
import com.gtnewhorizons.galaxia.core.network.CelestialKnowledgeSyncRegistry;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldKnowledgeStore;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldOrbitResolver;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryRuntime;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryScanService;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeService;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalMechanics;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteDiscoveryWorkerSource;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteNetworkService;

/** Server composition and lifecycle for celestial assets, knowledge, discovery, and satellites. */
public record CelestialServerRuntime(CelestialDiscoveryRuntime discovery, CelestialDiscoveryScanService scans) {

    public static CelestialServerRuntime create() {
        CelestialKnowledgeService.registerProvider(AsteroidFieldKnowledgeStore.provider());
        CelestialKnowledgeService.registerDiscoveryDomain(AsteroidFieldKnowledgeStore.discoveryDomain());
        OrbitalMechanics.registerMinorBodyResolver(AsteroidFieldOrbitResolver.INSTANCE);
        CelestialDiscoveryScanService scans = new CelestialDiscoveryScanService(
            CelestialKnowledgeService::discoveryDomain);
        CelestialKnowledgeSyncRegistry.register(new AsteroidKnowledgeSyncAdapter());
        CelestialKnowledgeSyncRegistry.register(new CelestialDiscoverySyncAdapter(scans));
        CelestialDiscoveryRuntime discovery = new CelestialDiscoveryRuntime(
            () -> SatelliteDiscoveryWorkerSource.prospectingWorkers(CelestialKnowledgeService::discoveryScopeRevision),
            scans);
        return new CelestialServerRuntime(discovery, scans);
    }

    public void tick() {
        SatelliteNetworkService.tickDataJobs();
        discovery.tick(1);
    }

    public void reset() {
        CelestialAssetStore.SERVER.clearInternal();
        SatelliteNetworkService.clear();
        AsteroidFieldKnowledgeStore.global()
            .clear();
        discovery.clear();
    }
}
