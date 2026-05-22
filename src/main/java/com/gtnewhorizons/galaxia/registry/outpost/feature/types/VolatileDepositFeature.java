package com.gtnewhorizons.galaxia.registry.outpost.feature.types;

import com.gtnewhorizons.galaxia.api.GalaxiaAPI;
import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeature;
import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeatureDefinition;
import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeatureLayer;
import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeaturePlacement;

public final class VolatileDepositFeature implements PlanetaryFeature {

    private static final PlanetaryFeatureDefinition DEFINITION = PlanetaryFeatureDefinition.builder("volatile_deposit")
        .displayName("Volatile Deposit")
        .description("Chemical volatile pocket")
        .texture(GalaxiaAPI.LocationGalaxia("textures/gui/station/features/volatile_deposit.png"))
        .layer(PlanetaryFeatureLayer.RESOURCE)
        .placement(PlanetaryFeaturePlacement.clusteredPatch(5.0, 2.0))
        .build();

    @Override
    public PlanetaryFeatureDefinition definition() {
        return DEFINITION;
    }
}
