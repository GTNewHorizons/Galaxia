package com.gtnewhorizons.galaxia.registry.outpost.feature.types;

import com.gtnewhorizons.galaxia.api.GalaxiaAPI;
import com.gtnewhorizons.galaxia.client.EnumColors;
import com.gtnewhorizons.galaxia.registry.outpost.feature.FeatureContribution;
import com.gtnewhorizons.galaxia.registry.outpost.feature.FeatureModuleContext;
import com.gtnewhorizons.galaxia.registry.outpost.feature.ModuleFeatureModifierBuilder;
import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeature;
import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeatureDefinition;
import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeatureLayer;
import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeaturePlacement;

public final class StableBedrockFeature implements PlanetaryFeature {

    public static final int UPKEEP_MULTIPLIER_PERCENT = 80;
    public static final int BUILD_SLOWDOWN_PERCENT = 20;

    private static final PlanetaryFeatureDefinition DEFINITION = PlanetaryFeatureDefinition.builder("stable_bedrock")
        .displayName("Stable Bedrock")
        .description("Structurally stable terrain")
        .texture(GalaxiaAPI.LocationGalaxia("textures/gui/station/features/stable_bedrock.png"))
        .layer(PlanetaryFeatureLayer.TERRAIN)
        .placement(PlanetaryFeaturePlacement.patch(30.0, 10.0))
        .overlayColor(EnumColors.MAP_COLOR_STATION_FEATURE_STABLE_BEDROCK.getColor())
        .build();

    @Override
    public PlanetaryFeatureDefinition definition() {
        return DEFINITION;
    }

    @Override
    public void applyModuleModifiers(FeatureModuleContext context, ModuleFeatureModifierBuilder builder) {
        builder.addBuildSpeedModifierPercent(-BUILD_SLOWDOWN_PERCENT);
        builder.minUpkeepMultiplierPercent(UPKEEP_MULTIPLIER_PERCENT);
        builder.addContribution(
            new FeatureContribution(
                key(),
                (byte) context.coveredTiles(),
                (byte) context.totalTiles(),
                "Upkeep x0.8, build speed -" + BUILD_SLOWDOWN_PERCENT + "%"));
    }
}
