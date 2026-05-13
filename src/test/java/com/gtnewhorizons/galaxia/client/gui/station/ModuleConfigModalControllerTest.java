package com.gtnewhorizons.galaxia.client.gui.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.cleanroommc.modularui.screen.ModularPanel;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.MinerFocusTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

final class ModuleConfigModalControllerTest {

    private static final UUID TEAM_ID = UUID.randomUUID();

    @BeforeAll
    static void initRegistries() {
        CelestialRegistry.freezeAndBake();
        FacilityModuleRegistry.init();
    }

    @BeforeEach
    void clearClientStore() {
        CelestialAssetStore.CLIENT.clearInternal();
    }

    @Test
    void moduleOperationCancelConfirmationCanBeArmedAndCleared() {
        ModuleConfigModalController controller = new ModuleConfigModalController(
            null,
            CelestialAsset.ID.create(),
            0,
            0);

        assertFalse(controller.isModuleOperationCancelArmed());

        controller.armModuleOperationCancel();

        assertTrue(controller.isModuleOperationCancelArmed());

        controller.clearModuleOperationCancel();

        assertFalse(controller.isModuleOperationCancelArmed());
    }

    @Test
    void retargetingSameMinerUpgradeModulePreservesSelectedFocusTier() {
        TestFacility test = facilityWith(FacilityModuleKind.MINER, ModuleTier.EV);
        ModuleConfigModalController controller = controllerFor(test.facility());
        controller.openUpgrade(0);
        controller.selectModuleUpgradeOption(ModuleUpgradeUiModel.GROUP_MINER_FOCUS_TIER, MinerFocusTier.III.name());

        controller.retargetTo(test.module());

        assertEquals(
            MinerFocusTier.III.name(),
            controller.moduleUpgradeSelection()
                .get(ModuleUpgradeUiModel.GROUP_MINER_FOCUS_TIER));
    }

    @Test
    void retargetingSameHammerUpgradeModulePreservesSelectedTierAndFlags() {
        TestFacility test = facilityWith(FacilityModuleKind.HAMMER, ModuleTier.EV);
        ModuleConfigModalController controller = controllerFor(test.facility());
        controller.openUpgrade(0);
        controller.selectModuleUpgradeOption(ModuleUpgradeUiModel.GROUP_HAMMER_TIER, ModuleTier.IV.name());
        controller.toggleHammerUpgradeReserveItems();
        controller.toggleHammerUpgradeVoidRefund();

        controller.retargetTo(test.module());

        assertEquals(
            ModuleTier.IV.name(),
            controller.moduleUpgradeSelection()
                .get(ModuleUpgradeUiModel.GROUP_HAMMER_TIER));
        assertTrue(controller.hammerUpgradeReserveItems());
        assertTrue(controller.hammerUpgradeVoidRefund());
    }

    @Test
    void requestedRetargetIsDeferredUntilUpdatePass() {
        TestFacility test = facilityWith(FacilityModuleKind.HAMMER, ModuleTier.EV);
        ModuleInstance miner = FacilityModuleKind.MINER
            .create(StationTileCoord.of(2, 0), ModuleShape.SINGLE, ModuleTier.EV);
        test.facility()
            .addModule(miner);
        test.facility()
            .stationLayout()
            .place(miner);
        ModuleConfigModalController controller = controllerFor(test.facility());
        controller.openHammer(0);

        controller.requestRetargetTo(miner);

        assertTrue(controller.isHammerOpen());

        controller.processDeferredActions();

        assertFalse(controller.isOpen());
    }

    @Test
    void openingSameUpgradeTwiceClosesIt() {
        TestFacility test = facilityWith(FacilityModuleKind.HAMMER, ModuleTier.EV);
        ModuleConfigModalController controller = controllerFor(test.facility());

        controller.openUpgrade(0);
        assertTrue(controller.isModuleUpgradeOpen());

        controller.openUpgrade(0);
        assertFalse(controller.isOpen());
    }

    private static ModuleConfigModalController controllerFor(AutomatedFacility facility) {
        CelestialAssetStore.CLIENT.registerAssetInternal(TEAM_ID, facility);
        return new ModuleConfigModalController(
            ModularPanel.defaultPanel("test_module_config_modal_controller", 800, 600),
            facility.assetId,
            0,
            0);
    }

    private static TestFacility facilityWith(FacilityModuleKind kind, ModuleTier tier) {
        AutomatedFacility facility = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.PANSPIRA,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
        ModuleInstance module = kind.create(StationTileCoord.of(1, 0), ModuleShape.SINGLE, tier);
        facility.addModule(module);
        facility.stationLayout()
            .place(module);
        return new TestFacility(facility, module);
    }

    private record TestFacility(AutomatedFacility facility, ModuleInstance module) {}
}
