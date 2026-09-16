package com.gtnewhorizons.galaxia.registry.outpost;

import static com.gtnewhorizons.galaxia.registry.outpost.FacilityTestFixtures.addModule;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeatureKey;
import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeatureRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class AutomatedFacilityFeatureModifierTest {

    @BeforeAll
    static void initRegistries() {
        GalaxiaTestBootstrap.ensureFacilityModules();
    }

    @Test
    void icePocketReducesSurfaceOutpostProductionModulePowerDraw() {
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

    @Test
    void orbitalStationDoesNotGeneratePlanetaryFeatures() {
        AutomatedFacility facility = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.FROZEN_BELT,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
        facility.setStationFeatureSalt(1L);

        for (int dx = StationTileCoord.MIN; dx <= StationTileCoord.MAX; dx++) {
            for (int dy = StationTileCoord.MIN; dy <= StationTileCoord.MAX; dy++) {
                assertTrue(
                    facility.planetaryFeaturesAt(dx, dy)
                        .isEmpty());
            }
        }
    }

    @Test
    void maintenanceCoverageAndGameplayRefreshTogetherAfterSourceChanges() {
        AutomatedFacility facility = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.OVERWORLD,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
        ModuleInstance bay = FacilityModuleKind.MAINTENANCE_BAY
            .create(StationTileCoord.of(5, 5), ModuleShape.SINGLE, ModuleTier.NONE);
        ModuleInstance target = FacilityModuleKind.STORAGE
            .create(StationTileCoord.of(6, 5), ModuleShape.SINGLE, ModuleTier.HV);
        addModule(facility, bay);
        addModule(facility, target);
        int discounted = facility.upkeepMultiplierPercent(target);
        assertTrue(discounted < 100);
        assertTrue(
            facility.layoutCache()
                .getMaintenanceCoverage()
                .contains(target.anchor()));

        bay.setEnabled(false);
        assertEquals(100, facility.upkeepMultiplierPercent(target));
        assertTrue(
            facility.layoutCache()
                .getMaintenanceCoverage()
                .isEmpty());

        bay.setEnabled(true);
        assertEquals(discounted, facility.upkeepMultiplierPercent(target));
        assertTrue(
            facility.layoutCache()
                .getMaintenanceCoverage()
                .contains(target.anchor()));

        facility.clearModules();
        assertEquals(100, facility.upkeepMultiplierPercent(target));
        assertTrue(
            facility.layoutCache()
                .getMaintenanceCoverage()
                .isEmpty());
    }

    private static AutomatedFacility facilityWithModuleOnFeature(FacilityModuleKind kind, ModuleTier tier,
        PlanetaryFeatureKey required) {
        for (long salt = 0; salt < 10_000L; salt++) {
            AutomatedFacility facility = new AutomatedFacility(
                CelestialAsset.ID.create(),
                CelestialObjectId.FROZEN_BELT,
                CelestialAsset.Kind.AUTOMATED_OUTPOST,
                Buildable.Status.OPERATIONAL);
            facility.setStationFeatureSalt(salt);
            for (int dx = StationTileCoord.MIN; dx <= StationTileCoord.MAX; dx++) {
                for (int dy = StationTileCoord.MIN; dy <= StationTileCoord.MAX; dy++) {
                    StationTileCoord coord = StationTileCoord.of(dx, dy);
                    if (!facility.planetaryFeaturesAt(coord)
                        .contains(required)) continue;
                    ModuleInstance module = kind.create(coord, kind.defaultShape(), tier);
                    module.updateStatus(Buildable.Status.OPERATIONAL);
                    addModule(facility, module);
                    return facility;
                }
            }
        }
        throw new AssertionError("Could not find station salt for feature " + required);
    }
}
