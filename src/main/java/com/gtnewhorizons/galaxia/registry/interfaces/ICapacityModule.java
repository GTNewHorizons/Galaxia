package com.gtnewhorizons.galaxia.registry.interfaces;

import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;

/**
 * Implemented by Storage, Tank, Battery modules for adjacency cluster capacity calculation.
 */
public interface ICapacityModule {

    long baseCapacityForTier(ModuleTier tier);
}
