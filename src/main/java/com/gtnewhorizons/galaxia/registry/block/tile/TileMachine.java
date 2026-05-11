package com.gtnewhorizons.galaxia.registry.block.tile;

import net.minecraft.tileentity.TileEntity;

import com.gtnewhorizons.galaxia.registry.interfaces.IMachineBlockUpdateable;

import cpw.mods.fml.common.Optional;

/**
 * This class is for deferring updates on another thread.
 */
@Optional.Interface(iface = "gregtech.api.interfaces.tileentity.IMachineBlockUpdateable", modid = "gregtech")
public abstract class TileMachine extends TileEntity
    implements IMachineBlockUpdateable, gregtech.api.interfaces.tileentity.IMachineBlockUpdateable {

    @Override
    public abstract void onMachineBlockUpdate();

    @Override
    public boolean isMachineBlockUpdateRecursive() {
        return IMachineBlockUpdateable.super.isMachineBlockUpdateRecursive();
    }
}
