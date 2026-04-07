package com.gtnewhorizons.galaxia.utility.effects;

import com.gtnewhorizons.galaxia.utility.EnumColors;

public class EffectFreezing extends GalaxiaPotionEffect {

    public EffectFreezing(int id) {
        super(id, true, EnumColors.EffectBad.getColor(), "galaxia.effect.freezing", 2, 0);
    }
}
