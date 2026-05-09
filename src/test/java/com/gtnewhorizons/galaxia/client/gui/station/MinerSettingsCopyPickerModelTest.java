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
import com.gtnewhorizons.galaxia.registry.outpost.module.MinerFocusTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleMiner;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

final class MinerSettingsCopyPickerModelTest {

    @BeforeAll
    static void initRegistries() {
        CelestialRegistry.freezeAndBake();
        FacilityModuleRegistry.init();
    }

    @Test
    void normalizeTargetReturnsModuleAnchorForOccupiedTile() {
        TestFacility test = twoMinerFacility();

        assertEquals(
            test.target()
                .anchor(),
            MinerSettingsCopyPickerModel.normalizeTarget(
                test.facility(),
                test.target()
                    .anchor()));
    }

    @Test
    void compatibleTargetMustBeDifferentMinerWithRequiredFocusTier() {
        TestFacility test = twoMinerFacility();
        ModuleMiner sourceMiner = (ModuleMiner) test.source()
            .component();
        ModuleMiner targetMiner = (ModuleMiner) test.target()
            .component();
        sourceMiner.setFocus(MinerFocusTier.I, "ore:iron", 0);
        targetMiner.setFocus(MinerFocusTier.NONE, null, 0);

        assertFalse(
            MinerSettingsCopyPickerModel.isCompatibleTarget(
                test.facility(),
                test.source(),
                test.source()
                    .anchor()));
        assertFalse(
            MinerSettingsCopyPickerModel.isCompatibleTarget(
                test.facility(),
                test.source(),
                test.target()
                    .anchor()));

        targetMiner.setFocus(MinerFocusTier.I, null, 0);

        assertTrue(
            MinerSettingsCopyPickerModel.isCompatibleTarget(
                test.facility(),
                test.source(),
                test.target()
                    .anchor()));
    }

    private static TestFacility twoMinerFacility() {
        AutomatedFacility facility = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.PANSPIRA,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
        ModuleInstance source = FacilityModuleKind.MINER
            .create(StationTileCoord.of(1, 0), ModuleShape.SINGLE, ModuleTier.EV);
        ModuleInstance target = FacilityModuleKind.MINER
            .create(StationTileCoord.of(2, 0), ModuleShape.SINGLE, ModuleTier.EV);
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
