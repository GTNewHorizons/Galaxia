package com.gtnewhorizons.galaxia.utility.effects;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.potion.Potion;

public class EffectOverheating extends Potion {

    public EffectOverheating(int id) {
        super(id, true, 0x66CCFF); // isBadEffect=true
        setPotionName("Overheating");
        setIconIndex(0, 0);
    }

    @Override
    public void performEffect(EntityLivingBase entity, int amplifier) {}

    @Override
    public boolean isReady(int duration, int amplifier) {
        return true;
    }
}
