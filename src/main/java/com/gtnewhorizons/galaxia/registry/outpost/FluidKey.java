package com.gtnewhorizons.galaxia.registry.outpost;

import javax.annotation.Nullable;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

/**
 * A stable, amount-independent identity key for a fluid.
 *
 * <p>
 * Replaces direct {@link FluidStack} use as a map/set key. {@link FluidStack}
 * is mutable and its equality semantics include the stored amount, making
 * it unsuitable as a key in aggregation maps.
 */
public record FluidKey(Fluid fluid, @Nullable NBTTagCompound tag) implements InventoryKey {

    /**
     * Constructs a {@code FluidKey} from an existing {@link FluidStack}.
     */
    public static FluidKey of(FluidStack stack) {
        return new FluidKey(stack.getFluid(), stack.tag);
    }

    /**
     * Reconstructs a {@link FluidStack} with the given volume.
     */
    public FluidStack toStack(int amount) {
        return tag == null ? new FluidStack(fluid, amount) : new FluidStack(fluid, amount, tag);
    }

    /**
     * Looks up a Fluid by registry name and wraps it in a tagless FluidKey.
     */
    public static @Nullable FluidKey fromName(String fluidName) {
        if (fluidName == null || fluidName.isEmpty()) return null;
        try {
            Fluid fluid = FluidRegistry.getFluid(fluidName);
            return fluid != null ? new FluidKey(fluid, null) : null;
        } catch (Throwable e) {
            return null;
        }
    }
}
