package com.gtnewhorizons.galaxia.registry.outpost.module;

import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;

import com.gtnewhorizons.galaxia.registry.outpost.FluidKey;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.upkeep.UpkeepAmount;
import com.gtnewhorizons.galaxia.registry.outpost.upkeep.UpkeepDemand;

public record ModuleTierData(long baseEnergyCapacity, long powerDrawEuPerTick, int cooldownTicks,
    @Nullable Long capacity, @Nullable Map<String, Integer> variantCooldowns, @Nullable Integer chargeTicks,
    @Nullable Map<String, Integer> variantChargeTicks, Map<ItemStack, Long> constructionCost, int buildTicks,
    int completionRefundPercent, UpkeepDemand upkeepDemand) {

    public ModuleTierData {
        constructionCost = Map.copyOf(constructionCost);
        upkeepDemand = upkeepDemand == null ? UpkeepDemand.EMPTY : upkeepDemand;
        if (variantCooldowns != null) {
            variantCooldowns = Map.copyOf(variantCooldowns);
        }
        if (variantChargeTicks != null) {
            variantChargeTicks = Map.copyOf(variantChargeTicks);
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
        this(
            baseEnergyCapacity,
            powerDrawEuPerTick,
            cooldownTicks,
            capacity,
            null,
            null,
            null,
            constructionCost,
            200,
            80,
            UpkeepDemand.EMPTY);
    }

    public ModuleTierData(long baseEnergyCapacity, long powerDrawEuPerTick, int cooldownTicks, @Nullable Long capacity,
        @Nullable Map<String, Integer> variantCooldowns, Map<ItemStack, Long> constructionCost, int buildTicks,
        int completionRefundPercent) {
        this(
            baseEnergyCapacity,
            powerDrawEuPerTick,
            cooldownTicks,
            capacity,
            variantCooldowns,
            null,
            null,
            constructionCost,
            buildTicks,
            completionRefundPercent,
            UpkeepDemand.EMPTY);
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
        private Integer chargeTicks;
        private Map<String, Integer> variantChargeTicks;
        private Map<ItemStack, Long> cost;
        private int buildTicks = 200;
        private int refundPercent = 80;
        private UpkeepDemand upkeepDemand = UpkeepDemand.EMPTY;

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

        public Builder chargeTicks(int chargeTicks) {
            this.chargeTicks = chargeTicks;
            return this;
        }

        public Builder variantChargeTicks(Map<String, Integer> variantChargeTicks) {
            this.variantChargeTicks = variantChargeTicks;
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

        public Builder upkeep(UpkeepDemand upkeepDemand) {
            this.upkeepDemand = require(upkeepDemand, "upkeepDemand");
            return this;
        }

        public Builder upkeepItem(ItemStack item, long amountPerMinute) {
            return upkeepItem(item, UpkeepAmount.ofWhole(amountPerMinute));
        }

        public Builder upkeepItem(ItemStack item, String amountPerMinute) {
            return upkeepItem(item, UpkeepAmount.parse(amountPerMinute));
        }

        public Builder upkeepItem(ItemStack item, UpkeepAmount amountPerMinute) {
            this.upkeepDemand = upkeepDemand.plus(
                UpkeepDemand.builder()
                    .item(ItemStackWrapper.of(require(item, "upkeepItem")), amountPerMinute)
                    .build());
            return this;
        }

        public Builder upkeepFluid(String fluidName, long amountPerMinute) {
            return upkeepFluid(
                require(FluidKey.fromName(fluidName), "upkeepFluid"),
                UpkeepAmount.ofWhole(amountPerMinute));
        }

        public Builder upkeepFluid(String fluidName, String amountPerMinute) {
            return upkeepFluid(
                require(FluidKey.fromName(fluidName), "upkeepFluid"),
                UpkeepAmount.parse(amountPerMinute));
        }

        public Builder upkeepFluid(FluidKey fluid, UpkeepAmount amountPerMinute) {
            this.upkeepDemand = upkeepDemand.plus(
                UpkeepDemand.builder()
                    .fluid(require(fluid, "upkeepFluid"), amountPerMinute)
                    .build());
            return this;
        }

        public ModuleTierData build() {
            return new ModuleTierData(
                require(addedEnergyCapacity, "addedEnergyCapacity"),
                require(powerDraw, "powerDraw"),
                require(cooldown, "cooldown"),
                capacity,
                variantCooldowns,
                chargeTicks,
                variantChargeTicks,
                require(cost, "cost"),
                buildTicks,
                refundPercent,
                upkeepDemand);
        }

        private static <T> T require(T value, String fieldName) {
            if (value == null) {
                throw new IllegalStateException("ModuleTierData.Builder: " + fieldName + " must be set");
            }
            return value;
        }
    }
}
