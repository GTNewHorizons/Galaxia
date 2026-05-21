package com.gtnewhorizons.galaxia.registry.outpost.feature.types;

import java.util.List;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import com.gtnewhorizons.galaxia.api.GalaxiaAPI;
import com.gtnewhorizons.galaxia.compat.GTUtility;
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

public final class RareCrystalFormationFeature implements PlanetaryFeature {

    private static final String[] MATERIALS = { "Diamond", "Emerald", "Ruby", "Sapphire" };

    private static final PlanetaryFeatureDefinition DEFINITION = PlanetaryFeatureDefinition
        .builder("rare_crystal_formation")
        .displayName("Rare Crystal Formation")
        .description("Rare crystal growth")
        .texture(GalaxiaAPI.LocationGalaxia("textures/gui/station/features/rare_crystal_formation.png"))
        .layer(PlanetaryFeatureLayer.RESOURCE)
        .placement(PlanetaryFeaturePlacement.line(5.0, 1.0))
        .build();

    @Override
    public PlanetaryFeatureDefinition definition() {
        return DEFINITION;
    }

    @Override
    public void applyMiningEffects(FeatureMiningContext context, MiningFeatureEffects.Builder builder) {
        builder.addCandidates(miningPool(), gemPoolWeight(context.coveredTiles()));
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
                FeatureContributionFormatter.bonus("Gem pool weight", gemPoolWeight(context.coveredTiles()))));
    }

    private static int gemPoolWeight(int coveredTiles) {
        return coveredTiles + 1;
    }

    private static List<ItemStack> miningPool() {
        List<ItemStack> pool = GTUtility.getRawOreStacks(MATERIALS);
        if (pool.isEmpty()) {
            pool.add(new ItemStack(Items.diamond));
            pool.add(new ItemStack(Items.emerald));
        }
        return pool;
    }
}
