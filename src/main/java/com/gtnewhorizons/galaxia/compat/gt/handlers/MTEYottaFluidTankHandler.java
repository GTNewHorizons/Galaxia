package com.gtnewhorizons.galaxia.compat.gt.handlers;

import java.math.BigInteger;
import java.util.List;

import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizons.galaxia.compat.gt.GTFluidStorageHandler;

import goodgenerator.blocks.tileEntity.MTEYottaFluidTank;

public class MTEYottaFluidTankHandler extends GTFluidStorageHandler<MTEYottaFluidTank> {

    @Override
    public long getFluidStored(MTEYottaFluidTank attachment) {
        return attachment.mStorageCurrent.min(BigInteger.valueOf(Long.MAX_VALUE))
            .longValue();
    }

    @Override
    public long getFluidCapacity(MTEYottaFluidTank attachment) {
        return attachment.mStorage.min(BigInteger.valueOf(Long.MAX_VALUE))
            .longValue();
    }

    @Override
    public List<FluidStack> getAllFluids(MTEYottaFluidTank attachment) {
        if (attachment.mFluid == null || attachment.mFluid.amount <= 0) return List.of();
        return List.of(attachment.mFluid);
    }

    @Override
    public FluidStack drainFluid(MTEYottaFluidTank attachment, FluidStack fluid, boolean doDrain) {
        if (attachment.mFluid == null || !attachment.mFluid.isFluidEqual(fluid)) return null;
        long drained = Math.min(fluid.amount, attachment.mStorageCurrent.longValue());
        if (drained <= 0) return null;
        if (doDrain && !attachment.reduceFluid(drained)) return null;
        return new FluidStack(attachment.mFluid.getFluid(), (int) drained);
    }

    @Override
    public long fillFluid(MTEYottaFluidTank attachment, FluidStack resource, boolean doFill) {
        if (resource == null || resource.amount <= 0) return 0;
        if (attachment.mFluid != null && !attachment.mFluid.isFluidEqual(resource)) return 0;
        return attachment.addFluid(resource.amount, doFill) ? resource.amount : 0;
    }
}
