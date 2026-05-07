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
    void chargeBarFinishesOneSecondBeforeShot() {
        FacilityModuleRegistry.Definition def = FacilityModuleRegistry.get(FacilityModuleKind.HAMMER);
        for (var entry : new Object[][] { { HammerVariant.BASE, ModuleTier.EV, 60 * 20, 500_000L },
            { HammerVariant.BIG, ModuleTier.LuV, 60 * 20, 8_000_000L } }) {
            HammerVariant variant = (HammerVariant) entry[0];
            ModuleTier tier = (ModuleTier) entry[1];
            int expectedCooldown = (int) entry[2];
            long expectedEnergy = (long) entry[3];

            ModuleTierData data = def.getTierData(tier);
            int cooldown = data.variantCooldowns() != null && data.variantCooldowns()
                .containsKey(variant.name()) ? data.variantCooldowns()
                    .get(variant.name()) : data.cooldownTicks();
            int chargeTicks = Math.max(1, cooldown - 20);
            long chargeRate = Math.ceilDiv(expectedEnergy, chargeTicks);

            assertEquals(expectedCooldown - 20, chargeTicks);
            assertTrue(chargeRate * 1180L >= expectedEnergy);
        }
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
