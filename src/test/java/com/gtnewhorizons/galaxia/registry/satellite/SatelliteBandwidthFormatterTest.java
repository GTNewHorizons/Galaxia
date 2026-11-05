package com.gtnewhorizons.galaxia.registry.satellite;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class SatelliteBandwidthFormatterTest {

    @Test
    void formattedSatelliteBandwidthKeepsOneDecimalPlace() {
        assertEquals("10.0 Kbps", SatelliteBandwidthFormatter.formatKbps(10L));
        assertEquals("0.5 Kb", SatelliteBandwidthFormatter.formatDataDeciKb(5L));
        assertEquals("1.0 Mbps", SatelliteBandwidthFormatter.formatDeciKbps(SatelliteBandwidthFormatter.megabits(1L)));
    }
}
