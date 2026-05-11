package com.gtnewhorizons.galaxia.api;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidHandler;

public interface IOxygenHandler extends IFluidHandler {

    Fluid OXYGEN = FluidRegistry.getFluid("oxygen");

    String NBT_OXYGEN_TANK = "OxygenTank";

    FluidTank getOxygenTank();

    default int getStoredOxygen() {
        FluidStack fs = getOxygenTank().getFluid();
        return fs != null ? fs.amount : 0;
    }

    default int fillOxygen(int amount, boolean doFill) {
        if (OXYGEN == null) return 0;
        return getOxygenTank().fill(new FluidStack(OXYGEN, amount), doFill);
    }

    default int drainOxygen(int amount, boolean doDrain) {
        FluidStack drained = getOxygenTank().drain(amount, doDrain);
        return drained != null ? drained.amount : 0;
    }

    @Override
    default int fill(ForgeDirection from, FluidStack resource, boolean doFill) {
        if (OXYGEN == null || resource == null || resource.getFluid() != OXYGEN) return 0;
        return getOxygenTank().fill(resource, doFill);
    }

    @Override
    default FluidStack drain(ForgeDirection from, FluidStack resource, boolean doDrain) {
        if (OXYGEN == null || resource == null || resource.getFluid() != OXYGEN) return null;
        return getOxygenTank().drain(resource.amount, doDrain);
    }

    @Override
    default FluidStack drain(ForgeDirection from, int maxDrain, boolean doDrain) {
        return getOxygenTank().drain(maxDrain, doDrain);
    }

    @Override
    default boolean canFill(ForgeDirection from, Fluid fluid) {
        return OXYGEN != null && fluid == OXYGEN;
    }

    @Override
    default boolean canDrain(ForgeDirection from, Fluid fluid) {
        return OXYGEN != null && fluid == OXYGEN;
    }

    @Override
    default FluidTankInfo[] getTankInfo(ForgeDirection from) {
        return new FluidTankInfo[] { getOxygenTank().getInfo() };
    }

    default void writeOxygenToNBT(NBTTagCompound tag) {
        NBTTagCompound tankTag = new NBTTagCompound();
        getOxygenTank().writeToNBT(tankTag);
        tag.setTag(NBT_OXYGEN_TANK, tankTag);
    }

    default void readOxygenFromNBT(NBTTagCompound tag) {
        if (tag.hasKey(NBT_OXYGEN_TANK)) {
            getOxygenTank().readFromNBT(tag.getCompoundTag(NBT_OXYGEN_TANK));
        }
    }
}
