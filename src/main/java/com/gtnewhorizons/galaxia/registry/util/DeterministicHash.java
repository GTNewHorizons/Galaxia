package com.gtnewhorizons.galaxia.registry.util;

/**
 * Stable hash primitives for procedural registry content.
 *
 * <p>Generated content stores ids, indices, and authored seeds rather than full
 * random state. Changing this algorithm changes generated worlds, so callers
 * should add a generation-version bump when intentionally changing inputs or
 * distribution rules.
 */
public final class DeterministicHash {

    private static final double UINT53_TO_UNIT = 1.0 / (1L << 53);

    private DeterministicHash() {}

    public static long mix(long first, long... rest) {
        long value = mix64(first);
        for (long next : rest) {
            value = mix64(value ^ next);
        }
        return value;
    }

    public static double unitDouble(long value) {
        return ((value >>> 11) & ((1L << 53) - 1)) * UINT53_TO_UNIT;
    }

    private static long mix64(long value) {
        value += 0x9E3779B97F4A7C15L;
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}
