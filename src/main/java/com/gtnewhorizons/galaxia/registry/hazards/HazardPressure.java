package com.gtnewhorizons.galaxia.registry.hazards;

import static com.gtnewhorizons.galaxia.api.GalaxiaAPI.getPressureProtection;

import java.util.Optional;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;

import com.gtnewhorizons.galaxia.registry.celestial.station.TileStation;
import com.gtnewhorizons.galaxia.registry.celestial.station.TileStationBase;
import com.gtnewhorizons.galaxia.registry.dimension.builder.EffectBuilder;
import com.gtnewhorizons.galaxia.registry.interfaces.IEnvironmentalHazard;

public class HazardPressure implements IEnvironmentalHazard {

    public static int DEFAULT_MIN = 1;
    public static int DEFAULT_MAX = 2;

    /**
     * Applies the pressure effects to the player
     *
     * @param def     The EffectDef holding dimensional effects
     * @param player  The Player entity
     * @param station
     * @return
     */
    @Override
    public HazardWarnings apply(EffectBuilder def, EntityPlayer player, Optional<TileStation> station) {
        // Temp until space suit added:
        int acceptableMin = DEFAULT_MIN;
        int acceptableMax = DEFAULT_MAX;
        int pressure = def.getPressure(player.worldObj);

        if (station.filter(TileStationBase::isSealed)
            .isPresent()) {
            return HazardWarnings.FINE;
        }

        acceptableMax += getPressureProtection(player, true);
        acceptableMin -= getPressureProtection(player, false);

        if (pressure <= acceptableMax && pressure >= acceptableMin) return HazardWarnings.FINE;
        HazardWarnings warning = pressure >= acceptableMax ? HazardWarnings.HIGH_PRESSURE : HazardWarnings.LOW_PRESSURE;

        if (player.isPotionActive(Potion.moveSlowdown)) return warning;
        if (player.isPotionActive(Potion.digSlowdown)) return warning;

        player.addPotionEffect(new PotionEffect(Potion.digSlowdown.id, BASE_EFFECT_DURATION * 2, 1));
        player.addPotionEffect(new PotionEffect(Potion.moveSlowdown.id, BASE_EFFECT_DURATION * 2, 1));
        return warning;
    }
}
