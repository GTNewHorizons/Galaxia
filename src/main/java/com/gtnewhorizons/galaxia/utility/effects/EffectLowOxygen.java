package com.gtnewhorizons.galaxia.utility.effects;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.potion.Potion;

import com.gtnewhorizons.galaxia.utility.EnumColors;

public class EffectLowOxygen extends Potion {

    public EffectLowOxygen(int id) {
        super(id, true, EnumColors.EffectBad.getColor()); // isBadEffect=true
        setPotionName("galaxia.effect.low_oxygen");
        setIconIndex(0, 0);
    }

    @Override
    public void performEffect(EntityLivingBase entity, int amplifier) {}

    @Override
    public boolean isReady(int duration, int amplifier) {
        return true;
    }
}
