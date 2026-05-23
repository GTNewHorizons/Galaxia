package com.gtnewhorizons.galaxia.registry.outpost.upkeep;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nonnull;

import com.gtnewhorizons.galaxia.registry.outpost.FluidKey;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;

public record UpkeepDemand(@Nonnull Map<ItemStackWrapper, UpkeepAmount> itemsPerMinute,
    @Nonnull Map<FluidKey, UpkeepAmount> fluidsPerMinute) {

    public static final UpkeepDemand EMPTY = new UpkeepDemand(Map.of(), Map.of());

    public UpkeepDemand {
        itemsPerMinute = normalizeItems(itemsPerMinute);
        fluidsPerMinute = normalizeFluids(fluidsPerMinute);
    }

    public static @Nonnull Builder builder() {
        return new Builder();
    }

    public boolean isEmpty() {
        return itemsPerMinute.isEmpty() && fluidsPerMinute.isEmpty();
    }

    public @Nonnull UpkeepDemand plus(@Nonnull UpkeepDemand other) {
        Objects.requireNonNull(other, "other");
        if (other.isEmpty()) return this;
        if (isEmpty()) return other;

        Builder builder = builder();
        itemsPerMinute.forEach(builder::item);
        fluidsPerMinute.forEach(builder::fluid);
        other.itemsPerMinute.forEach(builder::item);
        other.fluidsPerMinute.forEach(builder::fluid);
        return builder.build();
    }

    public @Nonnull UpkeepDemand multiplyPercent(int percent) {
        if (percent < 0) {
            throw new IllegalArgumentException("percent must be >= 0, got " + percent);
        }
        if (percent == 100 || isEmpty()) return this;
        if (percent == 0) return EMPTY;

        Builder builder = builder();
        itemsPerMinute.forEach((item, amount) -> builder.item(item, amount.multiplyPercent(percent)));
        fluidsPerMinute.forEach((fluid, amount) -> builder.fluid(fluid, amount.multiplyPercent(percent)));
        return builder.build();
    }

    private static Map<ItemStackWrapper, UpkeepAmount> normalizeItems(Map<ItemStackWrapper, UpkeepAmount> source) {
        Objects.requireNonNull(source, "itemsPerMinute");
        Map<ItemStackWrapper, UpkeepAmount> result = new LinkedHashMap<>();
        for (Map.Entry<ItemStackWrapper, UpkeepAmount> entry : source.entrySet()) {
            ItemStackWrapper item = Objects.requireNonNull(entry.getKey(), "upkeep item");
            UpkeepAmount amount = Objects.requireNonNull(entry.getValue(), "upkeep item amount");
            if (amount.isZero()) {
                throw new IllegalArgumentException("upkeep item amount must be > 0 for " + item.toKey());
            }
            result.put(item, amount);
        }
        return Collections.unmodifiableMap(result);
    }

    private static Map<FluidKey, UpkeepAmount> normalizeFluids(Map<FluidKey, UpkeepAmount> source) {
        Objects.requireNonNull(source, "fluidsPerMinute");
        Map<FluidKey, UpkeepAmount> result = new LinkedHashMap<>();
        for (Map.Entry<FluidKey, UpkeepAmount> entry : source.entrySet()) {
            FluidKey fluid = Objects.requireNonNull(entry.getKey(), "upkeep fluid");
            UpkeepAmount amount = Objects.requireNonNull(entry.getValue(), "upkeep fluid amount");
            if (amount.isZero()) {
                throw new IllegalArgumentException("upkeep fluid amount must be > 0 for " + fluid.toKey());
            }
            result.put(fluid, amount);
        }
        return Collections.unmodifiableMap(result);
    }

    public static final class Builder {

        private final Map<ItemStackWrapper, UpkeepAmount> itemsPerMinute = new LinkedHashMap<>();
        private final Map<FluidKey, UpkeepAmount> fluidsPerMinute = new LinkedHashMap<>();

        private Builder() {}

        public @Nonnull Builder item(@Nonnull ItemStackWrapper item, long amount) {
            return item(item, UpkeepAmount.ofWhole(amount));
        }

        public @Nonnull Builder item(@Nonnull ItemStackWrapper item, @Nonnull UpkeepAmount amount) {
            if (amount != null && !amount.isZero()) {
                itemsPerMinute.merge(Objects.requireNonNull(item, "item"), amount, UpkeepAmount::plus);
            }
            return this;
        }

        public @Nonnull Builder fluid(@Nonnull FluidKey fluid, long amount) {
            return fluid(fluid, UpkeepAmount.ofWhole(amount));
        }

        public @Nonnull Builder fluid(@Nonnull FluidKey fluid, @Nonnull UpkeepAmount amount) {
            if (amount != null && !amount.isZero()) {
                fluidsPerMinute.merge(Objects.requireNonNull(fluid, "fluid"), amount, UpkeepAmount::plus);
            }
            return this;
        }

        public @Nonnull UpkeepDemand build() {
            if (itemsPerMinute.isEmpty() && fluidsPerMinute.isEmpty()) return EMPTY;
            return new UpkeepDemand(itemsPerMinute, fluidsPerMinute);
        }
    }
}
