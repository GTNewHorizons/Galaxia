package com.gtnewhorizons.galaxia.rocketmodules.utility;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.RocketModule;
import com.gtnewhorizons.galaxia.rocketmodules.tileentities.gantry.TileEntityGantryTerminal;

/**
 * Record class to hold modules currently in transit in Gantry System
 *
 * @param module      The rocket module being moved
 * @param destination The terminal endpoint of the journey
 */
@Desugar
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
