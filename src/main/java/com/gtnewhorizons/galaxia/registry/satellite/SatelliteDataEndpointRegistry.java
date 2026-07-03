package com.gtnewhorizons.galaxia.registry.satellite;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleDebugDataGenerator;

public final class SatelliteDataEndpointRegistry {

    private final Map<UUID, Map<CelestialAsset.ID, List<Endpoint>>> byTeam = new HashMap<>();
    private final Map<CelestialAsset.ID, UUID> teamByAsset = new HashMap<>();

    /*
     * Facilities announce their current module endpoints here whenever their module list or config changes. The network
     * tick then reads this registry instead of scanning every celestial asset every tick.
     */
    public void refreshFacility(UUID teamId, AutomatedFacility facility) {
        if (facility == null) return;
        if (teamId == null) return;
        unregisterAsset(facility.assetId);
        List<Endpoint> endpoints = endpointsFor(teamId, facility);
        if (endpoints.isEmpty()) return;
        byTeam.computeIfAbsent(teamId, ignored -> new LinkedHashMap<>())
            .put(facility.assetId, endpoints);
        teamByAsset.put(facility.assetId, teamId);
    }

    public void unregisterAsset(CelestialAsset.ID assetId) {
        if (assetId == null) return;
        UUID teamId = teamByAsset.remove(assetId);
        if (teamId == null) return;
        Map<CelestialAsset.ID, List<Endpoint>> teamEndpoints = byTeam.get(teamId);
        if (teamEndpoints == null) return;
        teamEndpoints.remove(assetId);
        if (teamEndpoints.isEmpty()) byTeam.remove(teamId);
    }

    public void unregisterTeam(UUID teamId) {
        if (teamId == null) return;
        Map<CelestialAsset.ID, List<Endpoint>> removed = byTeam.remove(teamId);
        if (removed == null) return;
        for (CelestialAsset.ID assetId : removed.keySet()) {
            teamByAsset.remove(assetId);
        }
    }

    public void clear() {
        byTeam.clear();
        teamByAsset.clear();
    }

    public Set<UUID> teamIds() {
        return Set.copyOf(byTeam.keySet());
    }

    public List<Endpoint> endpoints(UUID teamId) {
        Map<CelestialAsset.ID, List<Endpoint>> teamEndpoints = byTeam.get(teamId);
        if (teamEndpoints == null || teamEndpoints.isEmpty()) return List.of();
        List<Endpoint> endpoints = new ArrayList<>();
        for (List<Endpoint> assetEndpoints : teamEndpoints.values()) {
            endpoints.addAll(assetEndpoints);
        }
        return List.copyOf(endpoints);
    }

    /*
     * At the moment the debug generator is the only endpoint-producing module. Keeping this extraction in one place
     * gives real future producer/consumer modules the same registration path.
     */
    private static List<Endpoint> endpointsFor(UUID teamId, AutomatedFacility facility) {
        List<Endpoint> endpoints = new ArrayList<>();
        for (ModuleInstance module : facility.modules()) {
            if (module.component() instanceof ModuleDebugDataGenerator debugModule) {
                endpoints.add(
                    new Endpoint(
                        teamId,
                        facility,
                        module,
                        facility.celestialObjectId,
                        debugModule));
            }
        }
        return endpoints;
    }

    public record Endpoint(UUID teamId, AutomatedFacility facility, ModuleInstance instance, CelestialObjectKey bodyKey,
        ModuleDebugDataGenerator module) {

        public CelestialObjectId bodyId() {
            return bodyKey.requireRegisteredBodyId();
        }
    }
}
