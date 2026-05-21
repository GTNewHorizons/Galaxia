package com.gtnewhorizons.galaxia.registry.interfaces;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.outpost.ResourceFilter;

final class FiltersTest {

    @Test
    void anyOfMatchesWhenAnyPredicateMatches() {
        Predicate<String> p = ResourceFilter.anyOf(s -> s.equals("a"), s -> s.equals("b"));
        assertTrue(p.test("a"));
        assertTrue(p.test("b"));
        assertFalse(p.test("c"));
    }

    @Test
    void allOfMatchesWhenAllPredicatesMatch() {
        Predicate<String> p = ResourceFilter.allOf(s -> s.length() > 0, s -> s.startsWith("a"));
        assertTrue(p.test("ab"));
        assertFalse(p.test("b"));
        assertFalse(p.test(""));
    }

    @Test
    void noneOfMatchesWhenNoPredicateMatches() {
        Predicate<String> p = ResourceFilter.noneOf(s -> s.equals("a"), s -> s.equals("b"));
        assertTrue(p.test("c"));
        assertFalse(p.test("a"));
    }

    @Test
    void matchesRegexPattern() {
        Predicate<String> p = ResourceFilter.matches(".*oo.*");
        assertTrue(p.test("foo"));
        assertTrue(p.test("foobar"));
        assertFalse(p.test("bar"));
    }
}
