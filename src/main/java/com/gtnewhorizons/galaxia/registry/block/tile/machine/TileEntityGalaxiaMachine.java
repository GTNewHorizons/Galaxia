package com.gtnewhorizons.galaxia.registry.block.tile.machine;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.gtnewhorizons.galaxia.api.IOxygenHandler;
import com.gtnewhorizons.galaxia.core.config.ConfigMachines;

import cofh.api.energy.IEnergyReceiver;
import cpw.mods.fml.common.Optional;
import gregtech.api.interfaces.tileentity.IColoredTileEntity;
import gregtech.api.interfaces.tileentity.IEnergyConnected;

@Optional.InterfaceList({
    @Optional.Interface(iface = "gregtech.api.interfaces.tileentity.IEnergyConnected", modid = "gregtech"),
    @Optional.Interface(iface = "cofh.api.energy.IEnergyReceiver", modid = "CoFHCore"),
    @Optional.Interface(iface = "gregtech.api.interfaces.tileentity.IColoredTileEntity", modid = "gregtech") })
public abstract class TileEntityGalaxiaMachine extends TileEntity
    implements IEnergyConnected, IEnergyReceiver, IOxygenHandler, IColoredTileEntity, IGuiHolder<PosGuiData> {

    public double storedEnergy;
    public boolean active;
    private int tickCounter;

    protected abstract double getMaxEnergyBuffer();

    protected abstract int getMaxOxygenBuffer();

    protected abstract double getEuPerOperation();

    protected abstract int getWorkIntervalTicks();

    protected abstract void doWork();

    protected final boolean useEU() {
        return !ConfigMachines.energy.useRF;
    }

    protected final boolean hasEnoughEnergy() {
        return storedEnergy >= getEuPerOperation();
    }

    protected final void consumeEnergy() {
        storedEnergy = Math.max(0, storedEnergy - getEuPerOperation());
    }

    // GT EU
    @Override
    @Optional.Method(modid = "gregtech")
    public long injectEnergyUnits(ForgeDirection direction, long voltage, long amperage) {
        if (!useEU()) return 0;

        long maxAdd = (long) getMaxEnergyBuffer() - (long) storedEnergy;
        long euToAdd = Math.min(voltage * amperage, maxAdd);

        if (euToAdd > 0) storedEnergy += euToAdd;
        return euToAdd > 0 ? amperage : 0;
    }

    @Override
    @Optional.Method(modid = "gregtech")
    public boolean inputEnergyFrom(ForgeDirection side) {
        return useEU();
    }

    @Override
    @Optional.Method(modid = "gregtech")
    public boolean outputsEnergyTo(ForgeDirection side) {
        return false;
    }

    // CoFH RF
    @Override
    @Optional.Method(modid = "CoFHCore")
    public int receiveEnergy(ForgeDirection from, int maxReceive, boolean simulate) {
        if (useEU()) return 0;
        int ratio = Math.max(1, ConfigMachines.energy.rfPerEU);
        long euSpace = (long) getMaxEnergyBuffer() - (long) storedEnergy;
        long euToAdd = Math.min((long) maxReceive / ratio, euSpace);

        if (!simulate) storedEnergy += euToAdd;
        return (int) (euToAdd * ratio);
    }

    @Override
    @Optional.Method(modid = "CoFHCore")
    public int getEnergyStored(ForgeDirection from) {
        if (useEU()) return 0;
        int ratio = Math.max(1, ConfigMachines.energy.rfPerEU);
        return (int) Math.min(storedEnergy * ratio, Integer.MAX_VALUE);
    }

    @Override
    @Optional.Method(modid = "CoFHCore")
    public int getMaxEnergyStored(ForgeDirection from) {
        if (useEU()) return 0;
        int ratio = Math.max(1, ConfigMachines.energy.rfPerEU);
        return (int) Math.min(getMaxEnergyBuffer() * ratio, Integer.MAX_VALUE);
    }

    @Override
    @Optional.Method(modid = "CoFHCore")
    public boolean canConnectEnergy(ForgeDirection from) {
        return !useEU();
    }

    @Override
    @Optional.Method(modid = "gregtech")
    public byte getColorization() {
        return -1;
    }

    @Override
    @Optional.Method(modid = "gregtech")
    public byte setColorization(byte colorization) {
        return -1;
    }

    @Override
    public void updateEntity() {
        if (worldObj.isRemote) return;

        tickCounter++;
        if (tickCounter < getWorkIntervalTicks()) return;
        tickCounter = 0;

        boolean wasActive = active;
        active = false;

        if (canWork()) {
            consumeEnergy();
            doWork();
        }

        if (active != wasActive) {
            markDirtyAndUpdate();
        }
    }

    protected boolean canWork() {
        return hasEnoughEnergy();
    }

    protected void markDirtyAndUpdate() {
        markDirty();
        worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setDouble("storedEnergy", storedEnergy);
        tag.setBoolean("active", active);
        writeOxygenToNBT(tag);
        writeMachineNBT(tag);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        storedEnergy = tag.getDouble("storedEnergy");
        active = tag.getBoolean("active");
        readOxygenFromNBT(tag);
        readMachineNBT(tag);
    }

    protected void writeMachineNBT(NBTTagCompound tag) {}

    protected void readMachineNBT(NBTTagCompound tag) {}

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound tag = new NBTTagCompound();
        writeToNBT(tag);
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, tag);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt) {
        readFromNBT(pkt.func_148857_g());
    }

    protected String energyUnitLabel() {
        return useEU() ? "EU" : "RF";
    }

    public boolean isItemValidForSlot(int slot, net.minecraft.item.ItemStack stack) {
        return true;
    }
}
