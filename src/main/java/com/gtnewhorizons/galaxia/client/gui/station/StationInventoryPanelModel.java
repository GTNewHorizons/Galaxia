package com.gtnewhorizons.galaxia.client.gui.station;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacilityInventory;
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

    static List<Map.Entry<ItemStackWrapper, Long>> inventoryRows(AutomatedFacilityInventory inventory) {
        Map<ItemStackWrapper, Long> rows = new LinkedHashMap<>(inventory.snapshot());
        for (ItemStackWrapper item : inventory.itemLowerBoundsSnapshot()
            .keySet()) {
            rows.putIfAbsent(item, inventory.getAmount(item));
        }
        for (ItemStackWrapper item : inventory.itemUpperBoundsSnapshot()
            .keySet()) {
            rows.putIfAbsent(item, inventory.getAmount(item));
        }
        rows.entrySet()
            .removeIf(
                row -> row.getValue() <= 0L && !inventory.hasItemLowerBound(row.getKey())
                    && !inventory.hasItemUpperBound(row.getKey()));
        List<Map.Entry<ItemStackWrapper, Long>> sorted = new ArrayList<>(rows.entrySet());
        sorted.sort(
            Comparator.comparing(
                row -> row.getKey()
                    .toStack(1)
                    .getDisplayName(),
                String.CASE_INSENSITIVE_ORDER));
        return sorted;
    }

    static List<FluidRow> fluidRows(AutomatedFacilityInventory inventory) {
        Map<String, Long> rows = new LinkedHashMap<>(inventory.fluidSnapshot());
        for (String fluidName : inventory.fluidLowerBoundsSnapshot()
            .keySet()) {
            rows.putIfAbsent(fluidName, inventory.getFluidAmount(fluidName));
        }
        for (String fluidName : inventory.fluidUpperBoundsSnapshot()
            .keySet()) {
            rows.putIfAbsent(fluidName, inventory.getFluidAmount(fluidName));
        }
        rows.entrySet()
            .removeIf(
                row -> row.getValue() <= 0L && !inventory.hasFluidLowerBound(row.getKey())
                    && !inventory.hasFluidUpperBound(row.getKey()));
        List<FluidRow> sorted = new ArrayList<>(rows.size());
        for (Map.Entry<String, Long> row : rows.entrySet()) {
            sorted.add(new FluidRow(row.getKey(), row.getValue()));
        }
        sorted.sort(Comparator.comparing(FluidRow::fluidName, String.CASE_INSENSITIVE_ORDER));
        return sorted;
    }

    record FluidRow(String fluidName, long amount) {}
}
