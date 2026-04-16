package com.gtnewhorizons.galaxia.registry.rocketmodules.utility;

import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.RocketModule;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.gantry.TileEntityGantryTerminal;

/**
 * Record class to hold modules currently in transit in Gantry System
 *
 * @param module      The rocket module being moved
 * @param destination The terminal endpoint of the journey
 */

public record TransitModule(RocketModule module, TileEntityGantryTerminal destination) {

    /**
     * Custom method to return a more useful string format
     */
    @Override
    public String toString() {
        return String
            .format("TransitModule: Module: {%s}, Destination: {%s}", module().getName(), destination.toString());
    }
}
