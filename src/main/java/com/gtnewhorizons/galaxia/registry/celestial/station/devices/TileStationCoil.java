package com.gtnewhorizons.galaxia.registry.celestial.station.devices;

import javax.annotation.Nullable;

/**
 * Keeps the room at a bearable temperature, it only needs energy
 */
public abstract class TileStationCoil extends TileStationDevice {

    public static final long ENERGY_USAGE = 1024;

    @Override
    public long getEnergyUsage() {
        return ENERGY_USAGE;
    }

    @Override
    protected @Nullable String missingInput() {
        return null;
    }

    @Override
    protected void consumeInput() {}

    @Override
    protected int contentHeight() {
        return 0;
    }
}
