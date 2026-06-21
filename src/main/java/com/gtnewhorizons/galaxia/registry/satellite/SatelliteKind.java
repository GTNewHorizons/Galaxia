package com.gtnewhorizons.galaxia.registry.satellite;

public enum SatelliteKind {

    COMMUNICATION(10.0D),
    PROSPECTING(0.10D);

    private final double effectPerSatellite;

    SatelliteKind(double effectPerSatellite) {
        this.effectPerSatellite = effectPerSatellite;
    }

    public double effectPerSatellite() {
        return effectPerSatellite;
    }
}
