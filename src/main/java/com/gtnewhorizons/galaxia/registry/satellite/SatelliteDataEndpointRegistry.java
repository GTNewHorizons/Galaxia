package com.gtnewhorizons.galaxia.registry.satellite;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleDebugDataGenerator;

public final class SatelliteDataEndpointRegistry {

    private final Map<CelestialAsset.ID, List<Endpoint>> byAsset = new LinkedHashMap<>();
    private final Map<UUID, Endpoint> external = new LinkedHashMap<>();

    public void register(Endpoint endpoint) {
        external.put(endpoint.id(), endpoint);
    }

    public void unregister(UUID id) {
        external.remove(id);
    }

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

    public void refreshSatellite(Satellite satellite) {
        byAsset.put(satellite.assetId, List.of(satellite));
    }

    public void clear() {
        byAsset.clear();
        external.clear();
    }

    public boolean isEmpty() {
        return byAsset.isEmpty() && external.isEmpty();
    }

    public List<Endpoint> endpoints() {
        List<Endpoint> endpoints = new ArrayList<>();
        for (List<Endpoint> assetEndpoints : byAsset.values()) {
            endpoints.addAll(assetEndpoints);
        }
        endpoints.addAll(external.values());
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
                endpoints.add(new DebugEndpoint(facility, module, facility.celestialObjectKey, debugModule));
            }
        }
        return endpoints;
    }

    /** Live participants advertise bounded offers and demands, independent of their physical representation. */
    public interface Endpoint {

        UUID id();

        CelestialObjectKey bodyKey();

        default boolean produces() {
            return false;
        }

        default boolean consumes() {
            return false;
        }

        default SatelliteDataKey producedKey() {
            return null;
        }

        default SatelliteDataKey demandKey() {
            return null;
        }

        long amountDeciKb();

        default boolean advanceProduction() {
            return true;
        }

        default void clearProduction() {}

        default boolean requiresCounterpart() {
            return false;
        }

        default CelestialObjectKey counterpartBodyKey() {
            return null;
        }

        default void updateCounterpart(CelestialObjectKey bodyKey) {}

        default SatelliteDataTransferPlanner.Demand demand() {
            return new SatelliteDataTransferPlanner.Demand(id(), bodyKey(), demandKey(), amountDeciKb());
        }

        default long accept(SatelliteDataKey key, long deciKb) {
            return 0L;
        }
    }

    private record DebugEndpoint(AutomatedFacility facility, ModuleInstance instance, CelestialObjectKey bodyKey,
        ModuleDebugDataGenerator module) implements Endpoint {

        public UUID id() {
            return instance.id.id();
        }

        public boolean requiresCounterpart() {
            return true;
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
            if (!consumes() || !demandKey().matchesProduced(key) || deciKb <= 0L) return 0L;
            long accepted = Math.min(deciKb, amountDeciKb());
            module.consume(accepted);
            facility.markDirty();
            return accepted;
        }
    }
}
