package com.gtnewhorizons.galaxia.registry.outpost.feature.types;

import com.gtnewhorizons.galaxia.api.GalaxiaAPI;
import com.gtnewhorizons.galaxia.registry.outpost.feature.FeatureContribution;
import com.gtnewhorizons.galaxia.registry.outpost.feature.FeatureContributionFormatter;
import com.gtnewhorizons.galaxia.registry.outpost.feature.FeatureModuleContext;
import com.gtnewhorizons.galaxia.registry.outpost.feature.ModuleFeatureModifierBuilder;
import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeature;
import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeatureDefinition;
import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeatureLayer;
import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeaturePlacement;

public final class RegolithFlatsFeature implements PlanetaryFeature {

    public static final int BUILD_SPEEDUP_PERCENT = 20;

    private static final PlanetaryFeatureDefinition DEFINITION = PlanetaryFeatureDefinition.builder("regolith_flats")
        .displayName("Regolith Flats")
        .description("Flat construction terrain")
        .texture(GalaxiaAPI.LocationGalaxia("textures/gui/station/features/regolith_flats.png"))
        .layer(PlanetaryFeatureLayer.TERRAIN)
        .placement(PlanetaryFeaturePlacement.patch(42.0, 14.0))
        .build();

    @Override
    public PlanetaryFeatureDefinition definition() {
        return DEFINITION;
    }

    @Override
    public void applyModuleModifiers(FeatureModuleContext context, ModuleFeatureModifierBuilder builder) {
        builder.addBuildSpeedModifierPercent(BUILD_SPEEDUP_PERCENT);
        builder.addContribution(
            new FeatureContribution(
                key(),
                (byte) context.coveredTiles(),
                (byte) context.totalTiles(),
                FeatureContributionFormatter.percentDelta("Build speed", BUILD_SPEEDUP_PERCENT)));
    }
}
