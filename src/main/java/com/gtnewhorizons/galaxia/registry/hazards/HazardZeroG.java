package com.gtnewhorizons.galaxia.registry.hazards;

import com.gtnewhorizons.galaxia.registry.interfaces.IEnvironmentalHazard;
import net.minecraft.entity.player.EntityPlayer;

import com.gtnewhorizons.galaxia.api.GalaxiaAPI;
import com.gtnewhorizons.galaxia.registry.dimension.builder.EffectBuilder;

public class HazardZeroG implements IEnvironmentalHazard {

    @Override
    public HazardWarnings apply(EffectBuilder def, EntityPlayer player) {
        if (GalaxiaAPI.getGravity(player) != 0) return HazardWarnings.FINE;
        if (GalaxiaAPI.hasZeroGMovementCapability(player)) return HazardWarnings.FINE;

        return HazardWarnings.NO_ZEROG_MOVEMENT;
    }
}
