package com.gtnewhorizons.galaxia.client.gui.station;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.gtnewhorizons.galaxia.registry.interfaces.IBoundedInventory;
import com.gtnewhorizons.galaxia.registry.interfaces.IDistributedInventoryOLD;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;

final class StationInventoryPanelModel {

    private StationInventoryPanelModel() {}

    static long voidAmount(boolean amountMode, long availableAmount, String amountText) {
        if (availableAmount <= 0L) return 0L;
        if (!amountMode) return availableAmount;
        if (amountText == null || amountText.isBlank()) return 0L;
        long parsed;
        try {
            parsed = Long.parseLong(amountText);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
        if (parsed <= 0L) return 0L;
        return Math.min(parsed, availableAmount);
    }

    static List<Map.Entry<ItemStackWrapper, Long>> inventoryRows(IDistributedInventoryOLD inventory,
                                                                 @Nullable IBoundedInventory bounds) {
        Map<ItemStackWrapper, Long> rows = new LinkedHashMap<>(inventory.aggregatedItemAmounts());
        if (bounds != null) {
            for (ItemStackWrapper item : bounds.itemLowerBoundsSnapshot()
                .keySet()) {
                rows.putIfAbsent(
                    item,
                    inventory.aggregatedItemAmounts()
                        .getOrDefault(item, 0L));
            }
            for (ItemStackWrapper item : bounds.itemUpperBoundsSnapshot()
                .keySet()) {
                rows.putIfAbsent(
                    item,
                    inventory.aggregatedItemAmounts()
                        .getOrDefault(item, 0L));
            }
            rows.entrySet()
                .removeIf(
                    row -> row.getValue() <= 0L && !bounds.hasItemLowerBound(row.getKey())
                        && !bounds.hasItemUpperBound(row.getKey()));
        } else {
            rows.entrySet()
                .removeIf(row -> row.getValue() <= 0L);
        }
        List<Map.Entry<ItemStackWrapper, Long>> sorted = new ArrayList<>(rows.entrySet());
        sorted.sort(
            Comparator.comparing(
                row -> row.getKey()
                    .toStack(1)
                    .getDisplayName(),
                String.CASE_INSENSITIVE_ORDER));
        return sorted;
    }

    static List<FluidRow> fluidRows(@Nullable IFluidInventory fluidInv) {
        if (fluidInv == null) return List.of();
        Map<String, Long> rows = new LinkedHashMap<>(fluidInv.fluidSnapshot());
        for (String fluidName : fluidInv.fluidLowerBoundsSnapshot()
            .keySet()) {
            rows.putIfAbsent(fluidName, fluidInv.getFluidAmount(fluidName));
        }
        for (String fluidName : fluidInv.fluidUpperBoundsSnapshot()
            .keySet()) {
            rows.putIfAbsent(fluidName, fluidInv.getFluidAmount(fluidName));
        }
        rows.entrySet()
            .removeIf(
                row -> row.getValue() <= 0L && !fluidInv.hasFluidLowerBound(row.getKey())
                    && !fluidInv.hasFluidUpperBound(row.getKey()));
        List<FluidRow> sorted = new ArrayList<>(rows.size());
        for (Map.Entry<String, Long> row : rows.entrySet()) {
            sorted.add(new FluidRow(row.getKey(), row.getValue()));
        }
        sorted.sort(Comparator.comparing(FluidRow::fluidName, String.CASE_INSENSITIVE_ORDER));
        return sorted;
    }

    record FluidRow(String fluidName, long amount) {}
}
