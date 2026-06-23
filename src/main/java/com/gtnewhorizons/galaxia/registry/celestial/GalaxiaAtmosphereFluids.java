package com.gtnewhorizons.galaxia.registry.celestial;

import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;

import gregtech.api.enums.FluidState;
import gregtech.api.fluid.GTFluidFactory;

public final class GalaxiaAtmosphereFluids {

    public static final String OXYGEN_NITROGEN_ATMOSPHERE_NAME = "galaxia.oxygen_nitrogen_atmosphere";

    private static Fluid oxygenNitrogenAtmosphere;

    private GalaxiaAtmosphereFluids() {}

    public static void init() {
        if (oxygenNitrogenAtmosphere != null) return;

        // TODO: define finite preset atmospheres for future generated celestial bodies instead of creating unbounded
        // per-body fluids.
        oxygenNitrogenAtmosphere = registerAtmosphereGas(
            OXYGEN_NITROGEN_ATMOSPHERE_NAME,
            "Oxygen-Nitrogen Atmosphere",
            295);
    }

    public static Fluid oxygenNitrogenAtmosphere() {
        if (oxygenNitrogenAtmosphere == null) {
            throw new IllegalStateException("Galaxia atmosphere fluids have not been initialized");
        }
        return oxygenNitrogenAtmosphere;
    }

    private static Fluid registerAtmosphereGas(String fluidName, String localizedName, int temperatureK) {
        Fluid existing = FluidRegistry.getFluid(fluidName);
        if (existing != null) {
            throw new IllegalStateException(
                "Atmosphere fluid is already registered before Galaxia initialization: " + fluidName);
        }

        Fluid fluid = GTFluidFactory.of(fluidName, localizedName, FluidState.GAS, temperatureK);
        Fluid registered = FluidRegistry.getFluid(fluidName);
        if (registered != fluid) {
            throw new IllegalStateException("GT failed to register Galaxia atmosphere fluid: " + fluidName);
        }
        return fluid;
    }
}
