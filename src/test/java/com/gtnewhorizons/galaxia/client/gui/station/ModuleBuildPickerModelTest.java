package com.gtnewhorizons.galaxia.client.gui.station;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

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

    @Test
    void selectedBuildTargetsUnlockAdjacentTiles() {
        AutomatedFacility facility = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.PANSPIRA,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);

        StationTileCoord first = StationTileCoord.of(1, 0);
        StationTileCoord chained = StationTileCoord.of(2, 0);

        assertFalse(
            ModuleBuildPickerModel
                .isCompatibleTarget(facility, FacilityModuleKind.STORAGE, ModuleShape.SINGLE, ModuleTier.HV, chained));
        assertTrue(
            ModuleBuildPickerModel.isCompatibleTarget(
                facility,
                FacilityModuleKind.STORAGE,
                ModuleShape.SINGLE,
                ModuleTier.HV,
                chained,
                List.of(first)));
        assertFalse(
            ModuleBuildPickerModel.isCompatibleTarget(
                facility,
                FacilityModuleKind.STORAGE,
                ModuleShape.SINGLE,
                ModuleTier.HV,
                first,
                List.of(first)));
    }

    @Test
    void disconnectedBuildTargetsArePrunedAfterSelectionChanges() {
        AutomatedFacility facility = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.PANSPIRA,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);

        StationTileCoord first = StationTileCoord.of(1, 0);
        StationTileCoord second = StationTileCoord.of(2, 0);
        StationTileCoord third = StationTileCoord.of(3, 0);

        assertEquals(
            List.of(first, second, third),
            ModuleBuildPickerModel.connectedTargets(facility, List.of(first, second, third)));
        assertEquals(List.of(), ModuleBuildPickerModel.connectedTargets(facility, List.of(second, third)));
        assertEquals(List.of(first), ModuleBuildPickerModel.connectedTargets(facility, List.of(first, third)));
    }
}
