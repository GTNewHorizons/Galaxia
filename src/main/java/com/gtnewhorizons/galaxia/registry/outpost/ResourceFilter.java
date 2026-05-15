package com.gtnewhorizons.galaxia.registry.outpost;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public final class ResourceFilter<T> implements Predicate<T> {

    private final List<T> identities = new ArrayList<>();
    private final List<Pattern> namePatterns = new ArrayList<>();
    private final List<String> serializedKeys = new ArrayList<>();
    private final Function<T, String> keyEncoder;
    private final Function<String, T> keyDecoder;
    private final Function<T, String> nameGetter;

    private ResourceFilter(Function<T, String> keyEncoder, Function<String, T> keyDecoder,
        Function<T, String> nameGetter) {
        this.keyEncoder = keyEncoder;
        this.keyDecoder = keyDecoder;
        this.nameGetter = nameGetter;
    }

    public void addIdentity(T key) {
        identities.add(key);
        serializedKeys.add(keyEncoder.apply(key));
    }

    public void addNameRegex(String regex) {
        namePatterns.add(Pattern.compile(regex, Pattern.CASE_INSENSITIVE));
        serializedKeys.add("~:" + regex);
    }

    public void remove(T key) {
        String encoded = keyEncoder.apply(key);
        serializedKeys.remove(encoded);
        identities.remove(key);
    }

    public void clear() {
        identities.clear();
        namePatterns.clear();
        serializedKeys.clear();
    }

    public void setAll(List<String> keys) {
        clear();
        for (String raw : keys) {
            if (raw.startsWith("~:")) {
                String regex = raw.substring(2);
                namePatterns.add(Pattern.compile(regex, Pattern.CASE_INSENSITIVE));
                serializedKeys.add(raw);
            } else {
                T decoded = keyDecoder.apply(raw);
                if (decoded != null) {
                    identities.add(decoded);
                    serializedKeys.add(raw);
                }
            }
        }
    }

    public boolean test(T t) {
        if (identities.isEmpty() && namePatterns.isEmpty()) return true;

        for (T identity : identities) {
            if (identity.equals(t)) return true;
        }
        for (Pattern pattern : namePatterns) {
            if (pattern.matcher(nameGetter.apply(t))
                .matches()) return true;
        }
        return false;
    }

    public boolean isEmpty() {
        return identities.isEmpty() && namePatterns.isEmpty();
    }

    public List<T> identities() {
        return List.copyOf(identities);
    }

    public List<String> serialize() {
        return List.copyOf(serializedKeys);
    }

    public static ResourceFilter<ItemStackWrapper> forItems() {
        return new ResourceFilter<>(
            ItemStackWrapper::toKey,
            ItemStackWrapper::fromKey,
            w -> w.toStack(1)
                .getUnlocalizedName());
    }

    public static ResourceFilter<FluidKey> forFluids() {
        return new ResourceFilter<>(
            fk -> fk.fluid()
                .getName(),
            FluidKey::fromName,
            fk -> fk.fluid()
                .getName());
    }
}
