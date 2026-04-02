package com.gtnewhorizons.galaxia.utility.hazards;

import static com.gtnewhorizons.galaxia.utility.GalaxiaAPI.getPressureProtection;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;

import com.gtnewhorizons.galaxia.registry.dimension.builder.EffectBuilder;

public class HazardPressure extends EnvironmentalHazard {

    public static int DEFAULT_MIN = 1;
    public static int DEFAULT_MAX = 2;

    /**
     * Applies the pressure effects to the player
     *
     * @param def    The EffectDef holding dimensional effects
     * @param player The Player entity
     * @return
     */
    @Override
    public HazardWarnings apply(EffectBuilder def, EntityPlayer player) {
        // Temp until space suit added:
        int acceptableMin = DEFAULT_MIN;
        int acceptableMax = DEFAULT_MAX;
        int pressure = def.getPressure(player);

        acceptableMax += getPressureProtection(player, true);
        acceptableMin -= getPressureProtection(player, false);

        if (pressure <= acceptableMax && pressure >= acceptableMin) return HazardWarnings.FINE;
        if (player.isPotionActive(Potion.moveSlowdown)) return HazardWarnings.LOW_PRESSURE;
        if (player.isPotionActive(Potion.digSlowdown)) return HazardWarnings.LOW_PRESSURE;

        player.addPotionEffect(new PotionEffect(Potion.digSlowdown.id, BASE_EFFECT_DURATION * 2, 1));
        player.addPotionEffect(new PotionEffect(Potion.moveSlowdown.id, BASE_EFFECT_DURATION * 2, 1));
        return HazardWarnings.LOW_PRESSURE;
    }
}
