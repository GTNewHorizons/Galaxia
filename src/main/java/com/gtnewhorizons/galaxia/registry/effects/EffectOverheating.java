package com.gtnewhorizons.galaxia.registry.effects;

import com.gtnewhorizons.galaxia.client.EnumColors;

public class EffectOverheating extends GalaxiaPotionEffect {

    public EffectOverheating(int id) {
        super(id, true, EnumColors.EffectBad.getColor(), "galaxia.effect.overheating", 1, 0);
    }
}
