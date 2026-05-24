package com.gtnewhorizons.galaxia.registry.hazards;

import static com.gtnewhorizons.galaxia.api.GalaxiaAPI.getRadiationProtection;

import java.util.Optional;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.DamageSource;

import com.gtnewhorizons.galaxia.registry.celestial.station.TileStation;
import com.gtnewhorizons.galaxia.registry.celestial.station.TileStationBase;
import com.gtnewhorizons.galaxia.registry.dimension.builder.EffectBuilder;
import com.gtnewhorizons.galaxia.registry.interfaces.IEnvironmentalHazard;

public class HazardRadiation implements IEnvironmentalHazard {

    public static final int DEFAULT_MAX = 0;
    final public static DamageSource radiationDamage = new DamageSource("galaxia.radiation").setDamageBypassesArmor()
        .setMagicDamage();

    /**
     * Applies the effects of radiation to the player
     *
     * @param def     The EffectDef holding dimensional effects
     * @param player  The player entity
     * @param station
     * @return
     */
    @Override
    public HazardWarnings apply(EffectBuilder def, EntityPlayer player, Optional<TileStation> station) {
        int radiation = def.getRadiation(player.worldObj);
        if (radiation == 0) return HazardWarnings.FINE;
        int acceptableMax = DEFAULT_MAX;

        if (station.filter(TileStationBase::isSealed)
            .isPresent()) {
            return HazardWarnings.FINE;
        }

        acceptableMax += getRadiationProtection(player);
        if (radiation <= acceptableMax) return HazardWarnings.FINE;
        player.attackEntityFrom(radiationDamage, 5.0f);
        return HazardWarnings.HIGH_RADIATION;
    }
}
