package com.gtnewhorizons.galaxia.rocketmodules.tileentities.gantry;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Vec3;

import com.gtnewhorizons.galaxia.rocketmodules.tileentities.TileEntityModuleAssembler;
import com.gtnewhorizons.galaxia.rocketmodules.tileentities.TileEntitySilo;
import com.gtnewhorizons.galaxia.rocketmodules.utility.TransitModule;

public class TileEntityGantryTerminal extends TileEntityGantry {

    private final static int DISPATCH_INTERVAL = 20;

    private TileEntitySilo connectedSilo;
    private TileEntityModuleAssembler connectedAssembler;

    public void connectSilo(TileEntitySilo silo) {
        connectedSilo = silo;
    }

    public void connectAssembler(TileEntityModuleAssembler assembler) {
        connectedAssembler = assembler;
    }

    public TileEntitySilo getSilo() {
        return connectedSilo;
    }

    public TileEntityModuleAssembler getAssembler() {
        return connectedAssembler;
    }

    public void sync() {
        markDirty();
        if (!worldObj.isRemote) {
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
    }

    @Override
    public void updateEntity() {
        super.updateEntity();

        TileEntityGantryTerminal teg = this;

        for (Vec3 check_offset : GantryAPI.CHECK_OFFSETS) {
            int cx = xCoord + (int) check_offset.xCoord;
            int cy = yCoord + (int) check_offset.yCoord;
            int cz = zCoord + (int) check_offset.zCoord;

            TileEntity checkTe = worldObj.getTileEntity(cx, cy, cz);
            if (checkTe instanceof TileEntitySilo checkTes) {
                teg.connectSilo(checkTes);
                checkTes.setGantryTerminal(teg);
            } else if (checkTe instanceof TileEntityModuleAssembler checkTema) {
                teg.connectAssembler(checkTema);
                checkTema.setGantryTerminal(teg);
            }
        }

    }

    public boolean acceptModule(TransitModule transit, boolean start) {
        super.acceptModule(transit);
        if (!start) passModuleToConsumer();
        return true;
    }

    public void passModuleToConsumer() {
        if (worldObj.isRemote) return;

        if (connectedSilo != null && connectedSilo.receiveModule(getModule().getId())) {
            clearModule();
            return;
        } else if (connectedAssembler != null) {
            connectedAssembler.addModule(getModule().getId());
            connectedAssembler.sync();
            clearModule();
            return;
        }
        return;
    }

}
