package com.gtnewhorizons.galaxia.utility.hazards;

import static com.gtnewhorizons.galaxia.utility.GalaxiaAPI.hasWitherProtection;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;

import com.gtnewhorizons.galaxia.registry.dimension.builder.EffectBuilder;

public class HazardWithering extends EnvironmentalHazard {

    /**
     * Applies wither where needed
     *
     * @param def    The EffectDef holding the dimensional effects
     * @param player The player entity
     * @return
     */
    @Override
    public HazardWarnings apply(EffectBuilder def, EntityPlayer player) {
        if (!def.getWithering(player)) return HazardWarnings.FINE;
        if (hasWitherProtection(player)) return HazardWarnings.FINE;
        if (player.isPotionActive(Potion.wither)) return HazardWarnings.WITHER;

        player.addPotionEffect(new PotionEffect(Potion.wither.id, BASE_EFFECT_DURATION, 1));
        return HazardWarnings.WITHER;
    }
}
