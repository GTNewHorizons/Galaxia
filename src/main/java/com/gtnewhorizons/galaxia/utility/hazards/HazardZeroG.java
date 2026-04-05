package com.gtnewhorizons.galaxia.utility.hazards;

import net.minecraft.entity.player.EntityPlayer;

import com.gtnewhorizons.galaxia.registry.dimension.builder.EffectBuilder;
import com.gtnewhorizons.galaxia.utility.GalaxiaAPI;

public class HazardZeroG extends EnvironmentalHazard {

    @Override
    public HazardWarnings apply(EffectBuilder def, EntityPlayer player) {
        if (GalaxiaAPI.getGravity(player) != 0) return HazardWarnings.FINE;
        if (GalaxiaAPI.hasZeroGMovementCapability(player)) return HazardWarnings.FINE;

        return HazardWarnings.NO_ZEROG_MOVEMENT;
    }
}
