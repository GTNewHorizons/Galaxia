package com.gtnewhorizons.galaxia.registry.hazards;

import static com.gtnewhorizons.galaxia.api.GalaxiaAPI.getPressureProtection;

import java.util.Optional;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;

import com.gtnewhorizons.galaxia.registry.android.AndroidControl;
import com.gtnewhorizons.galaxia.registry.celestial.station.TileStation;
import com.gtnewhorizons.galaxia.registry.celestial.station.TileStationBase;
import com.gtnewhorizons.galaxia.registry.dimension.builder.EffectBuilder;
import com.gtnewhorizons.galaxia.registry.interfaces.IEnvironmentalHazard;

public class HazardPressure implements IEnvironmentalHazard {

    public static int DEFAULT_MIN = 1;
    public static int DEFAULT_MAX = 2;

    /**
     * Applies the pressure effects to the body
     *
     * @param def     The EffectDef holding dimensional effects
     * @param body    The Player entity
     * @param station
     * @return
     */
    @Override
    public HazardWarnings apply(EffectBuilder def, EntityLivingBase body, Optional<TileStation> station) {
        // Temp until space suit added:
        int acceptableMin = DEFAULT_MIN;
        int acceptableMax = DEFAULT_MAX;
        int pressure = def.getPressure(body.worldObj);
        if (AndroidControl.isAndroid(body) && pressure < DEFAULT_MIN) return HazardWarnings.FINE;

        if (station.filter(TileStationBase::isSealed)
            .isPresent()) {
            return HazardWarnings.FINE;
        }

        acceptableMax += getPressureProtection(body, true);
        acceptableMin -= getPressureProtection(body, false);

        if (pressure <= acceptableMax && pressure >= acceptableMin) return HazardWarnings.FINE;
        HazardWarnings warning = pressure >= acceptableMax ? HazardWarnings.HIGH_PRESSURE : HazardWarnings.LOW_PRESSURE;

        if (body.isPotionActive(Potion.moveSlowdown)) return warning;
        if (body.isPotionActive(Potion.digSlowdown)) return warning;

        body.addPotionEffect(new PotionEffect(Potion.digSlowdown.id, BASE_EFFECT_DURATION * 2, 1));
        body.addPotionEffect(new PotionEffect(Potion.moveSlowdown.id, BASE_EFFECT_DURATION * 2, 1));
        return warning;
    }
}
