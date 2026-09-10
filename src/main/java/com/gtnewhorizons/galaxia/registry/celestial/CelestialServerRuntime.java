package com.gtnewhorizons.galaxia.registry.celestial;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

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
        return new CelestialServerRuntime(
            scans,
            () -> SatelliteDiscoveryWorkerSource.prospectingWorkers(CelestialKnowledgeService::discoveryScopeRevision));
    }

    public void tick() {
        SatelliteNetworkService.tickDataJobs();
        scans.tick(discoveryWorkers.get(), 1);
    }

    public void mergeTeams(UUID consumedTeam, UUID survivingTeam) {
        if (consumedTeam.equals(survivingTeam)) return;
        List<CelestialAsset> transferred = CelestialAssetStore.getTeamAssets(consumedTeam)
            .values()
            .stream()
            .flatMap(java.util.Collection::stream)
            .toList();
        CelestialKnowledgeService.mergeTeams(consumedTeam, survivingTeam);
        CelestialAssetStore.transferTeamAssets(consumedTeam, survivingTeam);
        SatelliteNetworkService.mergeTeams(consumedTeam, survivingTeam, transferred);
        scans.mergeTeams(consumedTeam, survivingTeam, discoveryWorkers.get());
    }

    /** Clears per-world state. Discovery domains are process-wide registrations from {@link #create()}. */
    public void reset() {
        CelestialAssetStore.SERVER.clearInternal();
        SatelliteNetworkService.clear();
        CelestialKnowledgeService.clearFacts();
        scans.clear();
    }
}
