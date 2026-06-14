package com.gtnewhorizons.galaxia.registry.satellite;

public enum SatelliteKind {

    COMMUNICATION(1L, 0.0D),
    PROSPECTING(0L, 0.10D);

    private final long bandwidthPerSatellite;
    private final double miningSpeedBonusPerSatellite;

    SatelliteKind(long bandwidthPerSatellite, double miningSpeedBonusPerSatellite) {
        this.bandwidthPerSatellite = bandwidthPerSatellite;
        this.miningSpeedBonusPerSatellite = miningSpeedBonusPerSatellite;
    }

    public long bandwidthPerSatellite() {
        return bandwidthPerSatellite;
    }

    public double miningSpeedBonusPerSatellite() {
        return miningSpeedBonusPerSatellite;
    }
}
