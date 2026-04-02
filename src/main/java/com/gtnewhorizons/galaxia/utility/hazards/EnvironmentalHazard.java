package com.gtnewhorizons.galaxia.utility.hazards;

import net.minecraft.entity.player.EntityPlayer;

import com.gtnewhorizons.galaxia.registry.dimension.builder.EffectBuilder;

public abstract class EnvironmentalHazard {

    public final int BASE_EFFECT_DURATION = 40;

    public abstract HazardWarnings apply(EffectBuilder def, EntityPlayer player);

}
