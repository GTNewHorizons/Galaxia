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

    public static Builder builder() {
        return new Builder();
    }

    public boolean hasCapacity() {
        return capacity != null;
    }

    public static final class Builder {

        private Long addedEnergyCapacity;
        private Long powerDraw;
        private Integer cooldown;
        private Long capacity;
        private Map<String, Integer> variantCooldowns;
        private Map<ItemStack, Long> cost;
        private int buildTicks = 200;
        private int refundPercent = 80;

        private Builder() {}

        public Builder addedEnergyCapacity(long addedEnergyCapacity) {
            this.addedEnergyCapacity = addedEnergyCapacity;
            return this;
        }

        public Builder powerDraw(long powerDraw) {
            this.powerDraw = powerDraw;
            return this;
        }

        public Builder cooldown(int cooldown) {
            this.cooldown = cooldown;
            return this;
        }

        public Builder capacity(long capacity) {
            this.capacity = capacity;
            return this;
        }

        public Builder variantCooldowns(Map<String, Integer> variantCooldowns) {
            this.variantCooldowns = variantCooldowns;
            return this;
        }

        public Builder cost(Map<ItemStack, Long> cost) {
            this.cost = cost;
            return this;
        }

        public Builder buildTicks(int buildTicks) {
            this.buildTicks = buildTicks;
            return this;
        }

        public Builder refundPercent(int refundPercent) {
            this.refundPercent = refundPercent;
            return this;
        }

        public ModuleTierData build() {
            return new ModuleTierData(
                require(addedEnergyCapacity, "addedEnergyCapacity"),
                require(powerDraw, "powerDraw"),
                require(cooldown, "cooldown"),
                capacity,
                variantCooldowns,
                require(cost, "cost"),
                buildTicks,
                refundPercent);
        }

        private static <T> T require(T value, String fieldName) {
            if (value == null) {
                throw new IllegalStateException("ModuleTierData.Builder: " + fieldName + " must be set");
            }
            return value;
        }
    }
}
