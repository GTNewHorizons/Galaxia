package com.gtnewhorizons.galaxia.utility.hazards;

import static com.gtnewhorizons.galaxia.utility.GalaxiaAPI.getRadiationProtection;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.DamageSource;

import com.gtnewhorizons.galaxia.registry.dimension.builder.EffectBuilder;

public class HazardRadiation extends EnvironmentalHazard {

    public static final int DEFAULT_MAX = 0;
    final public static DamageSource radiationDamage = new DamageSource("galaxia.radiation").setDamageBypassesArmor()
        .setMagicDamage();

    /**
     * Applies the effects of radiation to the player
     *
     * @param def    The EffectDef holding dimensional effects
     * @param player The player entity
     * @return
     */
    @Override
    public HazardWarnings apply(EffectBuilder def, EntityPlayer player) {
        int radiation = def.getRadiation(player);
        if (radiation == 0) return HazardWarnings.FINE;
        int acceptableMax = DEFAULT_MAX;

        acceptableMax += getRadiationProtection(player);
        if (radiation <= acceptableMax) return HazardWarnings.FINE;
        player.attackEntityFrom(radiationDamage, 5.0f);
        return HazardWarnings.HIGH_RADIATION;
    }
}
