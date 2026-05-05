package com.gtnewhorizons.galaxia.client.gui.station;

import com.gtnewhorizons.galaxia.registry.outpost.module.HammerVariant;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleHammer;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;

final class HammerConfigRules {

    private HammerConfigRules() {}

    static ModuleTier nextTier(HammerVariant variant, ModuleTier current) {
        ModuleTier[] values = switch (variant) {
            case BASE -> new ModuleTier[] { ModuleTier.EV, ModuleTier.IV, ModuleTier.LuV };
            case BIG -> new ModuleTier[] { ModuleTier.LuV, ModuleTier.ZPM, ModuleTier.UV };
        };
        for (int i = 0; i < values.length; i++) {
            if (values[i] == current) return values[(i + 1) % values.length];
        }
        throw new IllegalStateException("Hammer variant " + variant + " does not support tier " + current);
    }

    static ModuleTier tierForVariantSwitch(HammerVariant targetVariant, ModuleTier currentTier) {
        return ModuleHammer.supportsTier(targetVariant, currentTier) ? currentTier : ModuleTier.LuV;
    }
}
