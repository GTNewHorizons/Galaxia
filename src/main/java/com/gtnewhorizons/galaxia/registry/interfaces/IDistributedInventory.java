package com.gtnewhorizons.galaxia.registry.interfaces;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.gtnewhorizons.galaxia.registry.outpost.FluidKey;
import com.gtnewhorizons.galaxia.registry.outpost.InventoryKey;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.ResourceFilter;

public interface IDistributedInventory {

    default List<IDistributedInventory> getChildren() {
        return List.of();
    }

    default Map<ItemStackWrapper, Long> getItemAmounts() {
        return Map.of();
    }

    default Map<FluidKey, Long> getFluidAmounts() {
        return Map.of();
    }

    default ResourceFilter<ItemStackWrapper> getItemFilter() {
        return ResourceFilter.forItems();
    }

    default ResourceFilter<FluidKey> getFluidFilter() {
        return ResourceFilter.forFluids();
    }

    default int getPriority() {
        return 0;
    }

    default List<IDistributedInventory> getChildrenSortedByPriority() {
        List<IDistributedInventory> children = getChildren();
        if (children.isEmpty()) return children;
        return children.stream()
            .filter(Objects::nonNull)
            .sorted(
                Comparator.comparingInt(IDistributedInventory::getPriority)
                    .reversed())
            .collect(Collectors.toList());
    }

    default Map<ItemStackWrapper, Long> aggregatedItems() {
        Map<ItemStackWrapper, Long> result = new LinkedHashMap<>();
        for (IDistributedInventory child : getChildren()) {
            if (child == null) continue;
            child.aggregatedItems()
                .forEach((k, v) -> result.merge(k, v, Long::sum));
        }
        getItemAmounts().forEach((k, v) -> result.merge(k, v, Long::sum));
        return result;
    }

    default Map<FluidKey, Long> aggregatedFluids() {
        Map<FluidKey, Long> result = new LinkedHashMap<>();
        for (IDistributedInventory child : getChildren()) {
            if (child == null) continue;
            child.aggregatedFluids()
                .forEach((k, v) -> result.merge(k, v, Long::sum));
        }
        getFluidAmounts().forEach((k, v) -> result.merge(k, v, Long::sum));
        return result;
    }

    default long getItemAmount(ItemStackWrapper item) {
        long total = 0;
        for (IDistributedInventory child : getChildren()) {
            if (child != null) total += child.getItemAmount(item);
        }
        total += getItemAmounts().getOrDefault(item, 0L);
        return total;
    }

    default long getFluidAmount(FluidKey fluid) {
        long total = 0;
        for (IDistributedInventory child : getChildren()) {
            if (child != null) total += child.getFluidAmount(fluid);
        }
        total += getFluidAmounts().getOrDefault(fluid, 0L);
        return total;
    }

    default long totalItemSlots() {
        long total = 0;
        for (IDistributedInventory child : getChildren()) {
            if (child != null) total += child.totalItemSlots();
        }
        total += getItemAmounts().size();
        return total;
    }

    default long totalItemsStored() {
        long total = 0;
        for (IDistributedInventory child : getChildren()) {
            if (child != null) total += child.totalItemsStored();
        }
        for (long v : getItemAmounts().values()) {
            total += v;
        }
        return total;
    }

    default long totalFluidStored() {
        long total = 0;
        for (IDistributedInventory child : getChildren()) {
            if (child != null) total += child.totalFluidStored();
        }
        for (long v : getFluidAmounts().values()) {
            total += v;
        }
        return total;
    }

    default long totalItemCapacity() {
        long total = 0;
        for (IDistributedInventory child : getChildren()) {
            if (child != null) total += child.totalItemCapacity();
        }
        return total;
    }

    default long totalFluidCapacity() {
        long total = 0;
        for (IDistributedInventory child : getChildren()) {
            if (child != null) total += child.totalFluidCapacity();
        }
        return total;
    }

    default long getFreeItemSpace(ItemStackWrapper item) {
        if (!getItemFilter().test(item)) return 0L;
        long space = 0;
        for (IDistributedInventory child : getChildren()) {
            if (child != null) space += child.getFreeItemSpace(item);
        }
        return space;
    }

    default long getFreeFluidSpace(FluidKey fluid) {
        if (!getFluidFilter().test(fluid)) return 0L;
        long space = 0;
        for (IDistributedInventory child : getChildren()) {
            if (child != null) space += child.getFreeFluidSpace(fluid);
        }
        return space;
    }

    // =========================================================================
    // Mutations
    // =========================================================================

    default <T extends InventoryKey> long updateContents(T key, long delta) {
        return key.isItem() ? updateItems((ItemStackWrapper) key, delta) : updateFluids((FluidKey) key, delta);
    }

    default long updateItems(ItemStackWrapper item, long delta) {
        if (item == null || delta == 0L) return 0L;
        if (!getItemFilter().test(item)) return 0L;
        return delta > 0 ? insertItems(item, delta) : extractItems(item, extractionTarget(delta));
    }

    private long insertItems(ItemStackWrapper item, long target) {
        long transferred = 0;
        for (IDistributedInventory child : getChildrenSortedByPriority()) {
            if (transferred >= target) break;
            if (child == null || !child.getItemFilter()
                .test(item)) continue;
            transferred += child.updateItems(item, target - transferred);
        }
        if (transferred < target) {
            transferred += insertIntoOwnStorage(item, target - transferred);
        }
        return transferred;
    }

