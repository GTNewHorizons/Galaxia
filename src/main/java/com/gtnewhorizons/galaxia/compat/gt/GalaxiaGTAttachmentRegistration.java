package com.gtnewhorizons.galaxia.compat.gt;

import java.math.BigInteger;

import com.gtnewhorizons.galaxia.api.BlockPos;
import com.gtnewhorizons.galaxia.registry.celestial.station.StationAttachmentRegistry;
import com.gtnewhorizons.galaxia.registry.interfaces.IEnergyHandler;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import kekztech.common.tileentities.MTELapotronicSuperCapacitor;

public final class GalaxiaGTAttachmentRegistration {

    private GalaxiaGTAttachmentRegistration() {}

    public static void init() {
        StationAttachmentRegistry.register(
            MTELapotronicSuperCapacitor.class,
            new IEnergyHandler<MTELapotronicSuperCapacitor>() {

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
}
