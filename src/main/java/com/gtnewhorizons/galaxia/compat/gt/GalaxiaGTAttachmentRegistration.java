package com.gtnewhorizons.galaxia.compat.gt;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;

import com.gtnewhorizons.galaxia.registry.celestial.station.attachments.StationAttachmentRegistry;

import goodgenerator.blocks.tileEntity.MTEYottaFluidTank;
import gregtech.api.enums.GTValues;
import kekztech.common.tileentities.MTELapotronicSuperCapacitor;
import kekztech.common.tileentities.MTETankTFFT;

public final class GalaxiaGTAttachmentRegistration {

    private GalaxiaGTAttachmentRegistration() {}

    public static final int stationHatchId = 23050;
    public static final int stationAutoHatchId = 23051;

    public static MTEHatchStationMaintenance mteHatchStationMaintenance;
    public static MTEHatchStationMaintenance mteHatchAutoStationMaintenance;

    public static void init() {
        registerHatches();
        registerEnergyHandlers();
        registerFluidHandlers();
        registerStationPlugs();
    }

    private static void registerHatches() {
        mteHatchStationMaintenance = new MTEHatchStationMaintenance(
            stationHatchId,
            "hatch.station_controller",
            "Station Controller Hatch",
            1,
            false);
        mteHatchAutoStationMaintenance = new MTEHatchStationMaintenance(
            stationAutoHatchId,
            "hatch.station_controller",
            "Station Controller Auto Hatch",
            1,
            true);
    }

    private static void registerEnergyHandlers() {
        StationAttachmentRegistry.register(MTELapotronicSuperCapacitor.class, new GTEnergyHandler<>() {

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
        StationAttachmentRegistry.register(MTEYottaFluidTank.class, new GTFluidStorageHandler<>() {

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
                if (attachment.mFluid == null || attachment.mFluid.isFluidEqual(fluid)) return null;
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
        });

        StationAttachmentRegistry.register(MTETankTFFT.class, new GTFluidStorageHandler<>() {

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
        });
    }

    public static final Set<Integer> plugId = new HashSet<>();

    private static void registerStationPlugs() {
        int[] amps = new int[] { 4, 16, 64 };
        for (int tier = 0; tier < GTValues.V.length; tier++) {
            int id = MTEStationPlug.ID + tier;
            plugId.add(id);
            new MTEStationPlug(id, "station_plug_" + tier, "Station Plug " + tier, tier);
            for (int i = 0; i < amps.length; i++) {
                id = MTEStationPlugMulti.ID + tier * 10 + i;
                plugId.add(id);
                new MTEStationPlugMulti(
                    id,
                    "station_plug_multi_" + amps[i] + "a_t" + tier,
                    "Station Plug " + amps[i] + "A (Tier " + tier + ")",
                    tier,
                    amps[i]);
            }
        }

    }
}
