package com.gtnewhorizons.galaxia.registry.satellite;

public final class SatelliteBandwidthFormatter {

    private static final long DECI_KBPS_PER_KBPS = 10L;
    private static final long KBPS_PER_MBPS = 1_000L;
    private static final long MBPS_PER_GBPS = 1_000L;

    private SatelliteBandwidthFormatter() {}

    public static long kilobits(long kbps) {
        return multiplySaturated(Math.max(0L, kbps), DECI_KBPS_PER_KBPS);
    }

    public static long megabits(long mbps) {
        return kilobits(multiplySaturated(Math.max(0L, mbps), KBPS_PER_MBPS));
    }

    public static long gigabits(long gbps) {
        return megabits(multiplySaturated(Math.max(0L, gbps), MBPS_PER_GBPS));
    }

    public static String formatKbps(long kbps) {
        return formatDeciKbps(kilobits(kbps));
    }

    public static String formatDeciKbps(long deciKbps) {
        return formatDeci(deciKbps, "Kbps", "Mbps", "Gbps");
    }

    public static String formatDataDeciKb(long deciKb) {
        return formatDeci(deciKb, "Kb", "Mb", "Gb");
    }

    private static String formatDeci(long deciKbps, String kiloUnit, String megaUnit, String gigaUnit) {
        long value = Math.max(0L, deciKbps);
        if (value >= gigabits(1L)) return format(value, gigabits(1L), gigaUnit);
        if (value >= megabits(1L)) return format(value, megabits(1L), megaUnit);
        return format(value, kilobits(1L), kiloUnit);
    }

    private static String format(long deciKbps, long unitDeciKbps, String unit) {
        long whole = deciKbps / unitDeciKbps;
        long tenth = (deciKbps % unitDeciKbps) * 10L / unitDeciKbps;
        return whole + "." + tenth + " " + unit;
    }

    private static long multiplySaturated(long value, long multiplier) {
        if (value <= 0L || multiplier <= 0L) return 0L;
        if (value > Long.MAX_VALUE / multiplier) return Long.MAX_VALUE;
        return value * multiplier;
    }
}
