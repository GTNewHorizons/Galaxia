package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import java.util.Locale;

import javax.annotation.Nonnull;

import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryStep;

public enum AsteroidFieldScanPass implements CelestialDiscoveryStep {

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

    @Override
    @Nonnull
    public String id() {
        return "asteroid_" + name().toLowerCase(Locale.ROOT);
    }
}
