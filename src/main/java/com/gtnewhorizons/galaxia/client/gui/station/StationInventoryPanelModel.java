package com.gtnewhorizons.galaxia.client.gui.station;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.gtnewhorizons.galaxia.registry.interfaces.IDistributedInventory;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.FluidKey;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.upkeep.UpkeepAmount;

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
        Set<ItemStackWrapper> upkeepItems = Set.of();
        if (inventory instanceof AutomatedFacility facility) {
            upkeepItems = facility.upkeepSummary()
                .itemsPerMinute()
                .keySet();
            for (ItemStackWrapper item : upkeepItems) {
                rows.putIfAbsent(item, 0L);
            }
        }
        Set<ItemStackWrapper> visibleUpkeepItems = upkeepItems;
        rows.entrySet()
            .removeIf(row -> row.getValue() <= 0L && !visibleUpkeepItems.contains(row.getKey()));
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

    static UpkeepReserveStatus upkeepReserveStatus(AutomatedFacility facility, ItemStackWrapper item) {
        UpkeepAmount demand = facility.upkeepSummary()
            .itemsPerMinute()
            .get(item);
        long reserve = facility.upkeepReserve(item);
        if (demand == null || demand.isZero()) {
            return new UpkeepReserveStatus(reserve, 0.0D, UpkeepReserveLevel.NONE, "");
        }
        double minutes = reserve * (double) UpkeepAmount.MICRO_UNITS_PER_WHOLE / demand.microUnitsPerMinute();
        UpkeepReserveLevel level = minutes < 3.0D ? UpkeepReserveLevel.CRITICAL
            : minutes < 10.0D ? UpkeepReserveLevel.WARNING : UpkeepReserveLevel.NORMAL;
        String tooltip = String.format(Locale.ROOT, "Reserve covers %.1f min of upkeep.", minutes);
        return new UpkeepReserveStatus(reserve, minutes, level, tooltip);
    }

    enum UpkeepReserveLevel {
        NONE,
        NORMAL,
        WARNING,
        CRITICAL
    }

    record UpkeepReserveStatus(long reserve, double minutes, UpkeepReserveLevel level, String tooltip) {}

    record FluidRow(String fluidName, FluidKey fluidKey, long amount) {

        FluidRow withAmount(long amount) {
            return new FluidRow(fluidName, fluidKey, amount);
        }
    }
}
