package com.gtnewhorizons.galaxia.registry.interfaces;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.function.Predicate;

import com.gtnewhorizons.galaxia.registry.outpost.Filters;
import org.junit.jupiter.api.Test;

final class FiltersTest {

    @Test
    void anyOfMatchesWhenAnyPredicateMatches() {
        Predicate<String> p = Filters.anyOf(s -> s.equals("a"), s -> s.equals("b"));
        assertTrue(p.test("a"));
        assertTrue(p.test("b"));
        assertFalse(p.test("c"));
    }

    @Test
    void allOfMatchesWhenAllPredicatesMatch() {
        Predicate<String> p = Filters.allOf(s -> s.length() > 0, s -> s.startsWith("a"));
        assertTrue(p.test("ab"));
        assertFalse(p.test("b"));
        assertFalse(p.test(""));
    }

    @Test
    void noneOfMatchesWhenNoPredicateMatches() {
        Predicate<String> p = Filters.noneOf(s -> s.equals("a"), s -> s.equals("b"));
        assertTrue(p.test("c"));
        assertFalse(p.test("a"));
    }

    @Test
    void nameRegexMatchesUsingProvidedNameGetter() {
        Predicate<String> p = Filters.nameRegex(".*oo.*", s -> s);
        assertTrue(p.test("foo"));
        assertTrue(p.test("foobar"));
        assertFalse(p.test("bar"));
    }
}
