package com.gtnewhorizons.galaxia.client.gui.station;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.FluidKey;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.upkeep.UpkeepAmount;
import com.gtnewhorizons.galaxia.registry.outpost.upkeep.UpkeepLedger;

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

    static boolean boundsInputValid(String lowerText, boolean hasExistingLower, long existingLower, String upperText,
        boolean hasExistingUpper, long existingUpper) {
        BoundInput lower = resolveBoundInput(lowerText, hasExistingLower, existingLower);
        BoundInput upper = resolveBoundInput(upperText, hasExistingUpper, existingUpper);
        if (!lower.valid() || !upper.valid()) return false;
        return !lower.present() || !upper.present() || lower.amount() <= upper.amount();
    }

    private static BoundInput resolveBoundInput(String text, boolean hasExisting, long existing) {
        if (text == null || text.isBlank()) {
            return hasExisting ? new BoundInput(true, existing, true) : new BoundInput(false, 0L, true);
        }
        try {
            return new BoundInput(true, Long.parseLong(text), true);
        } catch (NumberFormatException ignored) {
            return new BoundInput(false, 0L, false);
        }
    }

    static List<InventoryItemRow> inventoryRows(Map<ItemStackWrapper, Long> amounts, AutomatedFacility facility) {
        Set<ItemStackWrapper> upkeepItems = facility == null ? Set.of()
            : facility.upkeepSummary()
                .itemsPerMinute()
                .keySet();
        List<InventoryItemRow> sorted = new ArrayList<>(amounts.size() + upkeepItems.size());
        for (Map.Entry<ItemStackWrapper, Long> entry : amounts.entrySet()) {
            if (entry.getValue() <= 0L && !upkeepItems.contains(entry.getKey())) continue;
            sorted.add(
                new InventoryItemRow(
                    entry.getKey(),
                    entry.getValue(),
                    facility == null ? 0L : facility.upkeepReserve(entry.getKey())));
        }
        if (facility != null) {
            for (ItemStackWrapper item : upkeepItems) {
                if (!amounts.containsKey(item)) {
                    sorted.add(new InventoryItemRow(item, 0L, facility.upkeepReserve(item)));
                }
            }
        }
        sorted.sort(
            Comparator.comparing(
                row -> row.item()
                    .toStack(1)
                    .getDisplayName(),
                String.CASE_INSENSITIVE_ORDER));
        return sorted;
    }

    static List<FluidRow> fluidRows(Map<FluidKey, Long> amounts) {
        List<FluidRow> result = new ArrayList<>();
        for (Map.Entry<FluidKey, Long> e : amounts.entrySet()) {
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

    static List<UpkeepItemRow> upkeepItemRows(AutomatedFacility facility) {
        List<UpkeepItemRow> result = new ArrayList<>();
        UpkeepLedger.UpkeepSummary summary = facility.upkeepSummary();
        for (Map.Entry<ItemStackWrapper, UpkeepAmount> entry : summary.itemsPerMinute()
            .entrySet()) {
            ItemStackWrapper item = entry.getKey();
            result.add(
                new UpkeepItemRow(
                    item,
                    entry.getValue(),
                    facility.itemAmount(item),
                    facility.upkeepReserve(item),
                    facility.isUpkeepAutoOrderEnabled(item),
                    upkeepReserveStatus(facility, summary, item)));
        }
        result.sort(
            Comparator.comparing(
                row -> row.item()
                    .toStack(1)
                    .getDisplayName(),
                String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    static UpkeepReserveStatus upkeepReserveStatus(AutomatedFacility facility, ItemStackWrapper item) {
        return upkeepReserveStatus(facility, facility.upkeepSummary(), item);
    }

    private static UpkeepReserveStatus upkeepReserveStatus(AutomatedFacility facility,
        UpkeepLedger.UpkeepSummary summary, ItemStackWrapper item) {
        UpkeepAmount demand = summary.itemsPerMinute()
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

    private record BoundInput(boolean present, long amount, boolean valid) {}

    record InventoryItemRow(ItemStackWrapper item, long amount, long upkeepReserve) {}

    record UpkeepItemRow(ItemStackWrapper item, UpkeepAmount perMinute, long stock, long reserve, boolean autoOrder,
        UpkeepReserveStatus status) {}

    record FluidRow(String fluidName, FluidKey fluidKey, long amount) {

        FluidRow withAmount(long amount) {
            return new FluidRow(fluidName, fluidKey, amount);
        }
    }
}
