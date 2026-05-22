package com.gtnewhorizons.galaxia.client.gui.station;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.HammerVariant;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleOperationPlan;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleOperationState;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleTierOperation;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class ModuleUpgradePickerModelTest {

    @BeforeAll
    static void initRegistries() {
        GalaxiaTestBootstrap.ensureFacilityModules();
    }

    @Test
    void hammerTargetMustBeInactiveHammerThatChangesSpec() {
        TestFacility test = twoModuleFacility(FacilityModuleKind.HAMMER, ModuleTier.EV);

        assertTrue(
            ModuleUpgradePickerModel.isCompatibleTarget(
                test.facility(),
                test.source(),
                ModuleTier.LuV,
                HammerVariant.BIG,
                test.source()
                    .anchor()));
        assertTrue(
            ModuleUpgradePickerModel.isCompatibleTarget(
                test.facility(),
                test.source(),
                ModuleTier.LuV,
                HammerVariant.BIG,
                test.target()
                    .anchor()));
        assertFalse(
            ModuleUpgradePickerModel.isCompatibleTarget(
                test.facility(),
                test.source(),
                ModuleTier.EV,
                HammerVariant.BASE,
                test.target()
                    .anchor()));
    }

    @Test
    void confirmedTargetsCanIncludeSourceModule() {
        TestFacility test = twoModuleFacility(FacilityModuleKind.HAMMER, ModuleTier.EV);

        List<StationTileCoord> targets = ModuleUpgradePickerModel.confirmedTargets(
            test.facility(),
            test.source(),
            ModuleTier.LuV,
            HammerVariant.BIG,
            List.of(
                test.source()
                    .anchor(),
                test.target()
                    .anchor()));

        assertEquals(
            List.of(
                test.source()
                    .anchor(),
                test.target()
                    .anchor()),
            targets);
    }

    @Test
    void activeBuildTargetIsNotCompatible() {
        TestFacility test = twoModuleFacility(FacilityModuleKind.STORAGE, ModuleTier.HV);
        test.target()
            .setOperation(
                ModuleOperationState
                    .waiting(new ModuleOperationPlan(new ModuleTierOperation(ModuleTier.EV), 1, Map.of(), false)));

        assertFalse(
            ModuleUpgradePickerModel.isCompatibleTarget(
                test.facility(),
                test.source(),
                ModuleTier.EV,
                null,
                test.target()
                    .anchor()));
    }

    @Test
    void normalizeTargetReturnsModuleAnchor() {
        TestFacility test = twoModuleFacility(FacilityModuleKind.STORAGE, ModuleTier.HV);

        assertEquals(
            test.target()
                .anchor(),
            ModuleUpgradePickerModel.normalizeTarget(
                test.facility(),
                test.target()
                    .anchor()));
    }

    @Test
    void confirmedTargetsDeduplicateByModuleId() {
        TestFacility test = twoModuleFacility(FacilityModuleKind.HAMMER, ModuleTier.EV);
        StationTileCoord duplicateAnchor = StationTileCoord.of(3, 0);
        test.facility()
            .stationLayout()
            .place(
                duplicateAnchor,
                new com.gtnewhorizons.galaxia.registry.outpost.station.PlacedTile(
                    test.target(),
                    com.gtnewhorizons.galaxia.registry.outpost.station.StationTileState.OCCUPIED_OPERATIONAL));

        List<StationTileCoord> targets = ModuleUpgradePickerModel.confirmedTargets(
            test.facility(),
            test.source(),
            ModuleTier.LuV,
            HammerVariant.BIG,
            List.of(
                test.target()
                    .anchor(),
                duplicateAnchor));

        assertEquals(
            List.of(
                test.target()
                    .anchor()),
            targets);
    }

    @Test
    void confirmedTargetsSkipTargetsThatBecameActiveBeforeConfirm() {
        TestFacility test = twoModuleFacility(FacilityModuleKind.HAMMER, ModuleTier.EV);
        test.target()
            .setOperation(
                ModuleOperationState
                    .waiting(new ModuleOperationPlan(new ModuleTierOperation(ModuleTier.IV), 1, Map.of(), false)));

        assertEquals(
            List.of(),
            ModuleUpgradePickerModel.confirmedTargets(
                test.facility(),
                test.source(),
                ModuleTier.LuV,
                HammerVariant.BIG,
                List.of(
                    test.target()
                        .anchor())));
    }

    private static TestFacility twoModuleFacility(FacilityModuleKind kind, ModuleTier tier) {
        AutomatedFacility facility = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.PANSPIRA,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
        ModuleInstance source = kind.create(StationTileCoord.of(1, 0), ModuleShape.SINGLE, tier);
        ModuleInstance target = kind.create(StationTileCoord.of(2, 0), ModuleShape.SINGLE, tier);
        facility.addModule(source);
        facility.addModule(target);
        facility.stationLayout()
            .place(source);
        facility.stationLayout()
            .place(target);
        return new TestFacility(facility, source, target);
    }

    private record TestFacility(AutomatedFacility facility, ModuleInstance source, ModuleInstance target) {}
}
