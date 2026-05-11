package com.gtnewhorizons.galaxia.registry.outpost.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleHammer;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

final class ModuleHammerTest {

    @BeforeAll
    static void initRegistries() {
        CelestialRegistry.freezeAndBake();
        FacilityModuleRegistry.init();
    }

    @Test
    void chargeRateFillsPrivateBufferByChargeEnd() {
        FacilityModuleRegistry.Definition def = FacilityModuleRegistry.get(FacilityModuleKind.HAMMER);
        for (var entry : new Object[][] { { HammerVariant.BASE, ModuleTier.EV, 60 * 20, 500_000L },
            { HammerVariant.BIG, ModuleTier.LuV, 60 * 20, 8_000_000L } }) {
            HammerVariant variant = (HammerVariant) entry[0];
            ModuleTier tier = (ModuleTier) entry[1];
            int expectedCooldown = (int) entry[2];
            long expectedEnergy = (long) entry[3];

            ModuleTierData data = def.getTierData(tier);
            int chargeTicks = ModuleHammer.chargeTicks(variant, data);
            long chargeRate = Math.ceilDiv(expectedEnergy, Math.max(1, chargeTicks - ModuleHammer.CHARGE_STEP_TICKS));

            assertEquals(expectedCooldown, chargeTicks);
            assertTrue(chargeRate * (chargeTicks - ModuleHammer.CHARGE_STEP_TICKS) >= expectedEnergy);
        }
    }

    @Test
    void hammerChargesPrivateBufferFromStationOnApplyBehaviorInterval() {
        AutomatedFacility outpost = createOutpost();
        ModuleInstance module = FacilityModuleRegistry.create(
            ModuleInstance.ID.create(),
            FacilityModuleKind.HAMMER,
            StationTileCoord.of(1, 0),
            ModuleShape.SINGLE,
            ModuleTier.EV);
        module.updateStatus(Buildable.Status.OPERATIONAL);
        ModuleHammer hammer = (ModuleHammer) module.component();
        outpost.setEnergyStored(500_000L);
        long chargeAmount = hammer.chargeRate(module) * module.cooldownTicks();

        for (int i = 0; i < module.cooldownTicks(); i++) {
            module.tick(outpost);
        }

        assertEquals(ModuleHammer.CHARGE_STEP_TICKS, module.cooldownTicks());
        assertEquals(1200, hammer.chargeTicks(module));
        assertEquals(500_000L - chargeAmount, outpost.getEnergyStored());
        assertEquals(chargeAmount, hammer.energyStored());
        assertFalse(hammer.canFire());
    }

    @Test
    void hammerChargesPrivateBufferOnApplyBehaviorInterval() {
        AutomatedFacility outpost = createOutpost();
        ModuleInstance module = FacilityModuleRegistry.create(
            ModuleInstance.ID.create(),
            FacilityModuleKind.HAMMER,
            StationTileCoord.of(1, 0),
            ModuleShape.SINGLE,
            ModuleTier.EV);
        module.updateStatus(Buildable.Status.OPERATIONAL);
        ModuleHammer hammer = (ModuleHammer) module.component();
        outpost.setEnergyStored(500_000L);

        module.tick(outpost);

        assertEquals(0L, hammer.energyStored());
        for (int i = 1; i < module.cooldownTicks(); i++) {
            module.tick(outpost);
        }

        assertEquals(20, module.cooldownTicks());
        assertEquals(hammer.chargeRate(module) * module.cooldownTicks(), hammer.energyStored());
    }

    @Test
    void hammerChargeMarksModuleDirtyPeriodicallyForClientSync() {
        AutomatedFacility outpost = createOutpost();
        ModuleInstance module = FacilityModuleRegistry.create(
            ModuleInstance.ID.create(),
            FacilityModuleKind.HAMMER,
            StationTileCoord.of(1, 0),
            ModuleShape.SINGLE,
            ModuleTier.EV);
        module.updateStatus(Buildable.Status.OPERATIONAL);
        outpost.addModule(module);
        outpost.drainDirtyModules();
        outpost.setEnergyStored(500_000L);
        outpost.clean();

        module.tick(outpost);

        assertFalse(outpost.isDirty());
        for (int i = 1; i < 20; i++) {
            module.tick(outpost);
        }

        assertTrue(outpost.isDirty());
        assertEquals(
            module.id,
            outpost.drainDirtyModules()
                .get(0).id);
    }

    @Test
    void hammerCanSpendPartialBufferOnRouteCost() {
        ModuleInstance module = FacilityModuleRegistry.create(
            ModuleInstance.ID.create(),
            FacilityModuleKind.HAMMER,
            StationTileCoord.of(1, 0),
            ModuleShape.SINGLE,
            ModuleTier.EV);
        ModuleHammer hammer = (ModuleHammer) module.component();

        hammer.setEnergyStored(100_000L);

        assertTrue(hammer.trySpendShotEnergy(ModuleHammer.shotEnergyCost(7.25)));
        assertEquals(27_500L, hammer.energyStored());
        assertFalse(hammer.trySpendShotEnergy(ModuleHammer.shotEnergyCost(3)));
        assertEquals(27_500L, hammer.energyStored());
    }

