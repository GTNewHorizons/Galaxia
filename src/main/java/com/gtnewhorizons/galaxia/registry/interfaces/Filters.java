package com.gtnewhorizons.galaxia.registry.interfaces;

import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public final class Filters {

    @SafeVarargs
    public static <T> Predicate<T> anyOf(Predicate<T>... predicates) {
        return t -> {
            for (Predicate<T> p : predicates) if (p.test(t)) return true;
            return false;
        };
    }

    @SafeVarargs
    public static <T> Predicate<T> allOf(Predicate<T>... predicates) {
        return t -> {
            for (Predicate<T> p : predicates) if (!p.test(t)) return false;
            return true;
        };
    }

    @SafeVarargs
    public static <T> Predicate<T> noneOf(Predicate<T>... predicates) {
        return t -> {
            for (Predicate<T> p : predicates) if (p.test(t)) return false;
            return true;
        };
    }

    public static <T> Predicate<T> nameRegex(String regex, Function<T, String> nameGetter) {
        Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        return t -> pattern.matcher(nameGetter.apply(t))
            .matches();
    }

    private Filters() {}
}
