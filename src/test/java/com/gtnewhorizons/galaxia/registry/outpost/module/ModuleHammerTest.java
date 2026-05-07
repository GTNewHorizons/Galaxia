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
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

final class ModuleHammerTest {

    @BeforeAll
    static void initRegistries() {
        CelestialRegistry.freezeAndBake();
        FacilityModuleRegistry.init();
    }

    @Test
    void chargeBarFinishesOneSecondBeforeShot() {
        assertEquals(60 * 20 - 20, ModuleHammer.chargeTicks(HammerVariant.BASE, ModuleTier.EV));
        assertTrue(ModuleHammer.chargeRateEuPerTick(HammerVariant.BASE, ModuleTier.EV) * 1180L >= 500_000L);
        assertEquals(60 * 20 - 20, ModuleHammer.chargeTicks(HammerVariant.BIG, ModuleTier.LuV));
        assertTrue(ModuleHammer.chargeRateEuPerTick(HammerVariant.BIG, ModuleTier.LuV) * 1180L >= 8_000_000L);
    }

    @Test
    void hammerConsumesShotEnergyOnlyWhenCooldownCompletes() {
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

        for (int i = 0; i < 60 * 20 - 1; i++) {
            module.tick(outpost);
        }

        assertEquals(500_000L, outpost.getEnergyStored());
        assertFalse(hammer.canFire());

        module.tick(outpost);

        assertEquals(0L, outpost.getEnergyStored());
        assertTrue(hammer.canFire());
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
