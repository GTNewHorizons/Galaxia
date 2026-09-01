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

    private final Map<InventoryKey, Long> amounts = new LinkedHashMap<>();
    private final Map<InventoryKey, InventoryBounds> bounds = new LinkedHashMap<>();
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
        return extract(amounts, resource, requested);
    }

    ExchangeResult tryExchange(InventoryExchange exchange, long itemCapacity) {
        if (exchange == null || exchange.inputs()
            .isEmpty()
            && exchange.outputs()
                .isEmpty())
            return ExchangeResult.REJECTED;
        if (!allowsOutputs(exchange)) return ExchangeResult.REJECTED;

        Set<InventoryKey> touched = touchedResources(exchange);
        Map<InventoryKey, Long> finalAmounts = new LinkedHashMap<>();
        boolean changed = false;
        for (InventoryKey resource : touched) {
            long stored = amount(resource);
            long input = exchange.inputs()
                .getOrDefault(resource, 0L);
            if (stored < input) return ExchangeResult.REJECTED;
            try {
                long finalAmount = Math.addExact(
                    stored - input,
                    exchange.outputs()
                        .getOrDefault(resource, 0L));
                finalAmounts.put(resource, finalAmount);
                changed |= finalAmount != stored;
            } catch (ArithmeticException ignored) {
                return ExchangeResult.REJECTED;
            }
        }
        if (exchange.outputs()
            .keySet()
            .stream()
            .anyMatch(InventoryKey::isItem) && !fitsItemCapacity(finalAmounts, itemCapacity)) {
            return ExchangeResult.REJECTED;
        }
        if (!changed) return ExchangeResult.UNCHANGED;

        for (Map.Entry<InventoryKey, Long> entry : finalAmounts.entrySet()) {
            setAmount(amounts, entry.getKey(), entry.getValue());
        }
        return ExchangeResult.CHANGED;
    }

    ReturnItemsResult returnItems(Map<ItemStackWrapper, Long> requested, long itemCapacity) {
        if (requested == null) throw new IllegalArgumentException("Returned items must not be null");
        for (Map.Entry<ItemStackWrapper, Long> entry : requested.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || entry.getValue() <= 0L) {
                throw new IllegalArgumentException("Returned items must contain positive amounts and non-null keys");
            }
        }

        long available = availableItemCapacity(itemCapacity);
        Map<ItemStackWrapper, Long> accepted = new LinkedHashMap<>();
        Map<ItemStackWrapper, Long> remaining = new LinkedHashMap<>();
        for (Map.Entry<ItemStackWrapper, Long> entry : requested.entrySet()) {
            long applied = Math.min(entry.getValue(), available);
            if (applied > 0L) {
                long stored = amounts.getOrDefault(entry.getKey(), 0L);
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
            amounts.merge(entry.getKey(), entry.getValue(), Math::addExact);
        }
        return new ReturnItemsResult(Collections.unmodifiableMap(remaining), !accepted.isEmpty());
    }

    long amount(InventoryKey resource) {
        return amounts.getOrDefault(resource, 0L);
    }

    InventoryBounds bound(InventoryKey resource) {
        if (resource == null) return InventoryBounds.invalid();
        return bounds.getOrDefault(resource, InventoryBounds.invalid());
    }

    void setBound(InventoryKey resource, InventoryBounds bound) {
        if (resource == null || bound == null) return;
        if (bound.isInvalid()) bounds.remove(resource);
        else bounds.put(resource, bound);
    }

    boolean clearBound(InventoryKey resource) {
        return resource != null && bounds.remove(resource) != null;
    }

    Map<InventoryKey, InventoryBounds> boundsSnapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(bounds));
    }

    void restoreBounds(Map<? extends InventoryKey, InventoryBounds> snapshot) {
        if (snapshot == null) throw new IllegalArgumentException("Facility inventory bounds must not be null");
        Map<InventoryKey, InventoryBounds> validated = new LinkedHashMap<>();
        for (Map.Entry<? extends InventoryKey, InventoryBounds> entry : snapshot.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null
                || entry.getValue()
                    .isInvalid()) {
                throw new IllegalArgumentException("Facility inventory bounds must contain valid keys and bounds");
            }
            validated.put(entry.getKey(), entry.getValue());
        }
        bounds.clear();
        bounds.putAll(validated);
    }

    long totalItems() {
        long total = 0L;
        for (Map.Entry<InventoryKey, Long> entry : amounts.entrySet()) {
            if (!entry.getKey()
                .isItem()) continue;
            if (Long.MAX_VALUE - total < entry.getValue()) return Long.MAX_VALUE;
            total += entry.getValue();
        }
        return total;
    }

    Map<FluidKey, Long> fluidAmountsSnapshot() {
        Map<FluidKey, Long> result = new LinkedHashMap<>();
        amounts.forEach((key, amount) -> { if (key instanceof FluidKey fluid) result.put(fluid, amount); });
        return Collections.unmodifiableMap(result);
    }

    Map<InventoryKey, Long> amountsSnapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(amounts));
    }

    void restoreSnapshot(Map<? extends InventoryKey, Long> snapshot) {
        if (snapshot == null) throw new IllegalArgumentException("Facility inventory snapshot must not be null");
        Map<InventoryKey, Long> validated = new LinkedHashMap<>();
        for (Map.Entry<? extends InventoryKey, Long> entry : snapshot.entrySet()) {
            InventoryKey key = entry.getKey();
            Long amount = entry.getValue();
            if (key == null || key instanceof FluidKey fluid && fluid.fluid() == null
                || amount == null
                || amount <= 0L) {
                throw new IllegalArgumentException("Facility inventory snapshot must contain valid positive amounts");
            }
            validated.put(key, amount);
        }
        amounts.clear();
        amounts.putAll(validated);
    }

    Map<ItemStackWrapper, Long> itemSnapshot() {
        Map<ItemStackWrapper, Long> result = new LinkedHashMap<>();
        amounts.forEach((key, amount) -> { if (key instanceof ItemStackWrapper item) result.put(item, amount); });
        return Collections.unmodifiableMap(result);
    }

    void clear() {
        amounts.clear();
        bounds.clear();
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
        amounts.merge(item, applied, Math::addExact);
        return applied;
    }

    private long insertFluid(FluidKey fluid, long requested) {
        long stored = amounts.getOrDefault(fluid, 0L);
        if (stored >= Long.MAX_VALUE) return 0L;
        long applied = Math.min(requested, Long.MAX_VALUE - stored);
        if (applied == 0L) return 0L;
        amounts.put(fluid, stored + applied);
        return applied;
    }

    private long availableItemCapacity(long capacity) {
        long available = Math.max(0L, capacity);
        for (Map.Entry<InventoryKey, Long> entry : amounts.entrySet()) {
            if (!entry.getKey()
                .isItem()) continue;
            long stored = entry.getValue();
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

    private boolean allowsOutputs(InventoryExchange exchange) {
        for (InventoryKey resource : exchange.outputs()
            .keySet()) {
            if (!allowsInsertion(resource)) return false;
        }
        return true;
    }

    private static Set<InventoryKey> touchedResources(InventoryExchange exchange) {
        Set<InventoryKey> touched = new LinkedHashSet<>(
            exchange.inputs()
                .keySet());
        touched.addAll(
            exchange.outputs()
                .keySet());
        return touched;
    }

    private boolean fitsItemCapacity(Map<InventoryKey, Long> finalAmounts, long capacity) {
        Map<InventoryKey, Long> projected = new LinkedHashMap<>(amounts);
        projected.putAll(finalAmounts);
        long remaining = Math.max(0L, capacity);
        for (Map.Entry<InventoryKey, Long> entry : projected.entrySet()) {
            if (!entry.getKey()
                .isItem()) continue;
            if (entry.getValue() > remaining) return false;
            remaining -= entry.getValue();
        }
        return true;
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
