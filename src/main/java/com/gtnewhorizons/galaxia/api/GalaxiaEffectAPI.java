package com.gtnewhorizons.galaxia.api;

import java.util.Arrays;
import java.util.List;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;

import com.gtnewhorizons.galaxia.registry.effects.GalaxiaEffects;

public class GalaxiaEffectAPI {

    public static final List<Potion> speedMultiplierEffects = Arrays
        .asList(GalaxiaEffects.lowOxygen, GalaxiaEffects.freezing, GalaxiaEffects.overheating, Potion.moveSlowdown);

    public static float getSpeedMultiplier(EntityLivingBase entity) {
        if (!(entity instanceof EntityPlayer)) return 1; // debuffs only apply for players
        if (!GalaxiaAPI.isInGalaxiaDimension(entity)) return 1;
        int amp = -1;
        for (Potion p : speedMultiplierEffects) {
            PotionEffect effect = entity.getActivePotionEffect(p);
            if (effect == null) continue;

            amp = Math.max(amp, effect.getAmplifier());
        }

        if (amp >= 0) return oxygenSpeedMultiplier(amp);
        return 1;
    }

    private static float oxygenSpeedMultiplier(int amp) {
        // amp starts from 0
        // minimal speed is 10% at lvl 10 (amp=9)
        return Math.max(0.1f, (float) (1 - 0.1 * (amp + 1)));
    }
}
