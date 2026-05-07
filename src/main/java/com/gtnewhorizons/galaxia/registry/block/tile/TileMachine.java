package com.gtnewhorizons.galaxia.registry.block.tile;

import net.minecraft.tileentity.TileEntity;

import com.gtnewhorizons.galaxia.registry.interfaces.IMachineBlockUpdateable;

/**
 * This class is for deferring updates on another thread. Also, optionally hooks into the GT update structure thread
 * through
 * {@link com.gtnewhorizons.galaxia.mixin.late.gregtech.MixinTileMachine}
 */
public abstract class TileMachine extends TileEntity implements IMachineBlockUpdateable {

    @Override
    public abstract void onMachineBlockUpdate();

    @Override
    public boolean isMachineBlockUpdateRecursive() {
        return IMachineBlockUpdateable.super.isMachineBlockUpdateRecursive();
    }
}
