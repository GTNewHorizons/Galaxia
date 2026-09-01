package com.gtnewhorizons.galaxia.registry.satellite;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleDebugDataGenerator;

public final class SatelliteDataEndpointRegistry {

    private final Map<CelestialAsset.ID, List<Endpoint>> byAsset = new LinkedHashMap<>();

    /*
     * Facilities announce their current module endpoints here whenever their module list or config changes. The network
     * tick then reads this registry instead of scanning every celestial asset every tick.
     */
    public void refreshFacility(AutomatedFacility facility) {
        if (facility == null) return;
        unregisterAsset(facility.assetId);
        List<Endpoint> endpoints = endpointsFor(facility);
        if (endpoints.isEmpty()) return;
        byAsset.put(facility.assetId, endpoints);
    }

    public void unregisterAsset(CelestialAsset.ID assetId) {
        if (assetId == null) return;
        byAsset.remove(assetId);
    }

    public void clear() {
        byAsset.clear();
    }

    public boolean isEmpty() {
        return byAsset.isEmpty();
    }

    public List<Endpoint> endpoints() {
        List<Endpoint> endpoints = new ArrayList<>();
        for (List<Endpoint> assetEndpoints : byAsset.values()) {
            endpoints.addAll(assetEndpoints);
        }
        return List.copyOf(endpoints);
    }

    public List<SatelliteDataTransferPlanner.Demand> demands() {
        return endpoints().stream()
            .filter(Endpoint::consumes)
            .map(Endpoint::demand)
            .toList();
    }

    /*
     * At the moment the debug generator is the only endpoint-producing module. Keeping this extraction in one place
     * gives real future producer/consumer modules the same registration path.
     */
    private static List<Endpoint> endpointsFor(AutomatedFacility facility) {
        List<Endpoint> endpoints = new ArrayList<>();
        for (ModuleInstance module : facility.modules()) {
            if (!module.isOperational() || !module.enabled()) continue;
            if (module.component() instanceof ModuleDebugDataGenerator debugModule) {
                endpoints.add(new Endpoint(facility, module, facility.celestialObjectKey, debugModule));
            }
        }
        return endpoints;
    }

    public record Endpoint(AutomatedFacility facility, ModuleInstance instance, CelestialObjectKey bodyKey,
        ModuleDebugDataGenerator module) {

        public ModuleInstance.ID id() {
            return instance.id;
        }

        public boolean produces() {
            return module.enabled() && module.isProducer();
        }

        public boolean consumes() {
            return module.enabled() && module.isConsumer();
        }

        public SatelliteDataKey producedKey() {
            return module.producedKey(bodyKey);
        }

        public SatelliteDataKey demandKey() {
            return module.demandKey();
        }

        public long amountDeciKb() {
            return module.amountDeciKb();
        }

        public boolean advanceProduction() {
            module.advanceJob();
            return module.jobComplete();
        }

        public void clearProduction() {
            module.clearJob();
        }

        public CelestialObjectKey counterpartBodyKey() {
            return module.detectedCounterpartBodyKey();
        }

        public void updateCounterpart(CelestialObjectKey counterpartBodyKey) {
            module.updateDetectedCounterpart(counterpartBodyKey);
            facility.markDirty();
        }

        public SatelliteDataTransferPlanner.Demand demand() {
            return new SatelliteDataTransferPlanner.Demand(id(), bodyKey, demandKey(), amountDeciKb());
        }

        public long accept(SatelliteDataKey key, long deciKb) {
            if (!consumes() || !demandKey().equals(key) || deciKb <= 0L) return 0L;
            long accepted = Math.min(deciKb, amountDeciKb());
            module.consume(accepted);
            facility.markDirty();
            return accepted;
        }
    }
}
