package com.gtnewhorizons.galaxia.registry.outpost;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeatureKey;
import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeatureRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

final class AutomatedFacilityFeatureModifierTest {

    @BeforeAll
    static void initRegistries() {
        CelestialRegistry.freezeAndBake();
        FacilityModuleRegistry.init();
    }

    @Test
    void icePocketReducesProductionModulePowerDraw() {
        AutomatedFacility facility = facilityWithModuleOnFeature(
            FacilityModuleKind.MACERATOR,
            ModuleTier.HV,
            PlanetaryFeatureRegistry.SUBSURFACE_ICE_POCKET.key());
        ModuleInstance module = facility.modules()
            .get(0);

        assertTrue(
            facility.featureModifiers(module)
                .powerDrawMultiplierPercent() < 100);
        long effectivePowerDraw = facility.effectivePowerDrawEuPerTick(module);
        assertTrue(effectivePowerDraw > 0);
        assertTrue(effectivePowerDraw < module.powerDrawEuPerTick());

        facility.setEnergyStored(1000L);
        facility.tick();

        assertEquals(1000L - effectivePowerDraw, facility.getEnergyStored());
    }

    private static AutomatedFacility facilityWithModuleOnFeature(FacilityModuleKind kind, ModuleTier tier,
        PlanetaryFeatureKey required) {
        for (long salt = 0; salt < 10_000L; salt++) {
            AutomatedFacility facility = new AutomatedFacility(
                CelestialAsset.ID.create(),
                CelestialObjectId.FROZEN_BELT,
                CelestialAsset.Kind.AUTOMATED_STATION,
                Buildable.Status.OPERATIONAL);
            facility.setStationFeatureSalt(salt);
            for (int dx = StationTileCoord.MIN; dx <= StationTileCoord.MAX; dx++) {
                for (int dy = StationTileCoord.MIN; dy <= StationTileCoord.MAX; dy++) {
                    StationTileCoord coord = StationTileCoord.of(dx, dy);
                    if (!facility.planetaryFeaturesAt(coord)
                        .contains(required)) continue;
                    ModuleInstance module = kind.create(coord, kind.defaultShape(), tier);
                    module.updateStatus(Buildable.Status.OPERATIONAL);
                    facility.addModule(module);
                    return facility;
                }
            }
        }
        throw new AssertionError("Could not find station salt for feature " + required);
    }
}
