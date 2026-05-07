package com.gtnewhorizons.galaxia.registry.outpost.module;

import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;

public record ModuleTierData(long baseEnergyCapacity, long powerDrawEuPerTick, int cooldownTicks,
    @Nullable Long capacity, @Nullable Map<String, Integer> variantCooldowns, Map<ItemStack, Long> constructionCost,
    int buildTicks, int completionRefundPercent) {

    public ModuleTierData {
        constructionCost = Map.copyOf(constructionCost);
        if (variantCooldowns != null) {
            variantCooldowns = Map.copyOf(variantCooldowns);
        }
        if (buildTicks <= 0) {
            throw new IllegalArgumentException("buildTicks must be > 0, got " + buildTicks);
        }
        if (completionRefundPercent < 0 || completionRefundPercent > 100) {
            throw new IllegalArgumentException(
                "completionRefundPercent must be in [0,100], got " + completionRefundPercent);
        }
    }

    public ModuleTierData(long baseEnergyCapacity, long powerDrawEuPerTick, int cooldownTicks, @Nullable Long capacity,
        Map<ItemStack, Long> constructionCost) {
        this(baseEnergyCapacity, powerDrawEuPerTick, cooldownTicks, capacity, null, constructionCost, 200, 80);
    }

    public boolean hasCapacity() {
        return capacity != null;
    }
}
