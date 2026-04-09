package com.gtnewhorizons.galaxia.registry.effects;

import com.gtnewhorizons.galaxia.client.EnumColors;

public class EffectFreezing extends GalaxiaPotionEffect {

    public EffectFreezing(int id) {
        super(id, true, EnumColors.EffectBad.getColor(), "galaxia.effect.freezing", 2, 0);
    }
}
