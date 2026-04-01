package com.gtnewhorizons.galaxia.utility.effects;

import com.gtnewhorizons.galaxia.utility.EnumColors;

public class EffectOvercooling extends GalaxiaPotionEffect {

    public EffectOvercooling(int id) {
        super(id, true, EnumColors.EffectBad.getColor(), "galaxia.effect.overcooling", 2, 0);
    }
}
