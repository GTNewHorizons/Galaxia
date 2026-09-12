package com.gtnewhorizons.galaxia.testing;

import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

public final class TestFluidStacks {

    private TestFluidStacks() {}

    public static FluidStack stack(String fluidName, int amount) {
        Fluid fluid = FluidRegistry.getFluid(fluidName);
        if (fluid == null) {
            FluidRegistry.registerFluid(new Fluid(fluidName));
            fluid = FluidRegistry.getFluid(fluidName);
        }
        return new FluidStack(fluid, amount);
    }

    public static String name(FluidStack stack) {
        Fluid fluid = stack.getFluid();
        return fluid != null ? fluid.getName() : null;
    }
}
