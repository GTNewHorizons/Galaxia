package com.gtnewhorizons.galaxia.registry.outpost.recipe;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;

public record SavedRecipeBounds(List<Entry> entries) {

    private static final SavedRecipeBounds EMPTY = new SavedRecipeBounds(List.of());

    public SavedRecipeBounds {
        if (entries == null || entries.isEmpty()) {
            entries = List.of();
        } else {
            List<Entry> sanitized = new ArrayList<>(entries.size());
            for (Entry entry : entries) {
                if (entry == null || entry.amount() < 0L) continue;
                withoutEntry(sanitized, entry.kind(), entry.slotIndex());
                sanitized.add(entry);
            }
            entries = sanitized.isEmpty() ? List.of() : List.copyOf(sanitized);
        }
    }

    public static SavedRecipeBounds empty() {
        return EMPTY;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public boolean hasBound(Kind kind, int slotIndex) {
        return entry(kind, slotIndex) != null;
    }

    public boolean hasBound(RecipeSnapshot recipe, Kind kind, int slotIndex) {
        return hasBound(kind, canonicalSlotIndex(recipe, kind, slotIndex));
    }

    public long boundOrDefault(Kind kind, int slotIndex) {
        Entry entry = entry(kind, slotIndex);
        if (entry != null) return entry.amount();
        return kind.defaultAmount();
    }

    public long boundOrDefault(RecipeSnapshot recipe, Kind kind, int slotIndex) {
        return boundOrDefault(kind, canonicalSlotIndex(recipe, kind, slotIndex));
    }

    public SavedRecipeBounds withBound(Kind kind, int slotIndex, long amount) {
        Entry next = new Entry(kind, slotIndex, amount);
        if (next.equals(entry(kind, slotIndex))) return this;
        List<Entry> updated = new ArrayList<>(entries.size() + 1);
        updated.addAll(entries);
        withoutEntry(updated, kind, checkedSlotIndex(slotIndex));
        updated.add(next);
        return new SavedRecipeBounds(updated);
    }

    public SavedRecipeBounds withBound(RecipeSnapshot recipe, Kind kind, int slotIndex, long amount) {
        return withBound(kind, canonicalSlotIndex(recipe, kind, slotIndex), amount);
    }

    public SavedRecipeBounds withoutBound(Kind kind, int slotIndex) {
        byte checkedSlotIndex = checkedSlotIndex(slotIndex);
        if (entry(kind, checkedSlotIndex) == null) return this;
        List<Entry> updated = new ArrayList<>(entries);
        withoutEntry(updated, kind, checkedSlotIndex);
        return new SavedRecipeBounds(updated);
    }

    public SavedRecipeBounds withoutBound(RecipeSnapshot recipe, Kind kind, int slotIndex) {
        return withoutBound(kind, canonicalSlotIndex(recipe, kind, slotIndex));
    }

    public SavedRecipeBounds canonicalized(RecipeSnapshot recipe) {
        if (recipe == null || entries.isEmpty()) return this;
        List<Entry> canonical = new ArrayList<>(entries.size());
        for (Entry entry : entries) {
            canonical.add(
                new Entry(entry.kind(), canonicalSlotIndex(recipe, entry.kind(), entry.slotIndex()), entry.amount()));
        }
        SavedRecipeBounds updated = new SavedRecipeBounds(canonical);
        return updated.equals(this) ? this : updated;
    }

    public static int canonicalSlotIndex(RecipeSnapshot recipe, Kind kind, int slotIndex) {
        checkedSlotIndex(slotIndex);
        if (recipe == null) return slotIndex;
        return switch (kind) {
            case INPUT_ITEM_LOWER -> canonicalItemSlot(recipe.inputs(), slotIndex);
            case OUTPUT_ITEM_UPPER -> canonicalItemSlot(recipe.outputs(), slotIndex);
            case INPUT_FLUID_LOWER -> canonicalFluidSlot(recipe.fluidInputs(), slotIndex);
            case OUTPUT_FLUID_UPPER -> canonicalFluidSlot(recipe.fluidOutputs(), slotIndex);
        };
    }

    private Entry entry(Kind kind, int slotIndex) {
        byte checkedSlotIndex = checkedSlotIndex(slotIndex);
        for (Entry entry : entries) {
            if (entry.kind() == kind && entry.slotIndex() == checkedSlotIndex) return entry;
        }
        return null;
    }

    private static int canonicalItemSlot(ItemStack[] stacks, int slotIndex) {
        if (stacks == null || slotIndex >= stacks.length) return slotIndex;
        ItemStackWrapper key = ItemStackWrapper.of(stacks[slotIndex]);
        if (key == null) return slotIndex;
        for (int i = 0; i < slotIndex; i++) {
            if (key.equals(ItemStackWrapper.of(stacks[i]))) return i;
        }
        return slotIndex;
    }

    private static int canonicalFluidSlot(FluidStack[] stacks, int slotIndex) {
        if (stacks == null || slotIndex >= stacks.length) return slotIndex;
        String key = fluidName(stacks[slotIndex]);
        if (key == null) return slotIndex;
        for (int i = 0; i < slotIndex; i++) {
            if (key.equals(fluidName(stacks[i]))) return i;
        }
        return slotIndex;
    }

    private static String fluidName(FluidStack stack) {
        if (stack == null) return null;
        try {
            Fluid fluid = stack.getFluid();
            return fluid != null ? fluid.getName() : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static void withoutEntry(List<Entry> entries, Kind kind, byte slotIndex) {
        entries.removeIf(entry -> entry.kind() == kind && entry.slotIndex() == slotIndex);
    }

    private static byte checkedSlotIndex(int slotIndex) {
        if (slotIndex < 0 || slotIndex > Byte.MAX_VALUE) {
            throw new IllegalArgumentException("slotIndex must fit in a positive byte: " + slotIndex);
        }
        return (byte) slotIndex;
    }

    public record Entry(Kind kind, byte slotIndex, long amount) {

        public Entry {
            if (kind == null) throw new NullPointerException("kind must not be null");
            if (slotIndex < 0) throw new IllegalArgumentException("slotIndex must be >= 0: " + slotIndex);
            if (amount < 0L) throw new IllegalArgumentException("amount must be >= 0: " + amount);
        }

        public Entry(Kind kind, int slotIndex, long amount) {
            this(kind, checkedSlotIndex(slotIndex), amount);
        }
    }

    public enum Kind {

        INPUT_ITEM_LOWER(0L),
        OUTPUT_ITEM_UPPER(Long.MAX_VALUE),
        INPUT_FLUID_LOWER(0L),
        OUTPUT_FLUID_UPPER(Long.MAX_VALUE);

        private final long defaultAmount;

        Kind(long defaultAmount) {
            this.defaultAmount = defaultAmount;
        }

        public long defaultAmount() {
            return defaultAmount;
        }
    }
}
