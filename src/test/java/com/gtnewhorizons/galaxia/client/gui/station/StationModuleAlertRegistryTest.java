package com.gtnewhorizons.galaxia.client.gui.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.interfaces.TieredModuleComponent;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModulePanelAction;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTierData;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class StationModuleAlertRegistryTest {

    @BeforeAll
    static void initRegistries() {
        GalaxiaTestBootstrap.ensureFacilityModules();
    }

    @Test
    void registeredProviderCanAttachAlertToModule() {
        AutomatedFacility facility = createFacility();
        ModuleInstance module = createModule(FacilityModuleKind.POWER, StationTileCoord.of(1, 0));
        facility.addModule(module);
        StationModuleAlert alert = StationModuleAlert.warning("Test", "Registered alert", new ItemStack(Items.paper));

        try (StationModuleAlertRegistry.Registration ignored = StationModuleAlertRegistry
            .register((f, m) -> m == module ? List.of(alert) : List.of())) {
            List<StationModuleAlert> alerts = StationModuleAlertRegistry.alertsFor(facility, module);

            assertTrue(alerts.contains(alert));
        }
    }

    @Test
    void upkeepWarningAppearsWhenModuleCannotCoverCurrentUpkeep() {
        AutomatedFacility facility = createFacility();
        ModuleInstance module = moduleWithUpkeep(FacilityModuleKind.POWER, StationTileCoord.of(1, 0), 1L);
        facility.addModule(module);

        List<StationModuleAlert> alerts = StationModuleAlertRegistry.alertsFor(facility, module);

        assertFalse(alerts.isEmpty());
        assertEquals(
            StationModuleAlert.Severity.WARNING,
            alerts.get(0)
                .severity());
    }

    @Test
    void upkeepWarningClearsWhenInventoryCoversCurrentUpkeep() {
        AutomatedFacility facility = createFacility();
        ModuleInstance module = moduleWithUpkeep(FacilityModuleKind.POWER, StationTileCoord.of(1, 0), 1L);
        facility.addModule(module);
        facility.addInventory(ItemStackWrapper.of(new ItemStack(Items.iron_ingot)), 1L);

        List<StationModuleAlert> alerts = StationModuleAlertRegistry.alertsFor(facility, module);

        assertEquals(List.of(), alerts);
    }

    private static AutomatedFacility createFacility() {
        return new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.PROXIMA_CENTAURI,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
    }

    private static ModuleInstance createModule(FacilityModuleKind kind, StationTileCoord anchor) {
        ModuleInstance module = FacilityModuleRegistry
            .create(ModuleInstance.ID.create(), kind, anchor, ModuleShape.SINGLE, kind.defaultTier());
        module.updateStatus(Buildable.Status.OPERATIONAL);
        return module;
    }

    private static ModuleInstance moduleWithUpkeep(FacilityModuleKind kind, StationTileCoord anchor, long itemAmount) {
        ModuleTierData tierData = ModuleTierData.builder()
            .addedEnergyCapacity(0L)
            .powerDraw(0L)
            .cooldown(20)
            .cost(Map.of(new ItemStack(Items.iron_ingot), 1L))
            .upkeepItem(new ItemStack(Items.iron_ingot), itemAmount)
            .build();
        FacilityModuleRegistry.Definition definition = new FacilityModuleRegistry.Definition(
            kind,
            Map.of(ModuleTier.NONE, tierData),
            (module, facility) -> {},
            TestTieredModule::new,
            List.<ModulePanelAction>of(),
            false,
            List.of());
        ModuleInstance module = new ModuleInstance(
            ModuleInstance.ID.create(),
            definition,
            anchor,
            ModuleShape.SINGLE,
            ModuleTier.NONE);
        module.setComponent(new TestTieredModule());
        module.updateStatus(Buildable.Status.OPERATIONAL);
        return module;
    }

    private static final class TestTieredModule extends TieredModuleComponent {
    }
}
