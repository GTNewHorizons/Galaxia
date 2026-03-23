package com.gtnewhorizons.galaxia.utility.effects;

import com.gtnewhorizons.galaxia.utility.EnumColors;

public class EffectLowOxygen extends GalaxiaPotionEffect {

    public EffectLowOxygen(int id) {
        super(id, true, EnumColors.EffectBad.getColor(), "galaxia.effect.low_oxygen", 0, 0);
    }
}
