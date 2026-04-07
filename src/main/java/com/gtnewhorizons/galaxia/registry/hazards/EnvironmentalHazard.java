package com.gtnewhorizons.galaxia.registry.hazards;

import net.minecraft.entity.player.EntityPlayer;

import com.gtnewhorizons.galaxia.core.config.ConfigPlayer;
import com.gtnewhorizons.galaxia.registry.dimension.builder.EffectBuilder;

public abstract class EnvironmentalHazard {

    public final int BASE_EFFECT_DURATION = 40;

    public HazardWarnings applyTotal(EffectBuilder def, EntityPlayer player) {
        if (player == null) return HazardWarnings.FINE;
        if (player.capabilities.isCreativeMode && !ConfigPlayer.ConfigPlayerGlobal.applyDebuffsInCreative)
            return HazardWarnings.FINE;
        return apply(def, player);
    }

    public abstract HazardWarnings apply(EffectBuilder def, EntityPlayer player);

}
