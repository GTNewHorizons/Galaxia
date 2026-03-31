package com.gtnewhorizons.galaxia.registry.celestial;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.github.bsideup.jabel.Desugar;

@Desugar
public record CelestialBodyAssetState(String celestialObjectId, List<CelestialManagedAsset> assets,
    List<CelestialConstructionSite> constructionSites) {

    public CelestialBodyAssetState {
        assets = assets == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(assets));
        constructionSites = constructionSites == null ? Collections.emptyList()
            : Collections.unmodifiableList(new ArrayList<>(constructionSites));
    }
}
