package com.gtnewhorizons.galaxia.registry.outpost.recipe;

import java.util.HashMap;
import java.util.Map;

import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;

public record RecipeSlotBounds(Map<ItemStackWrapper, Long> inputItemLowerBounds,
    Map<ItemStackWrapper, Long> outputItemUpperBounds, Map<String, Long> inputFluidLowerBounds,
    Map<String, Long> outputFluidUpperBounds) {

    private static final RecipeSlotBounds EMPTY = new RecipeSlotBounds(Map.of(), Map.of(), Map.of(), Map.of());

    public RecipeSlotBounds {
        inputItemLowerBounds = sanitizeItemBounds(inputItemLowerBounds);
        outputItemUpperBounds = sanitizeItemBounds(outputItemUpperBounds);
        inputFluidLowerBounds = sanitizeFluidBounds(inputFluidLowerBounds);
        outputFluidUpperBounds = sanitizeFluidBounds(outputFluidUpperBounds);
    }

    public static RecipeSlotBounds empty() {
        return EMPTY;
    }

    public RecipeSlotBounds withInputItemLowerBound(ItemStackWrapper item, long amount) {
        return withItemBound(inputItemLowerBounds, item, amount, BoundKind.INPUT_ITEM_LOWER);
    }

    public RecipeSlotBounds withoutInputItemLowerBound(ItemStackWrapper item) {
        return withoutItemBound(inputItemLowerBounds, item, BoundKind.INPUT_ITEM_LOWER);
    }

    public RecipeSlotBounds withOutputItemUpperBound(ItemStackWrapper item, long amount) {
        return withItemBound(outputItemUpperBounds, item, amount, BoundKind.OUTPUT_ITEM_UPPER);
    }

    public RecipeSlotBounds withoutOutputItemUpperBound(ItemStackWrapper item) {
        return withoutItemBound(outputItemUpperBounds, item, BoundKind.OUTPUT_ITEM_UPPER);
    }

    public RecipeSlotBounds withInputFluidLowerBound(String fluidName, long amount) {
        return withFluidBound(inputFluidLowerBounds, fluidName, amount, BoundKind.INPUT_FLUID_LOWER);
    }

    public RecipeSlotBounds withoutInputFluidLowerBound(String fluidName) {
        return withoutFluidBound(inputFluidLowerBounds, fluidName, BoundKind.INPUT_FLUID_LOWER);
    }

    public RecipeSlotBounds withOutputFluidUpperBound(String fluidName, long amount) {
        return withFluidBound(outputFluidUpperBounds, fluidName, amount, BoundKind.OUTPUT_FLUID_UPPER);
    }

    public RecipeSlotBounds withoutOutputFluidUpperBound(String fluidName) {
        return withoutFluidBound(outputFluidUpperBounds, fluidName, BoundKind.OUTPUT_FLUID_UPPER);
    }

    public long inputItemLowerBound(ItemStackWrapper item) {
        return inputItemLowerBounds.getOrDefault(item, 0L);
    }

    public long outputItemUpperBound(ItemStackWrapper item) {
        return outputItemUpperBounds.getOrDefault(item, Long.MAX_VALUE);
    }

    public long inputFluidLowerBound(String fluidName) {
        return inputFluidLowerBounds.getOrDefault(fluidName, 0L);
    }

    public long outputFluidUpperBound(String fluidName) {
        return outputFluidUpperBounds.getOrDefault(fluidName, Long.MAX_VALUE);
    }

    public boolean hasInputItemLowerBound(ItemStackWrapper item) {
        return inputItemLowerBounds.containsKey(item);
    }

    public boolean hasOutputItemUpperBound(ItemStackWrapper item) {
        return outputItemUpperBounds.containsKey(item);
    }

    public boolean hasInputFluidLowerBound(String fluidName) {
        return inputFluidLowerBounds.containsKey(fluidName);
    }

    public boolean hasOutputFluidUpperBound(String fluidName) {
        return outputFluidUpperBounds.containsKey(fluidName);
    }

    public boolean isEmpty() {
        return inputItemLowerBounds.isEmpty() && outputItemUpperBounds.isEmpty()
            && inputFluidLowerBounds.isEmpty()
            && outputFluidUpperBounds.isEmpty();
    }

    private RecipeSlotBounds withItemBound(Map<ItemStackWrapper, Long> source, ItemStackWrapper item, long amount,
        BoundKind kind) {
        if (item == null) throw new NullPointerException("item bound key must not be null");
        if (amount < 0L) throw new IllegalArgumentException("bound amount must be >= 0: " + amount);
        Long current = source.get(item);
        if (current != null && current == amount) return this;
        Map<ItemStackWrapper, Long> updated = new HashMap<>(source);
        updated.put(item, amount);
        return switch (kind) {
            case INPUT_ITEM_LOWER -> new RecipeSlotBounds(
                updated,
                outputItemUpperBounds,
                inputFluidLowerBounds,
                outputFluidUpperBounds);
            case OUTPUT_ITEM_UPPER -> new RecipeSlotBounds(
                inputItemLowerBounds,
                updated,
                inputFluidLowerBounds,
                outputFluidUpperBounds);
            default -> throw new IllegalArgumentException("invalid item bound kind: " + kind);
        };
    }

    private RecipeSlotBounds withFluidBound(Map<String, Long> source, String fluidName, long amount, BoundKind kind) {
        if (fluidName == null || fluidName.isBlank()) {
            throw new IllegalArgumentException("fluid bound key must not be null/blank");
        }
        if (amount < 0L) throw new IllegalArgumentException("bound amount must be >= 0: " + amount);
        Long current = source.get(fluidName);
        if (current != null && current == amount) return this;
        Map<String, Long> updated = new HashMap<>(source);
        updated.put(fluidName, amount);
        return switch (kind) {
            case INPUT_FLUID_LOWER -> new RecipeSlotBounds(
                inputItemLowerBounds,
                outputItemUpperBounds,
                updated,
                outputFluidUpperBounds);
            case OUTPUT_FLUID_UPPER -> new RecipeSlotBounds(
                inputItemLowerBounds,
                outputItemUpperBounds,
                inputFluidLowerBounds,
                updated);
            default -> throw new IllegalArgumentException("invalid fluid bound kind: " + kind);
        };
    }

    private RecipeSlotBounds withoutItemBound(Map<ItemStackWrapper, Long> source, ItemStackWrapper item,
        BoundKind kind) {
        if (item == null) return this;
        if (!source.containsKey(item)) return this;
        Map<ItemStackWrapper, Long> updated = new HashMap<>(source);
        updated.remove(item);
        return switch (kind) {
            case INPUT_ITEM_LOWER -> new RecipeSlotBounds(
                updated,
                outputItemUpperBounds,
                inputFluidLowerBounds,
                outputFluidUpperBounds);
            case OUTPUT_ITEM_UPPER -> new RecipeSlotBounds(
                inputItemLowerBounds,
                updated,
                inputFluidLowerBounds,
                outputFluidUpperBounds);
            default -> throw new IllegalArgumentException("invalid item bound kind: " + kind);
        };
    }

    private RecipeSlotBounds withoutFluidBound(Map<String, Long> source, String fluidName, BoundKind kind) {
        if (fluidName == null || fluidName.isBlank() || !source.containsKey(fluidName)) return this;
        Map<String, Long> updated = new HashMap<>(source);
        updated.remove(fluidName);
        return switch (kind) {
            case INPUT_FLUID_LOWER -> new RecipeSlotBounds(
                inputItemLowerBounds,
                outputItemUpperBounds,
                updated,
                outputFluidUpperBounds);
            case OUTPUT_FLUID_UPPER -> new RecipeSlotBounds(
                inputItemLowerBounds,
                outputItemUpperBounds,
                inputFluidLowerBounds,
                updated);
            default -> throw new IllegalArgumentException("invalid fluid bound kind: " + kind);
        };
    }

    private static Map<ItemStackWrapper, Long> sanitizeItemBounds(Map<ItemStackWrapper, Long> source) {
        if (source == null || source.isEmpty()) return Map.of();
        Map<ItemStackWrapper, Long> sanitized = new HashMap<>();
        for (Map.Entry<ItemStackWrapper, Long> entry : source.entrySet()) {
            if (entry.getKey() == null) throw new NullPointerException("item bound key must not be null");
            long amount = entry.getValue() == null ? 0L : entry.getValue();
            if (amount < 0L) throw new IllegalArgumentException("bound amount must be >= 0: " + amount);
            sanitized.put(entry.getKey(), amount);
        }
        return Map.copyOf(sanitized);
    }

    private static Map<String, Long> sanitizeFluidBounds(Map<String, Long> source) {
        if (source == null || source.isEmpty()) return Map.of();
        Map<String, Long> sanitized = new HashMap<>();
        for (Map.Entry<String, Long> entry : source.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.isBlank())
                throw new IllegalArgumentException("fluid bound key must not be null/blank");
            long amount = entry.getValue() == null ? 0L : entry.getValue();
            if (amount < 0L) throw new IllegalArgumentException("bound amount must be >= 0: " + amount);
            sanitized.put(key, amount);
        }
        return Map.copyOf(sanitized);
    }

    private enum BoundKind {
        INPUT_ITEM_LOWER,
        OUTPUT_ITEM_UPPER,
        INPUT_FLUID_LOWER,
        OUTPUT_FLUID_UPPER
    }
}
