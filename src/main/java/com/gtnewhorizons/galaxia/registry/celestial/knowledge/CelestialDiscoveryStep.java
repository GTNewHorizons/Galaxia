package com.gtnewhorizons.galaxia.registry.celestial.knowledge;

import java.util.Locale;

import javax.annotation.Nonnull;

/**
 * Stable discovery tier/type for progressive scanning or research work.
 */
public enum CelestialDiscoveryStep {

    DETECTION(1200),
    PROFILE(4800);

    private final int durationTicks;

    CelestialDiscoveryStep(int durationTicks) {
        if (durationTicks <= 0) throw new IllegalArgumentException("durationTicks must be positive");
        this.durationTicks = durationTicks;
    }

    @Nonnull
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public int durationTicks() {
        return durationTicks;
    }
}
