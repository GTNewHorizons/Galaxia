package com.gtnewhorizons.galaxia.testing;

import java.lang.reflect.Field;

import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import sun.misc.Unsafe;

public final class TestFluidStacks {

    private static final Unsafe UNSAFE = unsafe();
    private static final Field FLUID_FIELD = fluidField();

    private TestFluidStacks() {}

    public static FluidStack stack(String fluidName, int amount) {
        try {
            FluidStack stack = (FluidStack) UNSAFE.allocateInstance(FluidStack.class);
            FLUID_FIELD.set(stack, new Fluid(fluidName));
            stack.amount = amount;
            return stack;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    public static String name(FluidStack stack) {
        Fluid fluid = fluid(stack);
        return fluid != null ? fluid.getName() : null;
    }

    public static Fluid fluid(FluidStack stack) {
        try {
            return stack.getFluid();
        } catch (RuntimeException ignored) {
            try {
                return (Fluid) FLUID_FIELD.get(stack);
            } catch (IllegalAccessException e) {
                throw new AssertionError(e);
            }
        }
    }

    private static Field fluidField() {
        try {
            Field field = FluidStack.class.getDeclaredField("fluid");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static Unsafe unsafe() {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            return (Unsafe) field.get(null);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
