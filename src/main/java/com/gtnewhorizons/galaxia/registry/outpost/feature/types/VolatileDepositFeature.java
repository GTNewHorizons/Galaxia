package com.gtnewhorizons.galaxia.registry.outpost.feature.types;

import java.util.List;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import com.gtnewhorizons.galaxia.api.GalaxiaAPI;
import com.gtnewhorizons.galaxia.compat.GTUtility;
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

public final class VolatileDepositFeature implements PlanetaryFeature {

    private static final String[] MATERIALS = { "Sulfur", "Saltpeter", "Naquadah" };

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

    @Override
    public void applyMiningEffects(FeatureMiningContext context, MiningFeatureEffects.Builder builder) {
        builder.addCandidates(miningPool(), context.coveredTiles());
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
                "Volatile resource pool"));
    }

    private static List<ItemStack> miningPool() {
        List<ItemStack> pool = GTUtility.getRawOreStacks(MATERIALS);
        if (pool.isEmpty()) {
            pool.add(new ItemStack(Items.gunpowder));
            pool.add(new ItemStack(Items.coal));
            pool.add(new ItemStack(Items.redstone));
        }
        return pool;
    }
}
