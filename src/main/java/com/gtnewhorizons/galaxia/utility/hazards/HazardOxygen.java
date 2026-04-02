package com.gtnewhorizons.galaxia.utility.hazards;

import static com.gtnewhorizons.galaxia.utility.GalaxiaAPI.*;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;

import com.gtnewhorizons.galaxia.registry.dimension.builder.EffectBuilder;
import com.gtnewhorizons.galaxia.utility.effects.GalaxiaEffects;

public class HazardOxygen extends EnvironmentalHazard {

    public static final DamageSource noOxygenDamage = new DamageSource("galaxia.noOxygen").setDamageBypassesArmor()
        .setMagicDamage();

    // defines duration of player being low on oxygen in SECONDS
    private int lowOxygenDuration = 0;

    /**
     * Applies the effects of low oxygen to the player
     *
     * @param def    The EffectDef of the dimension
     * @param player The player entity
     * @return warning
     */
    @Override
    public HazardWarnings apply(EffectBuilder def, EntityPlayer player) {
        int oxygenPercent = def.getOxygenPercent(player);

        boolean hasOxygenToDrain = false;
        boolean hasMask = hasOxygenmask(player);
        if (hasMask) {
            hasOxygenToDrain = checkOxygenAndDrain(player, oxygenPercent);
        }

        float oxygenLevel = getPlayerOxygenLevel(player);
        if (oxygenLevel > 0.1 && hasMask) {
            lowOxygenDuration = 0;
            return HazardWarnings.FINE;
        } else lowOxygenDuration++;

        int harshness = -1;

        if (oxygenLevel < 0.02) {
            harshness = 0;
        } else if (oxygenLevel < 0.04) {
            harshness = 1;
        } else if (oxygenLevel < 0.06) {
            harshness = 2;
        } else if (oxygenLevel < 0.08) {
            harshness = 3;
        } else if (oxygenLevel < 0.1) {
            harshness = 4;
        }

        if (harshness >= 0) {
            player.addPotionEffect(new PotionEffect(GalaxiaEffects.lowOxygen.getId(), BASE_EFFECT_DURATION, harshness));
        }

        if (hasOxygenToDrain) return HazardWarnings.LOW_OXYGEN;
        // Apply damage if no tank could be drained (tank is empty or no tanks
        // available)
        // damage scaled linearly so it can't be bypassed long-term by most of armors
        player.attackEntityFrom(noOxygenDamage, lowOxygenDuration * 2);
        return HazardWarnings.NO_OXYGEN;
    }
}
