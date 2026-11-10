package com.gtnewhorizons.galaxia.registry.hazards;

import static com.gtnewhorizons.galaxia.api.GalaxiaAPI.hasWitherProtection;

import java.util.Optional;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;

import com.gtnewhorizons.galaxia.registry.celestial.station.TileStation;
import com.gtnewhorizons.galaxia.registry.dimension.builder.EffectBuilder;
import com.gtnewhorizons.galaxia.registry.interfaces.IEnvironmentalHazard;

public class HazardWithering implements IEnvironmentalHazard {

    /**
     * Applies wither where needed
     *
     * @param def     The EffectDef holding the dimensional effects
     * @param body    The body entity
     * @param station
     * @return
     */
    @Override
    public HazardWarnings apply(EffectBuilder def, EntityLivingBase body, Optional<TileStation> station) {
        if (!def.getWithering(body.worldObj)) return HazardWarnings.FINE;
        if (hasWitherProtection(body)) return HazardWarnings.FINE;
        if (body.isPotionActive(Potion.wither)) return HazardWarnings.WITHER;

        if (station.filter(TileStation::hasWitherBlocker)
            .isPresent()) {
            return HazardWarnings.FINE;
        }

        body.addPotionEffect(new PotionEffect(Potion.wither.id, BASE_EFFECT_DURATION, 1));
        return HazardWarnings.WITHER;
    }
}
