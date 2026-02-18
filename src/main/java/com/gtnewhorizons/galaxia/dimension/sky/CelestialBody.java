package com.gtnewhorizons.galaxia.dimension.sky;

import net.minecraft.util.ResourceLocation;

public class CelestialBody {

    public final ResourceLocation texture;
    public final ResourceLocation phaseTexture; // null if none
    public final float size;
    public final double distance;
    public final float inclination;
    public final long orbitalPeriodTicks; // default mc day is 24000
    public final boolean emissive;
    public final boolean hasPhases; // moon has 8 phases
    public final int phaseCount; // default 8
    public final long phaseOffsetTicks;

    public CelestialBody(ResourceLocation texture, ResourceLocation phaseTexture, float size, double distance,
        float inclination, long orbitalPeriodTicks, boolean emissive, boolean hasPhases, int phaseCount,
        long phaseOffsetTicks) {
        this.texture = texture;
        this.phaseTexture = phaseTexture;
        this.size = size;
        this.distance = distance;
        this.inclination = inclination;
        this.orbitalPeriodTicks = orbitalPeriodTicks;
        this.emissive = emissive;
        this.hasPhases = hasPhases;
        this.phaseCount = phaseCount;
        this.phaseOffsetTicks = phaseOffsetTicks;
    }
}
