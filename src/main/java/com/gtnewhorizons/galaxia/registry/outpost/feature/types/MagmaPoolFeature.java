package com.gtnewhorizons.galaxia.registry.outpost.feature.types;

import com.gtnewhorizons.galaxia.api.GalaxiaAPI;
import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeature;
import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeatureDefinition;
import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeatureLayer;
import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeaturePlacement;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;

public final class MagmaPoolFeature implements PlanetaryFeature {

    private static final PlanetaryFeatureDefinition DEFINITION = PlanetaryFeatureDefinition.builder("magma_pool")
        .displayName("Magma Pool")
        .description("Natural heat source")
        .texture(GalaxiaAPI.LocationGalaxia("textures/gui/station/features/magma_pool.png"))
        .layer(PlanetaryFeatureLayer.ENVIRONMENT)
        .placement(PlanetaryFeaturePlacement.isolated(6))
        .build();

    @Override
    public PlanetaryFeatureDefinition definition() {
        return DEFINITION;
    }

    @Override
    public boolean isRequiredAnchorFeatureFor(FacilityModuleKind kind) {
        return kind == FacilityModuleKind.GEOTHERMAL_GENERATOR;
    }
}
