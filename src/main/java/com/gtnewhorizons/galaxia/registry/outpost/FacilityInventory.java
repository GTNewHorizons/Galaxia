package com.gtnewhorizons.galaxia.registry.outpost;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class FacilityInventory {

    enum ExchangeResult {
        REJECTED,
        UNCHANGED,
        CHANGED
    }

    record ReturnItemsResult(Map<ItemStackWrapper, Long> remaining, boolean changed) {

        boolean completed() {
            return remaining.isEmpty();
        }
    }

    private final Map<ItemStackWrapper, Long> itemAmounts = new LinkedHashMap<>();
    private final Map<FluidKey, Long> fluidAmounts = new LinkedHashMap<>();
    private final ResourceFilter<ItemStackWrapper> itemFilter = ResourceFilter.forItems();
    private final ResourceFilter<FluidKey> fluidFilter = ResourceFilter.forFluids();

    long insert(InventoryKey resource, long requested, long itemCapacity) {
        requireNonNegative(requested, "insertion");
        if (resource == null || requested == 0L || !allowsInsertion(resource)) return 0L;
        if (resource instanceof ItemStackWrapper item) {
            return insertItem(item, requested, itemCapacity);
        }
        return insertFluid((FluidKey) resource, requested);
    }

    long extract(InventoryKey resource, long requested) {
        requireNonNegative(requested, "extraction");
        if (resource == null || requested == 0L) return 0L;
        if (resource instanceof ItemStackWrapper item) {
            return extract(itemAmounts, item, requested);
        }
        return extract(fluidAmounts, (FluidKey) resource, requested);
    }

    ExchangeResult tryExchange(InventoryExchange exchange, long itemCapacity) {
        if (exchange == null || exchange.isEmpty()) return ExchangeResult.REJECTED;
        if (!allowsOutputs(exchange)) return ExchangeResult.REJECTED;

        Set<InventoryKey> touched = touchedResources(exchange);
        Map<InventoryKey, Long> finalAmounts = new LinkedHashMap<>();
        boolean changed = false;
        for (InventoryKey resource : touched) {
            long stored = amount(resource);
            long input = exchange.inputAmount(resource);
            if (stored < input) return ExchangeResult.REJECTED;
            try {
                long finalAmount = Math.addExact(stored - input, exchange.outputAmount(resource));
                finalAmounts.put(resource, finalAmount);
                changed |= finalAmount != stored;
            } catch (ArithmeticException ignored) {
                return ExchangeResult.REJECTED;
            }
        }
        if (!exchange.itemOutputs()
            .isEmpty() && !fitsItemCapacity(finalAmounts, itemCapacity)) {
            return ExchangeResult.REJECTED;
        }
        if (!changed) return ExchangeResult.UNCHANGED;

        for (Map.Entry<InventoryKey, Long> entry : finalAmounts.entrySet()) {
            setAmount(entry.getKey(), entry.getValue());
        }
        return ExchangeResult.CHANGED;
    }

    ReturnItemsResult returnItems(Map<ItemStackWrapper, Long> requested, long itemCapacity) {
        if (requested == null) throw new IllegalArgumentException("Returned items must not be null");
        Map<ItemStackWrapper, Long> validated = new LinkedHashMap<>();
        for (Map.Entry<ItemStackWrapper, Long> entry : requested.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || entry.getValue() <= 0L) {
                throw new IllegalArgumentException("Returned items must contain positive amounts and non-null keys");
            }
            validated.put(entry.getKey(), entry.getValue());
        }

        long available = availableItemCapacity(itemCapacity);
        Map<ItemStackWrapper, Long> accepted = new LinkedHashMap<>();
        Map<ItemStackWrapper, Long> remaining = new LinkedHashMap<>();
        for (Map.Entry<ItemStackWrapper, Long> entry : validated.entrySet()) {
            long applied = Math.min(entry.getValue(), available);
            if (applied > 0L) {
                long stored = itemAmounts.getOrDefault(entry.getKey(), 0L);
                try {
                    Math.addExact(stored, applied);
                } catch (ArithmeticException overflow) {
                    throw new IllegalArgumentException("Returned item amount overflows for " + entry.getKey());
                }
                accepted.put(entry.getKey(), applied);
                available -= applied;
            }
            long leftover = entry.getValue() - applied;
            if (leftover > 0L) remaining.put(entry.getKey(), leftover);
        }
        for (Map.Entry<ItemStackWrapper, Long> entry : accepted.entrySet()) {
            itemAmounts.merge(entry.getKey(), entry.getValue(), Math::addExact);
        }
        return new ReturnItemsResult(Collections.unmodifiableMap(remaining), !accepted.isEmpty());
    }

    long amount(InventoryKey resource) {
        if (resource instanceof ItemStackWrapper item) return itemAmounts.getOrDefault(item, 0L);
        return fluidAmounts.getOrDefault((FluidKey) resource, 0L);
    }

    long totalItems() {
        return total(itemAmounts);
    }

    Map<FluidKey, Long> fluidAmountsSnapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(fluidAmounts));
    }

    Map<ItemStackWrapper, Long> itemSnapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(itemAmounts));
    }

    Map<String, Long> fluidSnapshot() {
        Map<String, Long> result = new LinkedHashMap<>();
        for (Map.Entry<FluidKey, Long> entry : fluidAmounts.entrySet()) {
            result.put(
                entry.getKey()
                    .fluid()
                    .getName(),
                entry.getValue());
        }
        return Collections.unmodifiableMap(result);
    }

    void loadItemSnapshot(Map<ItemStackWrapper, Long> snapshot) {
        if (snapshot == null) throw new IllegalArgumentException("Facility item snapshot must not be null");
        Map<ItemStackWrapper, Long> validated = new LinkedHashMap<>();
        for (Map.Entry<ItemStackWrapper, Long> entry : snapshot.entrySet()) {
            ItemStackWrapper key = entry.getKey();
            Long amount = entry.getValue();
            if (key == null) throw new IllegalArgumentException("Facility item snapshot contains a null item");
            if (amount == null || amount <= 0L) {
                throw new IllegalArgumentException(
                    "Facility item snapshot amount must be positive for " + key.toKey() + ": " + amount);
            }
            if (validated.put(key, amount) != null) {
                throw new IllegalArgumentException("Facility item snapshot contains duplicate item " + key.toKey());
            }
        }
        itemAmounts.clear();
        itemAmounts.putAll(validated);
    }

    void loadFluidSnapshot(Map<String, Long> snapshot) {
        if (snapshot == null) throw new IllegalArgumentException("Facility fluid snapshot must not be null");
        Map<FluidKey, Long> validated = new LinkedHashMap<>();
        for (Map.Entry<String, Long> entry : snapshot.entrySet()) {
            String name = entry.getKey();
            Long amount = entry.getValue();
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Facility fluid snapshot contains a null or blank fluid name");
            }
            if (amount == null || amount <= 0L) {
                throw new IllegalArgumentException(
                    "Facility fluid snapshot amount must be positive for " + name + ": " + amount);
            }
            FluidKey key = FluidKey.fromName(name);
            if (key == null)
                throw new IllegalArgumentException("Facility fluid snapshot contains unknown fluid " + name);
            if (validated.put(key, amount) != null) {
                throw new IllegalArgumentException("Facility fluid snapshot contains duplicate fluid " + name);
            }
        }
        fluidAmounts.clear();
        fluidAmounts.putAll(validated);
    }

    void clear() {
        itemAmounts.clear();
        fluidAmounts.clear();
        itemFilter.clear();
        fluidFilter.clear();
    }

    boolean addFilter(String key, boolean item) {
        if (key == null) return false;
        ResourceFilter<?> filter = item ? itemFilter : fluidFilter;
        if (filter.serialize()
            .contains(key)) {
            return false;
        }
        filter.add(key);
        return true;
    }

    boolean removeFilter(String key, boolean item) {
        if (key == null) return false;
        ResourceFilter<?> filter = item ? itemFilter : fluidFilter;
        if (!filter.serialize()
            .contains(key)) {
            return false;
        }
        filter.remove(key);
        return true;
    }

    Map<Boolean, List<String>> filtersSnapshot() {
        Map<Boolean, List<String>> result = new LinkedHashMap<>();
        List<String> itemSerialized = itemFilter.serialize();
        if (!itemSerialized.isEmpty()) result.put(true, itemSerialized);
        List<String> fluidSerialized = fluidFilter.serialize();
        if (!fluidSerialized.isEmpty()) result.put(false, fluidSerialized);
        return result;
    }

    boolean setFilters(List<String> filters, boolean item) {
        List<String> validated = validateFilters(filters, item);
        ResourceFilter<?> filter = item ? itemFilter : fluidFilter;
        if (filter.serialize()
            .equals(validated)) {
            return false;
        }
        filter.load(validated);
        return true;
    }

    private static List<String> validateFilters(List<String> filters, boolean item) {
        if (filters == null) throw new IllegalArgumentException("Facility filter list must not be null");
        Set<String> unique = new LinkedHashSet<>();
        for (String entry : filters) {
            if (entry == null || entry.isBlank()) {
                throw new IllegalArgumentException("Facility filter list contains a null or blank entry");
            }
            if (!item && FluidKey.fromName(entry) == null) {
                throw new IllegalArgumentException("Facility fluid filter contains unknown fluid " + entry);
            }
            if (!unique.add(entry)) {
                throw new IllegalArgumentException("Facility filter list contains duplicate entry " + entry);
            }
        }
        return List.copyOf(unique);
    }

    boolean clearFilters(boolean item) {
        ResourceFilter<?> filter = item ? itemFilter : fluidFilter;
        if (filter.isEmpty()) return false;
        filter.clear();
        return true;
    }

    private long insertItem(ItemStackWrapper item, long requested, long capacity) {
        long available = availableItemCapacity(capacity);
        long applied = Math.min(requested, available);
        if (applied == 0L) return 0L;
        itemAmounts.merge(item, applied, Math::addExact);
        return applied;
    }

    private long insertFluid(FluidKey fluid, long requested) {
        long stored = fluidAmounts.getOrDefault(fluid, 0L);
        if (stored >= Long.MAX_VALUE) return 0L;
        long applied = Math.min(requested, Long.MAX_VALUE - stored);
        if (applied == 0L) return 0L;
        fluidAmounts.put(fluid, stored + applied);
        return applied;
    }

    private long availableItemCapacity(long capacity) {
        long available = Math.max(0L, capacity);
        for (long stored : itemAmounts.values()) {
            if (stored >= available) return 0L;
            available -= stored;
        }
        return available;
    }

    boolean allowsInsertion(InventoryKey resource) {
        if (resource == null) return false;
        return resource instanceof ItemStackWrapper item ? itemFilter.test(item)
            : fluidFilter.test((FluidKey) resource);
    }

    private static long total(Map<?, Long> amounts) {
        long total = 0L;
        for (long amount : amounts.values()) {
            if (Long.MAX_VALUE - total < amount) return Long.MAX_VALUE;
            total += amount;
        }
        return total;
    }

    private boolean allowsOutputs(InventoryExchange exchange) {
        for (ItemStackWrapper item : exchange.itemOutputs()
            .keySet()) {
            if (!itemFilter.test(item)) return false;
        }
        for (FluidKey fluid : exchange.fluidOutputs()
            .keySet()) {
            if (!fluidFilter.test(fluid)) return false;
        }
        return true;
    }

    private static Set<InventoryKey> touchedResources(InventoryExchange exchange) {
        Set<InventoryKey> touched = new LinkedHashSet<>();
        touched.addAll(
            exchange.itemInputs()
                .keySet());
        touched.addAll(
            exchange.fluidInputs()
                .keySet());
        touched.addAll(
            exchange.itemOutputs()
                .keySet());
        touched.addAll(
            exchange.fluidOutputs()
                .keySet());
        return touched;
    }

    private boolean fitsItemCapacity(Map<InventoryKey, Long> finalAmounts, long capacity) {
        long remaining = Math.max(0L, capacity);
        Set<ItemStackWrapper> counted = new LinkedHashSet<>();
        for (Map.Entry<ItemStackWrapper, Long> entry : itemAmounts.entrySet()) {
            long amount = finalAmounts.getOrDefault(entry.getKey(), entry.getValue());
            if (amount > remaining) return false;
            remaining -= amount;
            counted.add(entry.getKey());
        }
        for (Map.Entry<InventoryKey, Long> entry : finalAmounts.entrySet()) {
            if (!(entry.getKey() instanceof ItemStackWrapper item) || counted.contains(item)) continue;
            if (entry.getValue() > remaining) return false;
            remaining -= entry.getValue();
        }
        return true;
    }

    private void setAmount(InventoryKey resource, long amount) {
        if (resource instanceof ItemStackWrapper item) {
            setAmount(itemAmounts, item, amount);
        } else {
            setAmount(fluidAmounts, (FluidKey) resource, amount);
        }
    }

    private static <K> void setAmount(Map<K, Long> amounts, K key, long amount) {
        if (amount == 0L) amounts.remove(key);
        else amounts.put(key, amount);
    }

    private static <K> long extract(Map<K, Long> amounts, K key, long requested) {
        long stored = amounts.getOrDefault(key, 0L);
        long applied = Math.min(stored, requested);
        if (applied == 0L) return 0L;
        long remaining = stored - applied;
        if (remaining == 0L) amounts.remove(key);
        else amounts.put(key, remaining);
        return applied;
    }

    private static void requireNonNegative(long requested, String operation) {
        if (requested < 0L) {
            throw new IllegalArgumentException("Facility inventory " + operation + " request must be non-negative");
        }
    }
}
