package com.gtnewhorizons.galaxia.registry.outpost.feature;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

final class PlanetaryFeatureBehaviorTest {

    @BeforeAll
    static void initRegistries() {
        CelestialRegistry.freezeAndBake();
        FacilityModuleRegistry.init();
    }

    @Test
    void registryReturnsBehaviorObjectForFeatureKey() {
        PlanetaryFeature feature = PlanetaryFeatureRegistry
            .feature(PlanetaryFeatureRegistry.SUBSURFACE_ICE_POCKET.key());

        assertNotNull(feature);
        assertSame(PlanetaryFeatureRegistry.SUBSURFACE_ICE_POCKET, feature.definition());
    }

    @Test
    void icePocketFeatureOwnsProductionPowerModifier() {
        PlanetaryFeature feature = PlanetaryFeatureRegistry
            .feature(PlanetaryFeatureRegistry.SUBSURFACE_ICE_POCKET.key());
        ModuleInstance module = FacilityModuleRegistry.create(
            ModuleInstance.ID.create(),
            FacilityModuleKind.MACERATOR,
            StationTileCoord.of(0, 0),
            ModuleShape.SINGLE,
            ModuleTier.HV);
        ModuleFeatureModifierBuilder builder = new ModuleFeatureModifierBuilder();

        feature.applyModuleModifiers(new FeatureModuleContext(module, feature.key(), 1, 1), builder);

        assertTrue(
            builder.build(java.util.Map.of(feature.key(), 1))
                .powerDrawMultiplierPercent() < 100);
    }

    @Test
    void mineralVeinFeatureOwnsMiningBonusRolls() {
        PlanetaryFeature feature = PlanetaryFeatureRegistry.feature(PlanetaryFeatureRegistry.MINERAL_VEIN.key());
        ModuleInstance module = FacilityModuleRegistry.create(
            ModuleInstance.ID.create(),
            FacilityModuleKind.MINER,
            StationTileCoord.of(0, 0),
            ModuleShape.QUAD_2x2,
            ModuleTier.HV);
        MiningFeatureEffects.Builder builder = MiningFeatureEffects.builder();

        feature.applyMiningEffects(new FeatureMiningContext(module, feature.key(), 3, 4), builder);

        assertTrue(
            builder.build()
                .bonusRolls() > 0);
    }

}
