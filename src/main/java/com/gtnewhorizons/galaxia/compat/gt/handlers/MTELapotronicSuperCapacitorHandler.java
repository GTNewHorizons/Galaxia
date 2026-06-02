package com.gtnewhorizons.galaxia.compat.gt.handlers;

import java.math.BigInteger;

import com.gtnewhorizons.galaxia.compat.gt.GTEnergyHandler;

import kekztech.common.tileentities.MTELapotronicSuperCapacitor;

public class MTELapotronicSuperCapacitorHandler extends GTEnergyHandler<MTELapotronicSuperCapacitor> {

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

    // TODO: Make it count towards the running average, maybe with station plugs/dynamo hatches
    @Override
    public long drawEnergy(MTELapotronicSuperCapacitor attachment, long amount) {
        BigInteger current = attachment.getStored();
        long drawn = Math.min(amount, attachment.maxEUOutput());
        attachment.setStored(current.subtract(BigInteger.valueOf(drawn)));
        return drawn;
    }
}
