package com.gtnewhorizons.galaxia.registry.block.tile;

import gregtech.api.interfaces.tileentity.IMachineBlockUpdateable;
import net.minecraft.tileentity.TileEntity;

import cpw.mods.fml.common.Optional;

/**
 * This class is for deferring updates on another thread.
 */
@Optional.Interface(iface = "gregtech.api.interfaces.tileentity.IMachineBlockUpdateable", modid = "gregtech")
public abstract class TileMachine extends TileEntity implements IMachineBlockUpdateable {
    @Override
    public abstract void onMachineBlockUpdate();
}
