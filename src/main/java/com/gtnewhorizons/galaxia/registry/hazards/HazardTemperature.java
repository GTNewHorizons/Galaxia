package com.gtnewhorizons.galaxia.registry.hazards;

import static com.gtnewhorizons.galaxia.api.GalaxiaAPI.getThermalProtection;

import java.util.Optional;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;

import com.gtnewhorizons.galaxia.registry.android.AndroidControl;
import com.gtnewhorizons.galaxia.registry.celestial.station.TileStation;
import com.gtnewhorizons.galaxia.registry.dimension.builder.EffectBuilder;
import com.gtnewhorizons.galaxia.registry.effects.GalaxiaEffects;
import com.gtnewhorizons.galaxia.registry.interfaces.IEnvironmentalHazard;

public class HazardTemperature implements IEnvironmentalHazard {

    private static DamageSource temperatureDamage = new DamageSource("galaxia.temperature").setDamageBypassesArmor()
        .setMagicDamage();

    public static int DEFAULT_MIN = 268; // -5 Celsius
    public static int DEFAULT_MAX = 323; // 50 Celsius

    public static int getAcceptableMinTemp(EntityLivingBase body) {
        int coldProtection = getThermalProtection(body, false);

        return DEFAULT_MIN - coldProtection;
    }

    public static int getAcceptableMaxTemp(EntityLivingBase body) {
        int heatProtection = getThermalProtection(body, true);

        return DEFAULT_MAX + heatProtection;
    }

    public static int getAcceptableMinTemp(EntityLivingBase body, Optional<TileStation> station) {
        return getAcceptableMinTemp(body) - station.map(TileStation::getCoolingModifier)
            .orElse(0);
    }

    /**
     * Applies the effects of Temperature for the body:
     * Diff < 20K = Slowness/Fatigue 1
     * 20K <= Diff <= 50K = Slowness/Fatigue 2
     * Diff > 50K = Slowness/Fatigue 3 and 1 heart per second damange
     *
     * @param def     The EffectDef of the dimension
     * @param body    The body entity
     * @param station
     * @return
     */
    @Override
    public HazardWarnings apply(EffectBuilder def, EntityLivingBase body, Optional<TileStation> station) {
        int temp = def.getTemperature(body.worldObj);

        int acceptableMaxTemp = getAcceptableMaxTemp(body) + station.map(TileStation::getHeatingModifier)
            .orElse(0);
        int acceptableMinTemp = getAcceptableMinTemp(body, station);

        if (temp < acceptableMaxTemp && temp > acceptableMinTemp) return HazardWarnings.FINE;
        if (temp < acceptableMinTemp) {
            return AndroidControl.isAndroid(body) ? HazardWarnings.FREEZING
                : applyFreeze(body, temp, acceptableMinTemp);
        } else if (temp > acceptableMaxTemp) {
            return applyBurning(body, temp, acceptableMaxTemp);
        }

        return HazardWarnings.FINE;
    }

    private HazardWarnings applyFreeze(EntityLivingBase body, int temperature, int acceptableMinTemp) {
        final int diff = acceptableMinTemp - temperature;
        final int harshness;
        if (diff < 20) {
            harshness = 0;
        } else if (diff < 50) {
            harshness = 1;
        } else {
            harshness = 2;
        }

        body.addPotionEffect(new PotionEffect(GalaxiaEffects.freezing.id, BASE_EFFECT_DURATION, harshness));
        if (harshness == 2) {
            body.attackEntityFrom(temperatureDamage, 2.0f);
        }

        return HazardWarnings.FREEZING;
    }

    private HazardWarnings applyBurning(EntityLivingBase body, int temperature, int acceptableMaxTemp) {
        final int diff = temperature - acceptableMaxTemp;
        final int harshness;
        if (diff < 20) {
            harshness = 0;
        } else if (diff < 50) {
            harshness = 1;
        } else {
            harshness = 2;
        }

        body.addPotionEffect(new PotionEffect(GalaxiaEffects.overheating.id, BASE_EFFECT_DURATION, harshness));
        return HazardWarnings.BURNING;
    }
}
