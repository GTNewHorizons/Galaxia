package com.gtnewhorizons.galaxia.registry.celestial.station.devices;

import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;

/**
 * Breathes oxygen into the room it stands in
 */
public class TileOxygenator extends TileStationFluidDevice {

    @Override
    protected String blockName() {
        return "oxygenator";
    }

    public static final long ENERGY_USAGE = 1024;
    public static final int OXYGEN_PER_SECOND = 20;

    public TileOxygenator() {
        super(16_000);
    }

    @Override
    public long getEnergyUsage() {
        return ENERGY_USAGE;
    }

    @Override
    protected Fluid fluid() {
        return FluidRegistry.getFluid("oxygen");
    }

    @Override
    protected int litresPerSecond() {
        return OXYGEN_PER_SECOND;
    }

    @Override
    protected String missingFluidKey() {
        return "galaxia.gui.station_device.no_oxygen";
    }
}
