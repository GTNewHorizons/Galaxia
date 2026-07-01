package com.gtnewhorizons.galaxia.registry.satellite;

public enum AsteroidSatelliteScanPass {

    DETECTION(1200),
    SIGNATURE(2400),
    PROFILE(4800);

    private final int durationTicks;

    AsteroidSatelliteScanPass(int durationTicks) {
        if (durationTicks <= 0) throw new IllegalArgumentException("durationTicks must be positive");
        this.durationTicks = durationTicks;
    }

    public int durationTicks() {
        return durationTicks;
    }
}
