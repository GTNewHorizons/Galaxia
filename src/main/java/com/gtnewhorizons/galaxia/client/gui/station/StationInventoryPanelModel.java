package com.gtnewhorizons.galaxia.client.gui.station;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.gtnewhorizons.galaxia.registry.interfaces.IDistributedInventory;
import com.gtnewhorizons.galaxia.registry.outpost.FluidKey;
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

    static List<Map.Entry<ItemStackWrapper, Long>> inventoryRows(IDistributedInventory inventory) {
        Map<ItemStackWrapper, Long> rows = new LinkedHashMap<>(inventory.aggregatedItems());
        rows.entrySet()
            .removeIf(row -> row.getValue() <= 0L);
        List<Map.Entry<ItemStackWrapper, Long>> sorted = new ArrayList<>(rows.entrySet());
        sorted.sort(
            Comparator.comparing(
                row -> row.getKey()
                    .toStack(1)
                    .getDisplayName(),
                String.CASE_INSENSITIVE_ORDER));
        return sorted;
    }

    static List<FluidRow> fluidRows(IDistributedInventory distributed) {
        List<FluidRow> result = new ArrayList<>();
        for (Map.Entry<FluidKey, Long> e : distributed.aggregatedFluids()
            .entrySet()) {
            if (e.getValue() > 0L) {
                result.add(
                    new FluidRow(
                        e.getKey()
                            .fluid()
                            .getName(),
                        e.getKey(),
                        e.getValue()));
            }
        }
        result.sort(Comparator.comparing(FluidRow::fluidName, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    record FluidRow(String fluidName, FluidKey fluidKey, long amount) {

        FluidRow withAmount(long amount) {
            return new FluidRow(fluidName, fluidKey, amount);
        }
    }
}
