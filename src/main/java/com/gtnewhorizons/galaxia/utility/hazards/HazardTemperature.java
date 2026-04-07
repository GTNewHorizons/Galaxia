package com.gtnewhorizons.galaxia.utility.hazards;

import static com.gtnewhorizons.galaxia.utility.GalaxiaAPI.getThermalProtection;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;

import com.gtnewhorizons.galaxia.registry.dimension.builder.EffectBuilder;
import com.gtnewhorizons.galaxia.utility.effects.GalaxiaEffects;

public class HazardTemperature extends EnvironmentalHazard {

    private static DamageSource temperatureDamage = new DamageSource("galaxia.temperature").setDamageBypassesArmor()
        .setMagicDamage();

    public static int DEFAULT_MIN = 268; // -5 Celsius
    public static int DEFAULT_MAX = 323; // 50 Celsius

    public static int getAcceptableMinTemp(EntityPlayer player) {
        int coldProtection = getThermalProtection(player, false);

        return DEFAULT_MIN - coldProtection;
    }

    public static int getAcceptableMaxTemp(EntityPlayer player) {
        int heatProtection = getThermalProtection(player, true);

        return DEFAULT_MAX + heatProtection;
    }

    /**
     * Applies the effects of Temperature for the player:
     * Diff < 20K = Slowness/Fatigue 1
     * 20K <= Diff <= 50K = Slowness/Fatigue 2
     * Diff > 50K = Slowness/Fatigue 3 and 1 heart per second damange
     *
     * @param def    The EffectDef of the dimension
     * @param player The player entity
     * @return
     */
    @Override
    public HazardWarnings apply(EffectBuilder def, EntityPlayer player) {
        int temp = def.getTemperature(player);

        int acceptableMaxTemp = getAcceptableMaxTemp(player);
        int acceptableMinTemp = getAcceptableMinTemp(player);

        if (temp < acceptableMaxTemp && temp > acceptableMinTemp) return HazardWarnings.FINE;
        if (temp < acceptableMinTemp) {
            return applyFreeze(player, temp, acceptableMinTemp);
        } else if (temp > acceptableMaxTemp) {
            return applyBurning(player, temp, acceptableMaxTemp);
        }

        return HazardWarnings.FINE;
    }

    private HazardWarnings applyFreeze(EntityPlayer player, int temperature, int acceptableMinTemp) {
        final int diff = acceptableMinTemp - temperature;
        final int harshness;
        if (diff < 20) {
            harshness = 0;
        } else if (diff < 50) {
            harshness = 1;
        } else {
            harshness = 2;
        }

        player.addPotionEffect(new PotionEffect(GalaxiaEffects.freezing.id, BASE_EFFECT_DURATION, harshness));
        if (harshness == 2) {
            player.attackEntityFrom(temperatureDamage, 2.0f);
        }

        return HazardWarnings.FREEZING;
    }

    private HazardWarnings applyBurning(EntityPlayer player, int temperature, int acceptableMaxTemp) {
        final int diff = temperature - acceptableMaxTemp;
        final int harshness;
        if (diff < 20) {
            harshness = 0;
        } else if (diff < 50) {
            harshness = 1;
        } else {
            harshness = 2;
        }

        player.addPotionEffect(new PotionEffect(GalaxiaEffects.overheating.id, BASE_EFFECT_DURATION, harshness));
        return HazardWarnings.BURNING;
    }
}
