package com.gtnewhorizons.galaxia.dimension;

import java.util.Collections;
import java.util.List;

import net.minecraft.world.WorldProvider;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.galaxia.dimension.builder.EffectBuilder;
import com.gtnewhorizons.galaxia.dimension.sky.CelestialBody;

/**
 * Record to hold characteristics of the dimension (effectively a posh dataclass)
 */
@Desugar
public record DimensionDef(String name, int id, Class<? extends WorldProvider> provider, boolean keepLoaded,
    double gravity, double airResistance, boolean removeSpeedCancelation, List<CelestialBody> celestialBodies,
    EffectBuilder effects, double mass, double orbitalRadius, double radius) {

    public DimensionDef {
        celestialBodies = celestialBodies == null ? null : Collections.unmodifiableList(celestialBodies);
    }
}
