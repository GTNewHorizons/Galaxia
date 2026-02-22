package com.gtnewhorizons.galaxia.dimension.sky;

import net.minecraft.util.ResourceLocation;

import com.github.bsideup.jabel.Desugar;

/**
 * @param texture            object texture location
 * @param size               size on a skybox
 * @param distance           relative distance to player, only changes render order, shouldn't be negative
 * @param orbitalPeriodTicks default mc day - 24000 ticks
 * @param hasPhases          does have phases
 * @param phaseCount         amount of phases
 * @param phaseOffsetTicks   offset in ticks from default position
 * @param isMainLightSource  sets if this object is the main light source on a planet
 */
@Desugar
public record CelestialBody(ResourceLocation texture, float size, double distance, float inclination,
    long orbitalPeriodTicks, boolean hasPhases, int phaseCount, long phaseOffsetTicks, boolean isMainLightSource) {}
