package com.gtnewhorizons.galaxia.registry.outpost;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

public final class ResourceFilter<T> implements Predicate<T> {

    private final List<Predicate<String>> stringPredicates = new ArrayList<>();
    private final List<String> serialized = new ArrayList<>();

    private final Function<T, String> encoder;

    private ResourceFilter(Function<T, String> encoder) {
        this.encoder = encoder;
    }

    /** Exact match against the encoded form of a typed value. */
    public void add(T value) {
        add(encoder.apply(value));
    }

    /** Exact match against a raw string. */
    public void add(String value) {
        if (serialized.contains(value)) return;
        stringPredicates.add(s -> s.equals(value));
        serialized.add(value);
    }

    public void remove(T value) {
        remove(encoder.apply(value));
    }

    /**
     * Removes the first entry whose serialized form equals {@code value}.
     * Both parallel lists are updated atomically so they stay in sync.
     */
    public void remove(String value) {
        int index = serialized.indexOf(value);
        if (index >= 0) {
            serialized.remove(index);
            stringPredicates.remove(index); // Bug fix: was missing entirely
        }
    }

    /**
     * Replaces the current state with the entries produced by a previous
     * {@link #serialize()} call, reconstructing all predicates from their
     * serialized prefix.
     */
    public void load(List<String> entries) {
        clear();
        for (String entry : entries) {
            stringPredicates.add(s -> s.equals(entry));
            serialized.add(entry);
        }
    }

    public List<String> serialize() {
        return List.copyOf(serialized);
    }

    @Override
    public boolean test(T value) {
        if (isEmpty()) {
            return true;
        }
        String text = encoder.apply(value);
        for (Predicate<String> predicate : stringPredicates) {
            if (predicate.test(text)) {
                return true;
            }
        }
        return false;
    }

    public void clear() {
        stringPredicates.clear();
        serialized.clear();
    }

    /**
     * Returns {@code true} when no filters have been added.
     * Bug fix: was {@code stringPredicates.isEmpty()}, which returned {@code true}
     * even after plain {@link #add} calls, because those never populated
     * {@code stringPredicates}. Since the lists are always kept in sync,
     * either one is a valid check; {@code serialized} is the canonical source.
     */
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
