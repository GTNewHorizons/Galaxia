package com.gtnewhorizons.galaxia.registry.outpost;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

public final class ResourceFilter<T> implements Predicate<T> {

    private final List<String> serialized = new ArrayList<>();

    private final Function<T, String> encoder;

    private ResourceFilter(Function<T, String> encoder) {
        this.encoder = encoder;
    }

    /** Exact match against the encoded form of a typed value. */
    public boolean add(T value) {
        return add(encoder.apply(value));
    }

    /** Exact match against a raw string. */
    public boolean add(String value) {
        if (serialized.contains(value)) return false;
        return serialized.add(value);
    }

    public boolean remove(T value) {
        return remove(encoder.apply(value));
    }

    /** Removes the first entry whose serialized form equals {@code value}. */
    public boolean remove(String value) {
        return serialized.remove(value);
    }

    /**
     * Replaces the current state with the entries produced by a previous
     * {@link #serialize()} call.
     */
    public void load(List<String> entries) {
        clear();
        serialized.addAll(entries);
    }

    public List<String> serialize() {
        return List.copyOf(serialized);
    }

    @Override
    public boolean test(T value) {
        if (isEmpty()) {
            return true;
        }
        return serialized.contains(Objects.requireNonNull(encoder.apply(value)));
    }

    public void clear() {
        serialized.clear();
    }

    /** Returns {@code true} when no filters have been added. */
    public boolean isEmpty() {
        return serialized.isEmpty();
    }

    public static ResourceFilter<ItemStackWrapper> forItems() {
        return new ResourceFilter<>(
            item -> item.toItemStack()
                .getUnlocalizedName());
    }

    public static ResourceFilter<FluidKey> forFluids() {
        return new ResourceFilter<>(
            fluid -> fluid.fluid()
                .getName());
    }
}
