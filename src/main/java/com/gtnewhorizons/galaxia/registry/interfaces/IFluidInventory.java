package com.gtnewhorizons.galaxia.registry.interfaces;

import java.util.Map;

import javax.annotation.Nonnull;

public interface IFluidInventory {

    long getFluidAmount(String fluidName);

    @Nonnull
    Map<String, Long> fluidSnapshot();

    boolean hasFluidLowerBound(String fluidName);

    boolean hasFluidUpperBound(String fluidName);

    long fluidLowerBoundOrDefault(String fluidName);

    long fluidUpperBoundOrDefault(String fluidName);

    @Nonnull
    Map<String, Long> fluidLowerBoundsSnapshot();

    @Nonnull
    Map<String, Long> fluidUpperBoundsSnapshot();
}
