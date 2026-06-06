package com.gtnewhorizons.galaxia.registry.effects;

import com.gtnewhorizons.galaxia.client.EnumColors;

public class EffectLowOxygen extends GalaxiaPotionEffect {

    public EffectLowOxygen(int id) {
        super(id, true, EnumColors.EffectBad.getColor(), "galaxia.effect.low_oxygen", 0, 0);
    }
}
