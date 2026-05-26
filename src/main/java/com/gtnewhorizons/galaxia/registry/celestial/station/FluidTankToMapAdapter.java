package com.gtnewhorizons.galaxia.registry.celestial.station;

import java.util.Map;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidTank;

import com.gtnewhorizons.galaxia.registry.interfaces.IDistributedInventory;
import com.gtnewhorizons.galaxia.registry.outpost.FluidKey;
import com.gtnewhorizons.galaxia.registry.outpost.ResourceFilter;

public class FluidTankToMapAdapter implements IDistributedInventory {

    private final IFluidTank tank;

    public FluidTankToMapAdapter(IFluidTank tank) {
        this.tank = tank;
    }

    @Override
    public Map<FluidKey, Long> getFluidAmounts() {
        FluidStack fluid = tank.getFluid();
        if (fluid == null || fluid.amount <= 0) return Map.of();
        return Map.of(FluidKey.of(fluid), (long) fluid.amount);
    }

    @Override
    public long totalFluidCapacity() {
        return tank.getCapacity();
    }

    @Override
    public long getFreeFluidSpace(FluidKey fluid) {
        if (!getFluidFilter().test(fluid)) return 0L;
        FluidStack stored = tank.getFluid();
        if (stored == null || stored.amount <= 0) return tank.getCapacity();
        if (stored.isFluidEqual(fluid.toStack(1))) {
            return tank.getCapacity() - stored.amount;
        }
        return 0L;
    }

    @Override
    public long insertIntoOwnFluidStorage(FluidKey fluid, long target) {
        if (fluid == null || target <= 0) return 0;
        int maxFill = tank.fill(fluid.toStack(Integer.MAX_VALUE), false);
        int toFill = (int) Math.min(target, maxFill);
        if (toFill <= 0) return 0;
        int filled = tank.fill(fluid.toStack(toFill), true);
        return filled;
    }

    @Override
    public long extractFromOwnFluidStorage(FluidKey fluid, long target) {
        if (fluid == null || target <= 0) return 0;
        FluidStack stored = tank.getFluid();
        if (stored == null || stored.amount <= 0 || !stored.isFluidEqual(fluid.toStack(1))) return 0;
        int maxDrain = Math.min(stored.amount, tank.drain(Integer.MAX_VALUE, false).amount);
        int toDrain = (int) Math.min(target, maxDrain);
        if (toDrain <= 0) return 0;
        FluidStack drained = tank.drain(toDrain, true);
        return drained != null ? drained.amount : 0;
    }

    @Override
    public ResourceFilter<FluidKey> getFluidFilter() {
        return ResourceFilter.forFluids();
    }
}