    private long extractItems(ItemStackWrapper item, long target) {
        long transferred = 0;
        List<IDistributedInventory> sorted = getChildrenSortedByPriority();
        for (int i = sorted.size() - 1; i >= 0 && transferred < target; i--) {
            IDistributedInventory child = sorted.get(i);
            if (child == null) continue;
            transferred += child.updateItems(item, -(target - transferred));
        }
        if (transferred < target) {
            transferred += extractFromOwnStorage(item, target - transferred);
        }
        return transferred;
    }

    default long insertIntoOwnStorage(ItemStackWrapper item, long target) {
        if (item == null || target <= 0) return 0;
        long remaining = Math.max(0, totalItemCapacity() - totalItemsStored());
        long toAdd = Math.min(target, remaining);
        if (toAdd <= 0) return 0;
        Map<ItemStackWrapper, Long> amounts = getItemAmounts();
        if (amounts == null) return 0;
        long current = amounts.getOrDefault(item, 0L);
        amounts.put(item, current + toAdd);
        return toAdd;
    }

    default long extractFromOwnStorage(ItemStackWrapper item, long target) {
        Map<ItemStackWrapper, Long> amounts = getItemAmounts();
        if (amounts == null) return 0;
        long current = amounts.getOrDefault(item, 0L);
        long extracted = Math.clamp(current, 0, target);
        if (extracted <= 0) return 0;
        long remaining = current - extracted;
        if (remaining <= 0) amounts.remove(item);
        else amounts.put(item, remaining);
        return extracted;
    }

    default long updateFluids(FluidKey fluid, long delta) {
        if (fluid == null || delta == 0) return 0L;
        if (!getFluidFilter().test(fluid)) return 0L;
        return delta > 0 ? insertFluids(fluid, delta) : extractFluids(fluid, extractionTarget(delta));
    }

    private static long extractionTarget(long delta) {
        return delta == Long.MIN_VALUE ? Long.MAX_VALUE : -delta;
    }

    private long insertFluids(FluidKey fluid, long target) {
        long transferred = 0;
        for (IDistributedInventory child : getChildrenSortedByPriority()) {
            if (transferred >= target) break;
            if (child == null || !child.getFluidFilter()
                .test(fluid)) continue;
            transferred += child.updateFluids(fluid, target - transferred);
        }
        if (transferred < target) {
            transferred += insertIntoOwnFluidStorage(fluid, target - transferred);
        }
        return transferred;
    }

    private long extractFluids(FluidKey fluid, long target) {
        long transferred = 0;
        List<IDistributedInventory> sorted = getChildrenSortedByPriority();
        for (int i = sorted.size() - 1; i >= 0 && transferred < target; i--) {
            IDistributedInventory child = sorted.get(i);
            if (child == null) continue;
            transferred += child.updateFluids(fluid, -(target - transferred));
        }
        if (transferred < target) {
            transferred += extractFromOwnFluidStorage(fluid, target - transferred);
        }
        return transferred;
    }

    default long insertIntoOwnFluidStorage(FluidKey fluid, long target) {
        if (fluid == null || target <= 0) return 0;
        long remaining = Math.max(0, totalFluidCapacity() - totalFluidStored());
        long toAdd = Math.min(target, remaining);
        if (toAdd <= 0) return 0;
        Map<FluidKey, Long> amounts = getFluidAmounts();
        if (amounts == null) return 0;
        long current = amounts.getOrDefault(fluid, 0L);
        amounts.put(fluid, current + toAdd);
        return toAdd;
    }

    default long extractFromOwnFluidStorage(FluidKey fluid, long target) {
        Map<FluidKey, Long> amounts = getFluidAmounts();
        if (amounts == null) return 0;
        long current = amounts.getOrDefault(fluid, 0L);
        long extracted = Math.clamp(current, 0, target);
        if (extracted <= 0) return 0;
        long remaining = current - extracted;
        if (remaining <= 0) amounts.remove(fluid);
        else amounts.put(fluid, remaining);
        return extracted;
    }

    default void markDirty() {
        for (IDistributedInventory child : getChildren()) {
            if (child != null) child.markDirty();
        }
    }

    default Map<ItemStackWrapper, Long> filterItems(ResourceFilter<ItemStackWrapper> predicate) {
        return aggregatedItems().entrySet()
            .stream()
            .filter(e -> predicate.test(e.getKey()))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    default Map<FluidKey, Long> filterFluids(ResourceFilter<FluidKey> predicate) {
        return aggregatedFluids().entrySet()
            .stream()
            .filter(e -> predicate.test(e.getKey()))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    default Map<ItemStackWrapper, Long> getItemsBelowThreshold(Map<ItemStackWrapper, Long> thresholds) {
        Map<ItemStackWrapper, Long> snapshot = aggregatedItems();
        return thresholds.entrySet()
            .stream()
            .filter(e -> snapshot.getOrDefault(e.getKey(), 0L) < e.getValue())
            .collect(Collectors.toMap(Map.Entry::getKey, e -> snapshot.getOrDefault(e.getKey(), 0L)));
    }

    default double fluidFillFactor() {
        long capacity = totalFluidCapacity();
        return capacity == 0L ? 0.0 : (double) totalFluidStored() / capacity;
    }

    default double itemFillFactor() {
        long capacity = totalItemCapacity();
        return capacity == 0L ? 0.0 : (double) totalItemsStored() / capacity;
    }
}
