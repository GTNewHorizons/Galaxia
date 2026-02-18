package com.gtnewhorizons.galaxia.dimension.sky;

public class SunBuilder extends CelestialBodyBuilder<SunBuilder> {

    private boolean emissive = true;

    public SunBuilder emissive(boolean emissive) {
        this.emissive = emissive;
        return this;
    }

    @Override
    public CelestialBody build() {
        validate();
        return new CelestialBody(
            texture,
            null,
            size,
            distance,
            inclination,
            orbitalPeriodTicks,
            emissive,
            false,
            0,
            phaseOffsetTicks);
    }
}
