package com.gtnewhorizons.galaxia.registry.outpost.feature.types;

import com.gtnewhorizons.galaxia.api.GalaxiaAPI;
import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeature;
import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeatureDefinition;
import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeatureLayer;
import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeaturePlacement;

public final class ThermalSinkZoneFeature implements PlanetaryFeature {

    private static final PlanetaryFeatureDefinition DEFINITION = PlanetaryFeatureDefinition.builder("thermal_sink_zone")
        .displayName("Thermal Sink Zone")
        .description("Naturally heat-absorbing terrain")
        .texture(GalaxiaAPI.LocationGalaxia("textures/gui/station/features/thermal_sink_zone.png"))
        .layer(PlanetaryFeatureLayer.ENVIRONMENT)
        .placement(PlanetaryFeaturePlacement.patch(12.0, 5.0))
        .build();

    @Override
    public PlanetaryFeatureDefinition definition() {
        return DEFINITION;
    }
}
