package com.gtnewhorizons.galaxia.registry.hazards;

import static com.gtnewhorizons.galaxia.api.GalaxiaAPI.*;

import java.util.Optional;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;

import com.gtnewhorizons.galaxia.core.GalaxiaPlayerProperties;
import com.gtnewhorizons.galaxia.registry.android.AndroidControl;
import com.gtnewhorizons.galaxia.registry.android.EntityBody;
import com.gtnewhorizons.galaxia.registry.celestial.station.TileStation;
import com.gtnewhorizons.galaxia.registry.dimension.builder.EffectBuilder;
import com.gtnewhorizons.galaxia.registry.effects.GalaxiaEffects;
import com.gtnewhorizons.galaxia.registry.interfaces.IEnvironmentalHazard;

public class HazardOxygen implements IEnvironmentalHazard {

    public static final DamageSource noOxygenDamage = new DamageSource("galaxia.noOxygen").setDamageBypassesArmor()
        .setMagicDamage();

    /**
     * Applies the effects of low oxygen to the player
     *
     * @param def     The EffectDef of the dimension
     * @param body    The body entity
     * @param station
     * @return warning
     */
    @Override
    public HazardWarnings apply(EffectBuilder def, EntityLivingBase body, Optional<TileStation> station) {
        if (AndroidControl.isAndroid(body) || !(body instanceof EntityPlayer || body instanceof EntityBody))
            return HazardWarnings.FINE;
        final int oxygenPercent = def.getOxygenPercent(body.worldObj);
        if (oxygenPercent >= 100) return HazardWarnings.FINE;

        if (station.filter(TileStation::isOxygenated)
            .isPresent()) {
            return HazardWarnings.FINE;
        }

        final boolean hasMask = hasOxygenmask(body);
        final boolean hasOxygenToDrain = hasMask && checkOxygenAndDrain(body, oxygenPercent);
        int lowOxygenDuration = body instanceof EntityPlayer player
            ? GalaxiaPlayerProperties.get(player).lowOxygenDuration
            : ((EntityBody) body).lowOxygenDuration();
        final float oxygenLevel = getPlayerOxygenLevel(body);
        if (oxygenLevel > 0.1 && hasOxygenToDrain) {
            setLowOxygenDuration(body, 0);
            return HazardWarnings.FINE;
        }
        lowOxygenDuration++;
        setLowOxygenDuration(body, lowOxygenDuration);

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
            body.addPotionEffect(new PotionEffect(GalaxiaEffects.lowOxygen.getId(), BASE_EFFECT_DURATION, harshness));
        }

        if (hasOxygenToDrain) return HazardWarnings.LOW_OXYGEN;
        // Apply damage if no tank could be drained (tank is empty or no tanks
        // available)
        // damage scaled linearly so it can't be bypassed long-term by most of armors
        body.attackEntityFrom(noOxygenDamage, lowOxygenDuration * 2);
        return HazardWarnings.NO_OXYGEN;
    }

    private static void setLowOxygenDuration(EntityLivingBase body, int duration) {
        if (body instanceof EntityPlayer player) GalaxiaPlayerProperties.get(player).lowOxygenDuration = duration;
        else((EntityBody) body).setLowOxygenDuration(duration);
    }

}
