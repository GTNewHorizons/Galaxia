package com.gtnewhorizons.galaxia.client.gui.station;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

final class ModuleBuildPickerModelTest {

    @BeforeAll
    static void initRegistries() {
        CelestialRegistry.freezeAndBake();
        FacilityModuleRegistry.init();
    }

    @Test
    void compatibleBuildTargetMustBeEmptyAdjacentTile() {
        AutomatedFacility facility = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.PANSPIRA,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);

        assertTrue(
            ModuleBuildPickerModel.isCompatibleTarget(
                facility,
                FacilityModuleKind.STORAGE,
                ModuleShape.SINGLE,
                ModuleTier.HV,
                StationTileCoord.of(1, 0)));
        assertFalse(
            ModuleBuildPickerModel.isCompatibleTarget(
                facility,
                FacilityModuleKind.STORAGE,
                ModuleShape.SINGLE,
                ModuleTier.HV,
                StationTileCoord.CORE));
        assertFalse(
            ModuleBuildPickerModel.isCompatibleTarget(
                facility,
                FacilityModuleKind.STORAGE,
                ModuleShape.SINGLE,
                ModuleTier.HV,
                StationTileCoord.of(5, 5)));
    }

    @Test
    void incompatibleModuleKindOrTierCannotBePicked() {
        AutomatedFacility facility = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.PANSPIRA,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);

        assertFalse(
            ModuleBuildPickerModel.isCompatibleTarget(
                facility,
                FacilityModuleKind.POWER,
                ModuleShape.SINGLE,
                ModuleTier.HV,
                StationTileCoord.of(1, 0)));
    }
}
