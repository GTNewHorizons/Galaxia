package com.gtnewhorizons.galaxia.registry.outpost.feature.types;

import com.gtnewhorizons.galaxia.api.GalaxiaAPI;
import com.gtnewhorizons.galaxia.registry.outpost.feature.FeatureContribution;
import com.gtnewhorizons.galaxia.registry.outpost.feature.FeatureContributionFormatter;
import com.gtnewhorizons.galaxia.registry.outpost.feature.FeatureMiningContext;
import com.gtnewhorizons.galaxia.registry.outpost.feature.FeatureModuleContext;
import com.gtnewhorizons.galaxia.registry.outpost.feature.MiningFeatureEffects;
import com.gtnewhorizons.galaxia.registry.outpost.feature.ModuleFeatureModifierBuilder;
import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeature;
import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeatureDefinition;
import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeatureLayer;
import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeaturePlacement;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;

public final class StableBedrockFeature implements PlanetaryFeature {

    public static final int UPKEEP_MULTIPLIER_PERCENT = 80;
    public static final int BUILD_SLOWDOWN_PERCENT = 20;
    public static final int MINER_POWER_DRAW_INCREASE_PER_TILE_PERCENT = 10;
    public static final int MINER_OUTPUT_REDUCTION_PER_TILE_PERCENT = 10;

    private static final PlanetaryFeatureDefinition DEFINITION = PlanetaryFeatureDefinition.builder("stable_bedrock")
        .displayName("Stable Bedrock")
        .description("Structurally stable terrain")
        .texture(GalaxiaAPI.LocationGalaxia("textures/gui/station/features/stable_bedrock.png"))
        .layer(PlanetaryFeatureLayer.TERRAIN)
        .placement(PlanetaryFeaturePlacement.patch(30.0, 10.0))
        .build();

    @Override
    public PlanetaryFeatureDefinition definition() {
        return DEFINITION;
    }

    @Override
    public void applyModuleModifiers(FeatureModuleContext context, ModuleFeatureModifierBuilder builder) {
        builder.addBuildSpeedModifierPercent(-BUILD_SLOWDOWN_PERCENT);
        builder.minUpkeepMultiplierPercent(UPKEEP_MULTIPLIER_PERCENT);
        if (context.module()
            .kind() == FacilityModuleKind.MINER) {
            builder.multiplyPowerDrawMultiplierPercent(minerPowerDrawMultiplierPercent(context.coveredTiles()));
        }
        builder.addContribution(
            new FeatureContribution(
                key(),
                (byte) context.coveredTiles(),
                (byte) context.totalTiles(),
                effectLine(context)));
    }

    @Override
    public void applyMiningEffects(FeatureMiningContext context, MiningFeatureEffects.Builder builder) {
        builder.multiplyOutputMultiplierPercent(minerOutputMultiplierPercent(context.coveredTiles()));
    }

    private static int minerOutputMultiplierPercent(int coveredTiles) {
        return Math.max(0, 100 - coveredTiles * MINER_OUTPUT_REDUCTION_PER_TILE_PERCENT);
    }

    private static int minerPowerDrawMultiplierPercent(int coveredTiles) {
        return 100 + coveredTiles * MINER_POWER_DRAW_INCREASE_PER_TILE_PERCENT;
    }

    private static String effectLine(FeatureModuleContext context) {
        if (context.module()
            .kind() == FacilityModuleKind.MINER) {
            return FeatureContributionFormatter
                .percentMultiplierDelta("Mining output", minerOutputMultiplierPercent(context.coveredTiles())) + ", "
                + FeatureContributionFormatter
                    .percentDelta("power draw", context.coveredTiles() * MINER_POWER_DRAW_INCREASE_PER_TILE_PERCENT);
        }
        return FeatureContributionFormatter.percentMultiplierDelta("Upkeep", UPKEEP_MULTIPLIER_PERCENT) + ", "
            + FeatureContributionFormatter.percentDelta("build speed", -BUILD_SLOWDOWN_PERCENT);
    }
}
