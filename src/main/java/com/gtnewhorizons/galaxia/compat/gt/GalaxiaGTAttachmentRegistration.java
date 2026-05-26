package com.gtnewhorizons.galaxia.compat.gt;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;

import com.gtnewhorizons.galaxia.api.BlockPos;
import com.gtnewhorizons.galaxia.registry.celestial.station.attachments.StationAttachmentRegistry;
import com.gtnewhorizons.galaxia.registry.interfaces.IEnergyHandler;
import com.gtnewhorizons.galaxia.registry.interfaces.IFluidStorageHandler;

import goodgenerator.blocks.tileEntity.MTEYottaFluidTank;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import kekztech.common.tileentities.MTELapotronicSuperCapacitor;
import kekztech.common.tileentities.MTETankTFFT;

public final class GalaxiaGTAttachmentRegistration {

    private GalaxiaGTAttachmentRegistration() {}

    public static void init() {
        registerEnergyHandlers();
        registerFluidHandlers();
    }

    private static void registerEnergyHandlers() {
        StationAttachmentRegistry.register(MTELapotronicSuperCapacitor.class, new IEnergyHandler<>() {

            @Override
            public BlockPos getPosition(MTELapotronicSuperCapacitor attachment) {
                IGregTechTileEntity base = attachment.getBaseMetaTileEntity();
                if (base == null) return null;
                return new BlockPos(base.getXCoord(), base.getYCoord(), base.getZCoord());
            }

            @Override
            public void tick(MTELapotronicSuperCapacitor attachment) {}

            @Override
            public boolean isReady(MTELapotronicSuperCapacitor attachment) {
                IGregTechTileEntity base = attachment.getBaseMetaTileEntity();
                return base != null && !base.isDead();
            }

            @Override
            public BigInteger getEnergyStored(MTELapotronicSuperCapacitor attachment) {
                return attachment.getStored();
            }

            @Override
            public BigInteger getEnergyCapacity(MTELapotronicSuperCapacitor attachment) {
                return attachment.getEnergyCapacity();
            }

            @Override
            public long getPassiveDrain(MTELapotronicSuperCapacitor attachment) {
                return attachment.getPassiveDischargeAmount();
            }

            @Override
            public long getInputRate(MTELapotronicSuperCapacitor attachment) {
                return attachment.getEnergyInputValues()
                    .avgLong();
            }

            @Override
            public long getOutputRate(MTELapotronicSuperCapacitor attachment) {
                return attachment.getEnergyOutputValues()
                    .avgLong();
            }

            @Override
            public long drawEnergy(MTELapotronicSuperCapacitor attachment, long amount) {
                BigInteger current = attachment.getStored();
                long drawn = Math.min(
                    amount,
                    current.min(BigInteger.valueOf(Long.MAX_VALUE))
                        .longValue());
                attachment.setStored(current.subtract(BigInteger.valueOf(drawn)));
                return drawn;
            }
        });
    }

    private static void registerFluidHandlers() {
        StationAttachmentRegistry.register(MTEYottaFluidTank.class, new IFluidStorageHandler<>() {

            @Override
            public BlockPos getPosition(MTEYottaFluidTank attachment) {
                IGregTechTileEntity base = attachment.getBaseMetaTileEntity();
                if (base == null) return null;
                return new BlockPos(base.getXCoord(), base.getYCoord(), base.getZCoord());
            }

            @Override
            public void tick(MTEYottaFluidTank attachment) {}

            @Override
            public boolean isReady(MTEYottaFluidTank attachment) {
                IGregTechTileEntity base = attachment.getBaseMetaTileEntity();
                return base != null && !base.isDead();
            }

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
            public FluidStack drainFluid(MTEYottaFluidTank attachment, long amount, boolean doDrain) {
                if (attachment.mFluid == null) return null;
                long drained = Math.min(amount, attachment.mStorageCurrent.longValue());
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
        });

        StationAttachmentRegistry.register(MTETankTFFT.class, new IFluidStorageHandler<>() {

            @Override
            public BlockPos getPosition(MTETankTFFT attachment) {
                IGregTechTileEntity base = attachment.getBaseMetaTileEntity();
                if (base == null) return null;
                return new BlockPos(base.getXCoord(), base.getYCoord(), base.getZCoord());
            }

            @Override
            public void tick(MTETankTFFT attachment) {}

            @Override
            public boolean isReady(MTETankTFFT attachment) {
                IGregTechTileEntity base = attachment.getBaseMetaTileEntity();
                return base != null && !base.isDead() && base.isActive();
            }

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
                net.minecraftforge.fluids.FluidTankInfo[] info = attachment.getTankInfo();
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
            public FluidStack drainFluid(MTETankTFFT attachment, long amount, boolean doDrain) {
                if (amount <= 0) return null;
                int intAmount = (int) Math.min(amount, Integer.MAX_VALUE);
                return attachment.push(intAmount, doDrain);
            }

            @Override
            public long fillFluid(MTETankTFFT attachment, FluidStack resource, boolean doFill) {
                if (resource == null || resource.amount <= 0) return 0;
                return attachment.pull(resource, doFill);
            }
        });
    }
}
