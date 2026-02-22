package com.gtnewhorizons.galaxia.dimension;

import java.util.Collections;
import java.util.List;

import net.minecraft.world.WorldProvider;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.galaxia.dimension.sky.CelestialBody;

@Desugar
public record DimensionDef(String name, int id, Class<? extends WorldProvider> provider, boolean keepLoaded,
    double gravity, double airResistance, boolean removeSpeedCancelation, List<CelestialBody> celestialBodies,
    EffectDef effects,

    // Orbital
    double mass, double orbitalRadius, double radius) {

    public DimensionDef {
        celestialBodies = celestialBodies == null ? null : Collections.unmodifiableList(celestialBodies);
    }
}
