package com.gtnewhorizons.galaxia.utility.effects;

import net.minecraft.entity.EntityLivingBase;

public class GalaxiaEffectAPI {

    public static float getSpeedMultiplier(EntityLivingBase entity) {
        float result = 1f;
        boolean applyMultiplier = entity.isPotionActive(GalaxiaEffects.lowOxygen)
            || entity.isPotionActive(GalaxiaEffects.overcooling)
            || entity.isPotionActive(GalaxiaEffects.overheating);

        if (applyMultiplier) {
            int amp = entity.getActivePotionEffect(GalaxiaEffects.lowOxygen)
                .getAmplifier();
            result = oxygenSpeedMultiplier(amp);
        }

        return result;
    }

    private static float oxygenSpeedMultiplier(int amp) {
        // amp starts from 0
        // minimal speed is 10% at lvl 10 (amp=9)
        return Math.max(0.1f, (float) (1 - 0.1 * (amp + 1)));
    }
}
