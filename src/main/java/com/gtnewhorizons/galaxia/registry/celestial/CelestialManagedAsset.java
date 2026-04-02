package com.gtnewhorizons.galaxia.registry.celestial;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.github.bsideup.jabel.Desugar;

@Desugar
public record CelestialManagedAsset(String assetId, String celestialObjectId, String displayName,
    CelestialAssetKind kind, CelestialAssetLocation location, CelestialAssetStatus status,
    List<CelestialAssetRequirement> requiredResources, List<CelestialAssetRequirement> constructionInventory) {

    public CelestialManagedAsset {
        requiredResources = requiredResources == null ? Collections.emptyList()
            : Collections.unmodifiableList(new ArrayList<>(requiredResources));
        constructionInventory = constructionInventory == null ? Collections.emptyList()
            : Collections.unmodifiableList(new ArrayList<>(constructionInventory));
    }
}
