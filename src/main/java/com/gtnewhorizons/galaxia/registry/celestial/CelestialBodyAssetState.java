package com.gtnewhorizons.galaxia.registry.celestial;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.github.bsideup.jabel.Desugar;

@Desugar
public record CelestialBodyAssetState(String celestialObjectId, List<CelestialManagedAsset> assets) {

    public CelestialBodyAssetState {
        assets = assets == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(assets));
    }
}
