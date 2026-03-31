package com.gtnewhorizons.galaxia.registry.celestial;

import com.github.bsideup.jabel.Desugar;

@Desugar
public record CelestialManagedAsset(String assetId, String celestialObjectId, String displayName, CelestialAssetKind kind,
    CelestialAssetLocation location) {}
