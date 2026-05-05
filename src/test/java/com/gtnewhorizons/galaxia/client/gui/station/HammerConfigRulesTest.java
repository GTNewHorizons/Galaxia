package com.gtnewhorizons.galaxia.client.gui.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.outpost.module.HammerVariant;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;

final class HammerConfigRulesTest {

    @Test
    void baseHammerTierCycleMatchesAllowedTiers() {
        assertEquals(ModuleTier.IV, HammerConfigRules.nextTier(HammerVariant.BASE, ModuleTier.EV));
        assertEquals(ModuleTier.LuV, HammerConfigRules.nextTier(HammerVariant.BASE, ModuleTier.IV));
        assertEquals(ModuleTier.EV, HammerConfigRules.nextTier(HammerVariant.BASE, ModuleTier.LuV));
    }

    @Test
    void bigHammerTierCycleStartsAtLuv() {
        assertEquals(ModuleTier.ZPM, HammerConfigRules.nextTier(HammerVariant.BIG, ModuleTier.LuV));
        assertEquals(ModuleTier.UV, HammerConfigRules.nextTier(HammerVariant.BIG, ModuleTier.ZPM));
        assertEquals(ModuleTier.LuV, HammerConfigRules.nextTier(HammerVariant.BIG, ModuleTier.UV));
    }

    @Test
    void invalidHammerTierCrashes() {
        assertThrows(IllegalStateException.class, () -> HammerConfigRules.nextTier(HammerVariant.BIG, ModuleTier.EV));
    }

    @Test
    void variantSwitchClampsPlayerInputToLowestLegalTier() {
        assertEquals(ModuleTier.LuV, HammerConfigRules.tierForVariantSwitch(HammerVariant.BIG, ModuleTier.EV));
        assertEquals(ModuleTier.LuV, HammerConfigRules.tierForVariantSwitch(HammerVariant.BIG, ModuleTier.IV));
        assertEquals(ModuleTier.LuV, HammerConfigRules.tierForVariantSwitch(HammerVariant.BASE, ModuleTier.ZPM));
        assertEquals(ModuleTier.LuV, HammerConfigRules.tierForVariantSwitch(HammerVariant.BASE, ModuleTier.UV));
        assertEquals(ModuleTier.IV, HammerConfigRules.tierForVariantSwitch(HammerVariant.BASE, ModuleTier.IV));
        assertEquals(ModuleTier.ZPM, HammerConfigRules.tierForVariantSwitch(HammerVariant.BIG, ModuleTier.ZPM));
    }
}
