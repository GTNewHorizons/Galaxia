package com.gtnewhorizons.galaxia.compat.gt.handlers;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;

import com.gtnewhorizons.galaxia.compat.gt.GTFluidStorageHandler;

import kekztech.common.tileentities.MTETankTFFT;

public class MTETankTFFTHandler extends GTFluidStorageHandler<MTETankTFFT> {

    @Override
    public long getFluidStored(MTETankTFFT attachment) {
        return attachment.getStoredAmount()
            .min(BigInteger.valueOf(Long.MAX_VALUE))
            .longValue();
    }

    @Override
    public long getFluidCapacity(MTETankTFFT attachment) {
        BigInteger perFluid = BigInteger.valueOf(attachment.getCapacityPerFluid());
        return perFluid.multiply(BigInteger.valueOf(MTETankTFFT.MAX_DISTINCT_FLUIDS))
            .min(BigInteger.valueOf(Long.MAX_VALUE))
            .longValue();
    }

    @Override
    public List<FluidStack> getAllFluids(MTETankTFFT attachment) {
        List<FluidStack> result = new ArrayList<>();
        FluidTankInfo[] info = attachment.getTankInfo();
        if (info != null) {
            for (FluidTankInfo tankInfo : info) {
                if (tankInfo != null && tankInfo.fluid != null && tankInfo.fluid.amount > 0) {
                    result.add(tankInfo.fluid);
                }
            }
        }
        return result;
    }

    @Override
    public FluidStack drainFluid(MTETankTFFT attachment, FluidStack resource, boolean doDrain) {
        if (resource == null || resource.amount <= 0) return null;
        return attachment.push(resource, doDrain);
    }

    @Override
    public long fillFluid(MTETankTFFT attachment, FluidStack resource, boolean doFill) {
        if (resource == null || resource.amount <= 0) return 0;
        return attachment.pull(resource, doFill);
    }
}
