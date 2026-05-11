package com.gtnewhorizons.galaxia.compat.structure;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizon.structurelib.structure.IStructureDefinition;

public interface ArbitraryShapeTile<T extends TileEntity & ArbitraryShapeTile<T>> {

    IStructureDefinition<T> getStructureDefinition();

    ForgeDirection getPlacedFacing();

    boolean isStructureValid();

    default int getVolume() {
        return ((ArbitraryShapeDefinition<T>) this).getVolume();
    }

    int getSearchRadius();

    default World worldObj() {
        return ((TileEntity) this).getWorldObj();
    }
}
