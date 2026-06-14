package com.gtnewhorizons.galaxia.registry.block;

import net.minecraft.nbt.NBTTagCompound;

public abstract class GalaxiaBootableMultiblock<T extends GalaxiaBootableMultiblock<T>>
    extends GalaxiaMultiblockBase<T> {

    protected enum BootState {
        UNINITIALIZED,
        STRUCTURE_VALID,
        BOOTED
    }

    protected BootState bootState = BootState.UNINITIALIZED;

    protected void onBootComplete() {}

    protected void onBootFailed() {}

    protected boolean attemptBoot() {
        return true;
    }

    protected void tickPostBoot() {}

    public boolean booted() {
        return bootState == BootState.BOOTED;
    }

    protected void reset() {
        bootState = BootState.UNINITIALIZED;
        onBootFailed();
        super.reset();
    }

    @Override
    protected void onStructureFormed() {
        super.onStructureFormed();
        if (bootState == BootState.UNINITIALIZED) {
            bootState = BootState.STRUCTURE_VALID;
        }
    }

    @Override
    protected void onStructureDisformed() {
        super.onStructureDisformed();
        if (bootState == BootState.BOOTED) {
            bootState = BootState.UNINITIALIZED;
            onBootFailed();
        }
    }

    @Override
    protected void onStructureChecked() {
        if (bootState == BootState.STRUCTURE_VALID) {
            if (attemptBoot()) {
                bootState = BootState.BOOTED;
                onBootComplete();
                markDirty();
                worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
            }
        }
        if (bootState == BootState.BOOTED) {
            tickPostBoot();
        }
    }

    @Override
    public void onMachineBlockUpdate() {
        super.onMachineBlockUpdate();
        if (bootState != BootState.UNINITIALIZED) {
            updated = true;
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setInteger("bootState", bootState.ordinal());
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        if (nbt.hasKey("bootState")) {
            bootState = BootState.values()[nbt.getInteger("bootState")];
        }
        if (!reloadHappened && needsFormationOnReload()) {
            bootState = BootState.UNINITIALIZED;
        }
        super.readFromNBT(nbt);
    }
}
