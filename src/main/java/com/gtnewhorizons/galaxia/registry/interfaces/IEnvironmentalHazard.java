package com.gtnewhorizons.galaxia.registry.interfaces;

import java.util.Optional;

import net.minecraft.entity.player.EntityPlayer;

import com.gtnewhorizons.galaxia.registry.celestial.station.TileStation;
import com.gtnewhorizons.galaxia.registry.dimension.builder.EffectBuilder;
import com.gtnewhorizons.galaxia.registry.hazards.HazardWarnings;

public interface IEnvironmentalHazard {

    int BASE_EFFECT_DURATION = 40;

    HazardWarnings apply(EffectBuilder def, EntityPlayer player, Optional<TileStation> station);
}
