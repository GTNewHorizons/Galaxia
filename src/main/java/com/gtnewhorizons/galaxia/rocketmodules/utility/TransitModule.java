package com.gtnewhorizons.galaxia.rocketmodules.utility;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.RocketModule;
import com.gtnewhorizons.galaxia.rocketmodules.tileentities.gantry.TileEntityGantryTerminal;

@Desugar
public record TransitModule(RocketModule module, TileEntityGantryTerminal destination) {

    @Override
    public String toString() {
        return String
            .format("TransitModule: Module: {%s}, Destination: {%s}", module().getName(), destination.toString());
    }
}
