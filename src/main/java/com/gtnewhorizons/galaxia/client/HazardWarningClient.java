package com.gtnewhorizons.galaxia.client;

import java.util.Iterator;
import java.util.NoSuchElementException;

import org.jetbrains.annotations.NotNull;

import com.gtnewhorizons.galaxia.utility.hazards.HazardWarnings;

public class HazardWarningClient {

    private static long warnings;

    private static final HazardWarnings[] VALUES = HazardWarnings.values();

    public static void setWarnings(long mask) {
        warnings = mask;
    }

    public static Iterable<HazardWarnings> iterable() {
        return WarningIterator.INSTANCE;
    }

    public static boolean hasWarnings() {
        return warnings != 0;
    }

    private static class WarningIterator implements Iterable<HazardWarnings>, Iterator<HazardWarnings> {

        private static final WarningIterator INSTANCE = new WarningIterator();

        private long remaining;

        @Override
        public @NotNull Iterator<HazardWarnings> iterator() {
            this.remaining = warnings;
            return this;
        }

        @Override
        public boolean hasNext() {
            return remaining != 0;
        }

        @Override
        public HazardWarnings next() {
            if (remaining == 0) {
                throw new NoSuchElementException();
            }
            long bit = remaining & -remaining;
            remaining ^= bit;
            int ordinal = Long.numberOfTrailingZeros(bit);

            return VALUES[ordinal];
        }
    }
}
