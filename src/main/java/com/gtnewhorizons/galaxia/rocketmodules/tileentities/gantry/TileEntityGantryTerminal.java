package com.gtnewhorizons.galaxia.rocketmodules.tileentities.gantry;

import com.gtnewhorizons.galaxia.rocketmodules.tileentities.TileEntityModuleAssembler;
import com.gtnewhorizons.galaxia.rocketmodules.tileentities.TileEntitySilo;

public class TileEntityGantryTerminal extends TileEntityGantry {

    private TileEntitySilo connectedSilo;
    private TileEntityModuleAssembler connectedAssembler;

    public void connectSilo(TileEntitySilo silo) {
        connectedSilo = silo;
    }

    public void connectAssembler(TileEntityModuleAssembler assembler) {
        connectedAssembler = assembler;
    }

}
