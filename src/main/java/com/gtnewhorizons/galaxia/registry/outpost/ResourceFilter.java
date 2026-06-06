package com.gtnewhorizons.galaxia.registry.outpost;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;

public final class ResourceFilter<T> implements Predicate<T> {

    private static final String REGEX_PREFIX = "~r:";
    private static final String CONTAINS_PREFIX = "~c:";
    private static final String STARTS_WITH_PREFIX = "~s:";
    private static final String ENDS_WITH_PREFIX = "~e:";

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

    public void addRegex(String regex) {
        stringPredicates.add(matches(regex));
        serialized.add(REGEX_PREFIX + regex);
    }

    public void addContains(String text) {
        stringPredicates.add(contains(text));
        serialized.add(CONTAINS_PREFIX + text);
    }

    public void addStartsWith(String prefix) {
        stringPredicates.add(startsWith(prefix));
        serialized.add(STARTS_WITH_PREFIX + prefix);
    }

    public void addEndsWith(String suffix) {
        stringPredicates.add(endsWith(suffix));
        serialized.add(ENDS_WITH_PREFIX + suffix);
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
            if (entry.startsWith(REGEX_PREFIX)) {
                stringPredicates.add(matches(entry.substring(REGEX_PREFIX.length())));
            } else if (entry.startsWith(CONTAINS_PREFIX)) {
                stringPredicates.add(contains(entry.substring(CONTAINS_PREFIX.length())));
            } else if (entry.startsWith(STARTS_WITH_PREFIX)) {
                stringPredicates.add(startsWith(entry.substring(STARTS_WITH_PREFIX.length())));
            } else if (entry.startsWith(ENDS_WITH_PREFIX)) {
                stringPredicates.add(endsWith(entry.substring(ENDS_WITH_PREFIX.length())));
            } else {
                stringPredicates.add(s -> s.equals(entry));
            }
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

    @SafeVarargs
    public static <T> Predicate<T> anyOf(@Nonnull Predicate<T>... predicates) {
        return value -> {
            for (Predicate<T> p : predicates) if (p.test(value)) return true;
            return false;
        };
    }

    @SafeVarargs
    public static <T> Predicate<T> allOf(@Nonnull Predicate<T>... predicates) {
        return value -> {
            for (Predicate<T> p : predicates) if (!p.test(value)) return false;
            return true;
        };
    }

    @SafeVarargs
    public static <T> Predicate<T> noneOf(@Nonnull Predicate<T>... predicates) {
        return value -> {
            for (Predicate<T> p : predicates) if (p.test(value)) return false;
            return true;
        };
    }

    public static Predicate<String> matches(String regex) {
        Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        return value -> pattern.matcher(value)
            .matches();
    }

    public static Predicate<String> contains(String text) {
        String lowered = text.toLowerCase();
        return value -> value.toLowerCase()
            .contains(lowered);
    }

    public static Predicate<String> startsWith(String prefix) {
        String lowered = prefix.toLowerCase();
        return value -> value.toLowerCase()
            .startsWith(lowered);
    }

    public static Predicate<String> endsWith(String suffix) {
        String lowered = suffix.toLowerCase();
        return value -> value.toLowerCase()
            .endsWith(lowered);
    }
}
