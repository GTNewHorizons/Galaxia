package com.gtnewhorizons.galaxia.dimension.sky;

import net.minecraft.util.ResourceLocation;

public class MoonBuilder extends CelestialBodyBuilder<MoonBuilder> {

    private boolean hasPhases = false;
    private int phaseCount = 8;
    private ResourceLocation phaseTexture;

    public MoonBuilder phases() {
        return phases(true);
    }

    public MoonBuilder phases(boolean enabled) {
        this.hasPhases = enabled;
        return this;
    }

    public MoonBuilder phaseCount(int count) {
        this.phaseCount = count;
        return this;
    }

    public MoonBuilder phaseTexture(ResourceLocation texture) {
        this.phaseTexture = texture;
        return this;
    }

    public MoonBuilder phaseTexture(String texture) {
        return phaseTexture(new ResourceLocation(texture));
    }

    @Override
    public CelestialBody build() {
        validate();
        if (hasPhases && phaseTexture == null) {
            phaseTexture = texture;
        }
        return new CelestialBody(
            texture,
            phaseTexture,
            size,
            distance,
            inclination,
            orbitalPeriodTicks,
            false,
            hasPhases,
            phaseCount,
            phaseOffsetTicks);
    }
}
