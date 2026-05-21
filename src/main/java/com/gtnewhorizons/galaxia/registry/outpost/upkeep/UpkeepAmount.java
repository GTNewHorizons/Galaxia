package com.gtnewhorizons.galaxia.registry.outpost.upkeep;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record UpkeepAmount(long microUnitsPerMinute) implements Comparable<UpkeepAmount> {

    public static final long MICRO_UNITS_PER_WHOLE = 1_000_000L;
    public static final UpkeepAmount ZERO = new UpkeepAmount(0L);

    public UpkeepAmount {
        if (microUnitsPerMinute < 0L) {
            throw new IllegalArgumentException("upkeep amount must be >= 0, got " + microUnitsPerMinute);
        }
    }

    public static UpkeepAmount ofWhole(long wholeUnits) {
        if (wholeUnits < 0L) {
            throw new IllegalArgumentException("wholeUnits must be >= 0, got " + wholeUnits);
        }
        if (wholeUnits == 0L) return ZERO;
        return new UpkeepAmount(Math.multiplyExact(wholeUnits, MICRO_UNITS_PER_WHOLE));
    }

    public static UpkeepAmount ofMicroUnits(long microUnits) {
        if (microUnits == 0L) return ZERO;
        return new UpkeepAmount(microUnits);
    }

    public static UpkeepAmount parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("upkeep amount must not be null/blank");
        }
        BigDecimal normalized;
        try {
            normalized = new BigDecimal(value.trim()).setScale(6, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("upkeep amount supports at most 6 decimal places: " + value, e);
        }
        if (normalized.signum() <= 0) {
            throw new IllegalArgumentException("upkeep amount must be > 0, got " + value);
        }
        return ofMicroUnits(
            normalized.movePointRight(6)
                .longValueExact());
    }

    public boolean isZero() {
        return microUnitsPerMinute == 0L;
    }

    public UpkeepAmount plus(UpkeepAmount other) {
        if (other == null || other.isZero()) return this;
        if (isZero()) return other;
        return ofMicroUnits(Math.addExact(microUnitsPerMinute, other.microUnitsPerMinute));
    }

    public UpkeepAmount minus(UpkeepAmount other) {
        if (other == null || other.isZero()) return this;
        long result = microUnitsPerMinute - other.microUnitsPerMinute;
        if (result < 0L) {
            throw new IllegalArgumentException("upkeep amount would become negative");
        }
        return ofMicroUnits(result);
    }

    public UpkeepAmount multiplyPercent(int percent) {
        if (percent < 0) {
            throw new IllegalArgumentException("percent must be >= 0, got " + percent);
        }
        if (percent == 0 || isZero()) return ZERO;
        if (percent == 100) return this;
        return ofMicroUnits((Math.multiplyExact(microUnitsPerMinute, percent) + 99L) / 100L);
    }

    public long wholeUnitsToCoverDeficit() {
        if (isZero()) return 0L;
        return (microUnitsPerMinute + MICRO_UNITS_PER_WHOLE - 1L) / MICRO_UNITS_PER_WHOLE;
    }

    public static UpkeepAmount wholeUnitsCredit(long wholeUnits) {
        return ofWhole(wholeUnits);
    }

    public String toDisplayString() {
        long whole = microUnitsPerMinute / MICRO_UNITS_PER_WHOLE;
        long fraction = microUnitsPerMinute % MICRO_UNITS_PER_WHOLE;
        if (fraction == 0L) return Long.toString(whole);

        String fractionText = String.format("%06d", fraction);
        int end = fractionText.length();
        while (end > 0 && fractionText.charAt(end - 1) == '0') {
            end--;
        }
        return whole + "." + fractionText.substring(0, end);
    }

    @Override
    public int compareTo(UpkeepAmount other) {
        return Long.compare(microUnitsPerMinute, other.microUnitsPerMinute);
    }
}
