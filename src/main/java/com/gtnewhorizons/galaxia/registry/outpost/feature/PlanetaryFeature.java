package com.gtnewhorizons.galaxia.registry.outpost.feature;

import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;

public interface PlanetaryFeature {

    PlanetaryFeatureDefinition definition();

    default PlanetaryFeatureKey key() {
        return definition().key();
    }

    default void applyModuleModifiers(FeatureModuleContext context, ModuleFeatureModifierBuilder builder) {}

    default void applyMiningEffects(FeatureMiningContext context, MiningFeatureEffects.Builder builder) {}

    default boolean isRequiredAnchorFeatureFor(FacilityModuleKind kind) {
        return false;
    }
}
