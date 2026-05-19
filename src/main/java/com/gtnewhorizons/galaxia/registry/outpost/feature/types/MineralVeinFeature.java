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

public final class MineralVeinFeature implements PlanetaryFeature {

    private static final PlanetaryFeatureDefinition DEFINITION = PlanetaryFeatureDefinition.builder("mineral_vein")
        .displayName("Mineral Vein")
        .description("Ore-rich tile")
        .texture(GalaxiaAPI.LocationGalaxia("textures/gui/station/features/mineral_vein.png"))
        .layer(PlanetaryFeatureLayer.RESOURCE)
        .placement(PlanetaryFeaturePlacement.patch(14.0, 6.0))
        .build();

    @Override
    public PlanetaryFeatureDefinition definition() {
        return DEFINITION;
    }

    @Override
    public void applyMiningEffects(FeatureMiningContext context, MiningFeatureEffects.Builder builder) {
        builder.addBonusRolls(context.coveredTiles());
    }

    @Override
    public void applyModuleModifiers(FeatureModuleContext context, ModuleFeatureModifierBuilder builder) {
        if (context.module()
            .kind() != FacilityModuleKind.MINER) return;
        builder.addContribution(
            new FeatureContribution(
                key(),
                (byte) context.coveredTiles(),
                (byte) context.totalTiles(),
                FeatureContributionFormatter.perTickBonus("Mining rolls", context.coveredTiles())));
    }
}
