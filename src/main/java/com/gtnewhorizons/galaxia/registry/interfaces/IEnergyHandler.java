package com.gtnewhorizons.galaxia.registry.interfaces;

import java.math.BigInteger;

public interface IEnergyHandler<T> extends IAttachmentHandler<T> {

    BigInteger getEnergyStored(T attachment);

    BigInteger getEnergyCapacity(T attachment);

    long getPassiveDrain(T attachment);

    long getInputRate(T attachment);

    long getOutputRate(T attachment);

    long drawEnergy(T attachment, long amount);

    /**
     * Stores energy handed to the station
     *
     * @return Energy taken in, 0 for stores that only give
     */
    default long addEnergy(T attachment, long amount) {
        return 0;
    }
}
