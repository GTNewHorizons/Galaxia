package com.gtnewhorizons.galaxia.registry.hazards;

import static com.gtnewhorizons.galaxia.api.GalaxiaAPI.*;

import java.util.Optional;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;

import com.gtnewhorizons.galaxia.registry.celestial.station.TileStation;
import com.gtnewhorizons.galaxia.registry.dimension.builder.EffectBuilder;
import com.gtnewhorizons.galaxia.registry.effects.GalaxiaEffects;
import com.gtnewhorizons.galaxia.registry.interfaces.IEnvironmentalHazard;

public class HazardOxygen implements IEnvironmentalHazard {

    public static final DamageSource noOxygenDamage = new DamageSource("galaxia.noOxygen").setDamageBypassesArmor()
        .setMagicDamage();

    // defines duration of player being low on oxygen in SECONDS
    private int lowOxygenDuration = 0;

    /**
     * Applies the effects of low oxygen to the player
     *
     * @param def     The EffectDef of the dimension
     * @param player  The player entity
     * @param station
     * @return warning
     */
    @Override
    public HazardWarnings apply(EffectBuilder def, EntityPlayer player, Optional<TileStation> station) {
        final int oxygenPercent = def.getOxygenPercent(player.worldObj);
        if (oxygenPercent >= 100) return HazardWarnings.FINE;

        if (station.filter(TileStation::isOxygenated)
            .isPresent()) {
            return HazardWarnings.FINE;
        }

        final boolean hasMask = hasOxygenmask(player);
        final boolean hasOxygenToDrain = hasMask && checkOxygenAndDrain(player, oxygenPercent);

        final float oxygenLevel = getPlayerOxygenLevel(player);
        if (oxygenLevel > 0.1 && hasOxygenToDrain) {
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
