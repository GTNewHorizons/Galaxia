package com.gtnewhorizons.galaxia.registry.satellite;

public enum SatelliteKind {

    COMMUNICATION(10L),
    PROSPECTING(0L);

    private final long bandwidthKbps;

    SatelliteKind(long bandwidthKbps) {
        this.bandwidthKbps = bandwidthKbps;
    }

    public long bandwidthKbps() {
        return bandwidthKbps;
    }
}
