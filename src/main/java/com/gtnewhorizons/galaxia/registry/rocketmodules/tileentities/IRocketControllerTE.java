package com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities;

import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;

public interface IRocketControllerTE {

    ForgeDirection getPlacedFacing();

    void setPlacedFacing(ForgeDirection dir);

    boolean isStructureValid();

    ExtendedFacing getCurrentFacing();
}
