package com.gtnewhorizons.galaxia.registry.outpost.feature.types;

import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;

import com.gtnewhorizons.galaxia.api.GalaxiaAPI;
import com.gtnewhorizons.galaxia.client.EnumColors;
import com.gtnewhorizons.galaxia.registry.outpost.feature.FeatureContribution;
import com.gtnewhorizons.galaxia.registry.outpost.feature.FeatureMiningContext;
import com.gtnewhorizons.galaxia.registry.outpost.feature.FeatureModuleContext;
import com.gtnewhorizons.galaxia.registry.outpost.feature.MiningFeatureEffects;
import com.gtnewhorizons.galaxia.registry.outpost.feature.ModuleFeatureModifierBuilder;
import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeature;
import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeatureDefinition;
import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeatureLayer;
import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeaturePlacement;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;

public final class SubsurfaceIcePocketFeature implements PlanetaryFeature {

    public static final int POWER_DRAW_MULTIPLIER_PERCENT = 90;
    public static final int ICE_ROLL_CHANCE_PER_TILE_PERCENT = 20;

    private static final PlanetaryFeatureDefinition DEFINITION = PlanetaryFeatureDefinition
        .builder("subsurface_ice_pocket")
        .displayName("Subsurface Ice Pocket")
        .description("Buried ice deposit")
        .texture(GalaxiaAPI.LocationGalaxia("textures/gui/station/features/subsurface_ice_pocket.png"))
        .layer(PlanetaryFeatureLayer.RESOURCE)
        .placement(PlanetaryFeaturePlacement.patch(3.0, 1.0))
        .overlayColor(EnumColors.MAP_COLOR_STATION_FEATURE_SUBSURFACE_ICE_POCKET.getColor())
        .build();

    @Override
    public PlanetaryFeatureDefinition definition() {
        return DEFINITION;
    }

    @Override
    public void applyModuleModifiers(FeatureModuleContext context, ModuleFeatureModifierBuilder builder) {
        if (context.module()
            .kind()
            .isProductionModule()) {
            builder.minPowerDrawMultiplierPercent(POWER_DRAW_MULTIPLIER_PERCENT);
            builder.addContribution(
                new FeatureContribution(
                    key(),
                    (byte) context.coveredTiles(),
                    (byte) context.totalTiles(),
                    "Power draw -10%"));
            return;
        }
        if (context.module()
            .kind() == FacilityModuleKind.MINER) {
            builder.addContribution(
                new FeatureContribution(
                    key(),
                    (byte) context.coveredTiles(),
                    (byte) context.totalTiles(),
                    iceRollChancePercent(context.coveredTiles()) + "% ice roll chance"));
        }
    }

    @Override
    public void applyMiningEffects(FeatureMiningContext context, MiningFeatureEffects.Builder builder) {
        builder.addReplacementRoll(iceRollChancePercent(context.coveredTiles()), iceStack());
    }

    private static int iceRollChancePercent(int coveredTiles) {
        return Math.min(coveredTiles * ICE_ROLL_CHANCE_PER_TILE_PERCENT, 100);
    }

    private static ItemStack iceStack() {
        return new ItemStack(Blocks.ice);
    }
}
