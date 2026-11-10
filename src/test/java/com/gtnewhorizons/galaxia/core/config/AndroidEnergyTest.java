package com.gtnewhorizons.galaxia.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class AndroidEnergyTest {

    /** Product contract: motor activity increases load while environmental support adds to every activity level. */
    @Test
    void motorsAndEnvironmentalSupportIncreaseLoadWithoutChargingStationarySprint() {
        ConfigMachines.Android settings = new ConfigMachines.Android();
        settings.baseEnergyPerTick = 3;
        settings.walkingEnergyPerTick = 5;
        settings.sprintingEnergyPerTick = 11;
        settings.heatingEnergyPerTick = 17;
        settings.zeroGEnergyPerTick = 23;
        long idle = settings.energyPerTick(false, false, false, false);
        long walk = settings.energyPerTick(true, false, false, false);
        long sprint = settings.energyPerTick(true, true, false, false);
        assertTrue(idle > 0);
        assertTrue(walk > idle);
        assertTrue(sprint > walk);
        assertEquals(idle, settings.energyPerTick(false, true, false, false));
        long heating = settings.energyPerTick(false, false, true, false) - idle;
        long zeroG = settings.energyPerTick(false, false, false, true) - idle;
        assertTrue(heating > 0);
        assertTrue(zeroG > 0);
        assertEquals(sprint + heating + zeroG, settings.energyPerTick(true, true, true, true));
    }
}