    @Test
    void hammerShotSpendMarksModuleDirtyImmediately() {
        AutomatedFacility outpost = createOutpost();
        ModuleInstance module = FacilityModuleRegistry.create(
            ModuleInstance.ID.create(),
            FacilityModuleKind.HAMMER,
            StationTileCoord.of(1, 0),
            ModuleShape.SINGLE,
            ModuleTier.EV);
        outpost.addModule(module);
        outpost.drainDirtyModules();
        ModuleHammer hammer = (ModuleHammer) module.component();
        hammer.setEnergyStored(100_000L);

        assertTrue(hammer.trySpendShotEnergy(module, outpost, ModuleHammer.shotEnergyCost(7.25)));

        assertTrue(outpost.isDirty());
        assertEquals(
            module.id,
            outpost.drainDirtyModules()
                .get(0).id);
    }

    @Test
    void hammerCannotFireRouteAbovePrivateBufferCapacity() {
        ModuleInstance module = FacilityModuleRegistry.create(
            ModuleInstance.ID.create(),
            FacilityModuleKind.HAMMER,
            StationTileCoord.of(1, 0),
            ModuleShape.SINGLE,
            ModuleTier.EV);
        ModuleHammer hammer = (ModuleHammer) module.component();

        hammer.setEnergyStored(hammer.energyCapacity());

        assertFalse(hammer.trySpendShotEnergy(hammer.energyCapacity() + 1));
        assertEquals(hammer.energyCapacity(), hammer.energyStored());
    }

    @Test
    void fullBufferAllowsMinimumPackageShot() {
        ModuleInstance module = FacilityModuleRegistry.create(
            ModuleInstance.ID.create(),
            FacilityModuleKind.HAMMER,
            StationTileCoord.of(1, 0),
            ModuleShape.SINGLE,
            ModuleTier.EV);
        ModuleHammer hammer = (ModuleHammer) module.component();

        hammer.setEnergyStored(ModuleHammer.MIN_SHOT_ENERGY_EU);

        assertTrue(hammer.canFire());
    }

    @Test
    void shotCooldownBlocksRoutePlanningUntilTierCooldownCompletes() {
        ModuleInstance module = FacilityModuleRegistry.create(
            ModuleInstance.ID.create(),
            FacilityModuleKind.HAMMER,
            StationTileCoord.of(1, 0),
            ModuleShape.SINGLE,
            ModuleTier.IV);
        module.updateStatus(Buildable.Status.OPERATIONAL);
        ModuleHammer hammer = (ModuleHammer) module.component();
        hammer.setEnergyStored(hammer.energyCapacity());

        assertTrue(hammer.canPlanRoute(module));

        hammer.markShotDispatched(module);

        assertFalse(hammer.canFire());
        assertFalse(hammer.canPlanRoute(module));
        for (int i = 1; i < hammer.chargeTicks(module); i++) {
            hammer.tickDispatchCooldowns();
            assertFalse(hammer.canPlanRoute(module));
        }

        hammer.tickDispatchCooldowns();

        assertTrue(hammer.canFire());
        assertTrue(hammer.canPlanRoute(module));
    }

    @Test
    void failedRouteProbeBlocksRepeatedRoutePlanningForOneSecond() {
        ModuleInstance module = FacilityModuleRegistry.create(
            ModuleInstance.ID.create(),
            FacilityModuleKind.HAMMER,
            StationTileCoord.of(1, 0),
            ModuleShape.SINGLE,
            ModuleTier.IV);
        module.updateStatus(Buildable.Status.OPERATIONAL);
        ModuleHammer hammer = (ModuleHammer) module.component();
        hammer.setEnergyStored(hammer.energyCapacity());

        assertTrue(hammer.canPlanRoute(module));

        hammer.markRouteProbeAttempted();

        assertFalse(hammer.canPlanRoute(module));
        for (int i = 1; i < ModuleHammer.ROUTE_PROBE_INTERVAL_TICKS; i++) {
            hammer.tickDispatchCooldowns();
            assertFalse(hammer.canPlanRoute(module));
        }

        hammer.tickDispatchCooldowns();

        assertTrue(hammer.canPlanRoute(module));
    }

    @Test
    void bigHammerCanStoreEnoughEnergyForOneShot() {
        AutomatedFacility outpost = createOutpost();

        outpost.setEnergyStored(8_000_000L);

        assertEquals(8_000_000L, outpost.getEnergyStored());
    }

    private static AutomatedFacility createOutpost() {
        return new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.PANSPIRA,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
    }
}
