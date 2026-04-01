package com.gtnewhorizons.galaxia.utility.effects;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.potion.PotionEffect;

public class GalaxiaEffectAPI {

    public static float getSpeedMultiplier(EntityLivingBase entity) {
        PotionEffect lowOxygen = entity.getActivePotionEffect(GalaxiaEffects.lowOxygen);
        PotionEffect overcooling = entity.getActivePotionEffect(GalaxiaEffects.overcooling);
        PotionEffect overheating = entity.getActivePotionEffect(GalaxiaEffects.overheating);

        int amp = -1;
        if (lowOxygen != null) amp = Math.max(amp, lowOxygen.getAmplifier());
        if (overcooling != null) amp = Math.max(amp, overcooling.getAmplifier());
        if (overheating != null) amp = Math.max(amp, overheating.getAmplifier());

        return amp >= 0 ? oxygenSpeedMultiplier(amp) : 1f;
    }

    private static float oxygenSpeedMultiplier(int amp) {
        // amp starts from 0
        // minimal speed is 10% at lvl 10 (amp=9)
        return Math.max(0.1f, (float) (1 - 0.1 * (amp + 1)));
    }
}
