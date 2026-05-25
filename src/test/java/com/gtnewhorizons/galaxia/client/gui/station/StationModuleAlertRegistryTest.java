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

import com.gtnewhorizons.galaxia.client.EnumTextures;
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
import com.gtnewhorizons.galaxia.registry.outpost.module.ModulePriority;
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
        StationModuleAlert alert = StationModuleAlert
            .warning("Test", "Registered alert", EnumTextures.ICON_STATION_ALERT_WARNING.get());

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
            StationModuleAlert.Severity.YELLOW,
            alerts.get(0)
                .severity());
    }

    @Test
    void blockedUpkeepShortageIsRedAlert() {
        AutomatedFacility facility = createFacility();
        ModuleInstance module = moduleWithUpkeep(FacilityModuleKind.POWER, StationTileCoord.of(1, 0), 1L);
        facility.addModule(module);

        tickUpkeepMinute(facility);

        List<StationModuleAlert> alerts = StationModuleAlertRegistry.alertsFor(facility, module);
        assertFalse(alerts.isEmpty());
        assertEquals(
            StationModuleAlert.Severity.RED,
            alerts.get(0)
                .severity());
    }

    @Test
    void redAlertsAreOrderedBeforeYellowAlerts() {
        AutomatedFacility facility = createFacility();
        ModuleInstance module = createModule(FacilityModuleKind.POWER, StationTileCoord.of(1, 0));
        StationModuleAlert yellow = StationModuleAlert
            .warning("Yellow", "Yellow alert", EnumTextures.ICON_STATION_ALERT_WARNING.get());
        StationModuleAlert red = StationModuleAlert
            .critical("Red", "Red alert", EnumTextures.ICON_STATION_ALERT_ERROR.get());

        try (StationModuleAlertRegistry.Registration ignored = StationModuleAlertRegistry
            .register((f, m) -> m == module ? List.of(yellow, red) : List.of())) {
            List<StationModuleAlert> alerts = StationModuleAlertRegistry.alertsFor(facility, module);

            assertEquals(List.of(red, yellow), alerts);
        }
    }

    @Test
    void upkeepWarningClearsWhenInventoryCoversCurrentUpkeep() {
        AutomatedFacility facility = createFacility();
        ModuleInstance module = moduleWithUpkeep(FacilityModuleKind.POWER, StationTileCoord.of(1, 0), 1L);
        facility.addModule(module);
        facility.updateItems(ItemStackWrapper.of(new ItemStack(Items.iron_ingot)), 1);

        List<StationModuleAlert> alerts = StationModuleAlertRegistry.alertsFor(facility, module);

        assertEquals(List.of(), alerts);
    }

    @Test
    void upkeepWarningUsesFullPrioritySettlement() {
        AutomatedFacility facility = createFacility();
        ModuleInstance high = moduleWithUpkeep(FacilityModuleKind.POWER, StationTileCoord.of(1, 0), 1L);
        ModuleInstance low = moduleWithUpkeep(FacilityModuleKind.POWER, StationTileCoord.of(2, 0), 1L);
        high.setPriorityOverride(ModulePriority.HIGH);
        low.setPriorityOverride(ModulePriority.LOW);
        facility.addModule(high);
        facility.addModule(low);
        facility.updateItems(ItemStackWrapper.of(new ItemStack(Items.iron_ingot)), 1);

        assertEquals(List.of(), StationModuleAlertRegistry.alertsFor(facility, high));
        List<StationModuleAlert> lowAlerts = StationModuleAlertRegistry.alertsFor(facility, low);

        assertFalse(lowAlerts.isEmpty());
        assertEquals(
            StationModuleAlert.Severity.YELLOW,
            lowAlerts.get(0)
                .severity());
    }

    @Test
    void coveredUpkeepStillConsumesInventoryOnMinuteTick() {
        AutomatedFacility facility = createFacility();
        ModuleInstance module = moduleWithUpkeep(FacilityModuleKind.POWER, StationTileCoord.of(1, 0), 1L);
        ItemStackWrapper upkeepItem = ItemStackWrapper.of(new ItemStack(Items.iron_ingot));
        facility.addModule(module);
        facility.updateItems(upkeepItem, 1);

        assertEquals(List.of(), StationModuleAlertRegistry.alertsFor(facility, module));

        tickUpkeepMinute(facility);

        assertEquals(0L, facility.getItemAmount(upkeepItem));
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

    private static void tickUpkeepMinute(AutomatedFacility facility) {
        for (int i = 0; i < AutomatedFacility.UPKEEP_INTERVAL_TICKS; i++) {
            facility.tick();
        }
    }

    private static final class TestTieredModule extends TieredModuleComponent {
    }
}
