package com.gtnewhorizons.galaxia.registry.block.planet.fluid;

import net.minecraftforge.fluids.Fluid;

public class FluidCustom extends Fluid {

    public FluidCustom(String name, int density, int viscosity, int temperature, int luminosity) {
        super(name);
        setDensity(density);
        setViscosity(viscosity);
        setTemperature(temperature);
        setGaseous(false);
        setLuminosity(luminosity);
    }
}
