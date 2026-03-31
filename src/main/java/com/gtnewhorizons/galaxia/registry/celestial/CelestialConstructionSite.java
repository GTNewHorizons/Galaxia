package com.gtnewhorizons.galaxia.registry.celestial;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.github.bsideup.jabel.Desugar;

@Desugar
public record CelestialConstructionSite(String siteId, String celestialObjectId, String displayName, CelestialAssetKind kind,
    CelestialAssetLocation location, ConstructionSiteStatus status, Map<String, Long> requiredResources,
    Map<String, Long> deliveredResources) {

    public CelestialConstructionSite {
        requiredResources = requiredResources == null ? Collections.emptyMap()
            : Collections.unmodifiableMap(new LinkedHashMap<>(requiredResources));
        deliveredResources = deliveredResources == null ? Collections.emptyMap()
            : Collections.unmodifiableMap(new LinkedHashMap<>(deliveredResources));
    }
}
