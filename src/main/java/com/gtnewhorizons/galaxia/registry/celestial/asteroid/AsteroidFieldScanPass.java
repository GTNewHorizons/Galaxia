package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

public enum AsteroidFieldScanPass {

    DETECTION(1200),
    SIGNATURE(2400),
    PROFILE(4800);

    private final int durationTicks;

    AsteroidFieldScanPass(int durationTicks) {
        if (durationTicks <= 0) throw new IllegalArgumentException("durationTicks must be positive");
        this.durationTicks = durationTicks;
    }

    public int durationTicks() {
        return durationTicks;
    }
}
