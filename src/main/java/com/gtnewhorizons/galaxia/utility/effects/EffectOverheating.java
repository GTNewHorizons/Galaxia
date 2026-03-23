package com.gtnewhorizons.galaxia.utility.effects;

import com.gtnewhorizons.galaxia.utility.EnumColors;

public class EffectOverheating extends GalaxiaPotionEffect {

    public EffectOverheating(int id) {
        super(id, true, EnumColors.EffectBad.getColor(), "galaxia.effect.overheating", 1, 0);
    }
}
