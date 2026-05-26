package com.gtnewhorizons.galaxia.registry.celestial.station;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizons.galaxia.registry.interfaces.IDistributedInventory;
import com.gtnewhorizons.galaxia.registry.interfaces.IFluidStorageHandler;
import com.gtnewhorizons.galaxia.registry.outpost.FluidKey;

@SuppressWarnings("rawtypes")
public class FluidAttachmentInventory implements IDistributedInventory {

    private final IFluidStorageHandler handler;
    private final Object attachment;

    public FluidAttachmentInventory(IFluidStorageHandler<?> handler, Object attachment) {
        this.handler = handler;
        this.attachment = attachment;
    }

    @Override
    public Map<FluidKey, Long> getFluidAmounts() {
        List<FluidStack> fluids = handler.getAllFluids(attachment);
        if (fluids.isEmpty()) return Map.of();
        Map<FluidKey, Long> map = new LinkedHashMap<>();
        for (FluidStack fs : fluids) {
            if (fs != null && fs.amount > 0) {
                map.merge(FluidKey.of(fs), (long) fs.amount, Long::sum);
            }
        }
        return map;
    }

    @Override
    public long totalFluidCapacity() {
        return handler.getFluidCapacity(attachment);
    }

    @Override
    public long getFreeFluidSpace(FluidKey fluid) {
        if (!getFluidFilter().test(fluid)) return 0L;
        long capacity = handler.getFluidCapacity(attachment);
        long stored = handler.getFluidStored(attachment);
        return Math.max(0, capacity - stored);
    }

    @Override
    public long insertIntoOwnFluidStorage(FluidKey fluid, long target) {
        int toFill = (int) Math.min(target, Integer.MAX_VALUE);
        return Math.max(0, handler.fillFluid(attachment, fluid.toStack(toFill), true));
    }

    @Override
    public long extractFromOwnFluidStorage(FluidKey fluid, long target) {
        FluidStack drained = handler.drainFluid(attachment, Math.min(target, Integer.MAX_VALUE), true);
        return drained != null ? drained.amount : 0;
    }
}
