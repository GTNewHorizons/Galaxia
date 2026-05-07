package com.gtnewhorizons.galaxia.registry.block.tile;

import net.minecraft.tileentity.TileEntity;

import com.gtnewhorizons.galaxia.registry.interfaces.IMachineBlockUpdateable;

public abstract class TileMachine extends TileEntity implements IMachineBlockUpdateable {

    @Override
    public abstract void onMachineBlockUpdate();

    @Override
    public boolean isMachineBlockUpdateRecursive() {
        return IMachineBlockUpdateable.super.isMachineBlockUpdateRecursive();
    }
}
