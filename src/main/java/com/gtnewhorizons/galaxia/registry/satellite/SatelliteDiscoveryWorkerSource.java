package com.gtnewhorizons.galaxia.registry.satellite;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.function.Function;

import javax.annotation.Nonnull;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryCapability;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryScanScope;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryWorkerContribution;

public final class SatelliteDiscoveryWorkerSource {

    public static final double PROSPECTING_SCAN_RADIUS = 0.12 * 23481;
    public static final double PROSPECTING_EFFECT_PER_WORKER = 1.0;

    private SatelliteDiscoveryWorkerSource() {}

    public static List<CelestialDiscoveryWorkerContribution> prospectingWorkers(
        @Nonnull Function<CelestialObjectKey, OptionalLong> scopeRevision) {
        Map<WorkerKey, Integer> workerCounts = new LinkedHashMap<>();
        for (CelestialAsset asset : CelestialAssetStore.allAssets()) {
            if (!(asset instanceof Satellite satellite) || satellite.satelliteKind() != SatelliteKind.PROSPECTING) {
                continue;
            }
            UUID teamId = CelestialAssetStore.getTeamId(satellite.assetId);
            if (teamId == null) continue;
            OptionalLong revision = scopeRevision.apply(satellite.celestialObjectId);
            if (revision.isEmpty()) continue;
            workerCounts.merge(
                new WorkerKey(teamId, prospectingScope(satellite.celestialObjectId, revision.getAsLong())),
                1,
                Integer::sum);
        }
        return workerCounts.entrySet()
            .stream()
            .map(
                entry -> new CelestialDiscoveryWorkerContribution(
                    entry.getKey()
                        .teamId(),
                    entry.getKey()
                        .scope(),
                    CelestialDiscoveryCapability.PROSPECTING,
                    entry.getValue(),
                    PROSPECTING_EFFECT_PER_WORKER))
            .toList();
    }

    public static CelestialDiscoveryScanScope prospectingScope(@Nonnull CelestialObjectKey anchorKey, long revision) {
        return new CelestialDiscoveryScanScope(anchorKey, PROSPECTING_SCAN_RADIUS, revision);
    }

    private record WorkerKey(UUID teamId, CelestialDiscoveryScanScope scope) {}
}
