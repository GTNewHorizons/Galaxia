package com.gtnewhorizons.galaxia.registry.interfaces;

import com.gtnewhorizons.galaxia.registry.dimension.builder.EffectBuilder;
import com.gtnewhorizons.galaxia.registry.hazards.HazardWarnings;
import net.minecraft.entity.player.EntityPlayer;

public interface IEnvironmentalHazard {

    int BASE_EFFECT_DURATION = 40;

    HazardWarnings apply(EffectBuilder def, EntityPlayer player);
}
