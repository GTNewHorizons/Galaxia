package com.gtnewhorizons.galaxia.registry.outpost.upkeep;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;

public record UpkeepDemand(Map<ItemStackWrapper, Long> itemsPerMinute, Map<String, Long> fluidsPerMinute) {

    public static final UpkeepDemand EMPTY = new UpkeepDemand(Map.of(), Map.of());

    public UpkeepDemand {
        itemsPerMinute = normalizeItems(itemsPerMinute);
        fluidsPerMinute = normalizeFluids(fluidsPerMinute);
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean isEmpty() {
        return itemsPerMinute.isEmpty() && fluidsPerMinute.isEmpty();
    }

    public UpkeepDemand plus(UpkeepDemand other) {
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

    public UpkeepDemand multiplyPercent(int percent) {
        if (percent < 0) {
            throw new IllegalArgumentException("percent must be >= 0, got " + percent);
        }
        if (percent == 100 || isEmpty()) return this;
        if (percent == 0) return EMPTY;

        Builder builder = builder();
        itemsPerMinute.forEach((item, amount) -> builder.item(item, scaleCeil(amount, percent)));
        fluidsPerMinute.forEach((fluid, amount) -> builder.fluid(fluid, scaleCeil(amount, percent)));
        return builder.build();
    }

    private static long scaleCeil(long amount, int percent) {
        return (amount * percent + 99L) / 100L;
    }

    private static Map<ItemStackWrapper, Long> normalizeItems(Map<ItemStackWrapper, Long> source) {
        Objects.requireNonNull(source, "itemsPerMinute");
        Map<ItemStackWrapper, Long> result = new LinkedHashMap<>();
        for (Map.Entry<ItemStackWrapper, Long> entry : source.entrySet()) {
            ItemStackWrapper item = Objects.requireNonNull(entry.getKey(), "upkeep item");
            Long amount = Objects.requireNonNull(entry.getValue(), "upkeep item amount");
            if (amount <= 0L) {
                throw new IllegalArgumentException("upkeep item amount must be > 0 for " + item.toKey());
            }
            result.merge(item, amount, Long::sum);
        }
        return Collections.unmodifiableMap(result);
    }

    private static Map<String, Long> normalizeFluids(Map<String, Long> source) {
        Objects.requireNonNull(source, "fluidsPerMinute");
        Map<String, Long> result = new LinkedHashMap<>();
        for (Map.Entry<String, Long> entry : source.entrySet()) {
            String fluid = Objects.requireNonNull(entry.getKey(), "upkeep fluid");
            if (fluid.isBlank()) {
                throw new IllegalArgumentException("upkeep fluid name must not be blank");
            }
            Long amount = Objects.requireNonNull(entry.getValue(), "upkeep fluid amount");
            if (amount <= 0L) {
                throw new IllegalArgumentException("upkeep fluid amount must be > 0 for " + fluid);
            }
            result.merge(fluid, amount, Long::sum);
        }
        return Collections.unmodifiableMap(result);
    }

    public static final class Builder {

        private final Map<ItemStackWrapper, Long> itemsPerMinute = new LinkedHashMap<>();
        private final Map<String, Long> fluidsPerMinute = new LinkedHashMap<>();

        private Builder() {}

        public Builder item(ItemStackWrapper item, long amount) {
            if (amount > 0L) {
                itemsPerMinute.merge(Objects.requireNonNull(item, "item"), amount, Long::sum);
            }
            return this;
        }

        public Builder fluid(String fluidName, long amount) {
            if (amount > 0L) {
                fluidsPerMinute.merge(Objects.requireNonNull(fluidName, "fluidName"), amount, Long::sum);
            }
            return this;
        }

        public UpkeepDemand build() {
            if (itemsPerMinute.isEmpty() && fluidsPerMinute.isEmpty()) return EMPTY;
            return new UpkeepDemand(itemsPerMinute, fluidsPerMinute);
        }
    }
}
