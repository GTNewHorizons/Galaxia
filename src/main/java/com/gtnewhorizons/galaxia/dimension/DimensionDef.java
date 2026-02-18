package com.gtnewhorizons.galaxia.dimension;

import java.util.Collections;
import java.util.List;

import net.minecraft.world.WorldProvider;

import com.gtnewhorizons.galaxia.dimension.sky.CelestialBody;

public final class DimensionDef {

    public final String name;
    public final int id;
    public final Class<? extends WorldProvider> provider;
    public final boolean keepLoaded;
    public final double gravity;
    public final double air_resistance;
    public final boolean removeSpeedCancelation;
    public final List<CelestialBody> celestialBodies;
    public final int mass;
    public final int orbitalRadius;

    DimensionDef(String name, int id, Class<? extends WorldProvider> provider, boolean keepLoaded, double gravity,
        double airResistance, boolean removeSpeedCancelation, List<CelestialBody> celestialBodies, int mass,
        int orbitalRadius) {
        this.name = name;
        this.id = id;
        this.mass = mass;
        this.orbitalRadius = orbitalRadius;
        this.provider = provider;
        this.keepLoaded = keepLoaded;
        this.gravity = gravity;
        this.air_resistance = airResistance;
        this.removeSpeedCancelation = removeSpeedCancelation;
        this.celestialBodies = celestialBodies == null ? Collections.emptyList()
            : Collections.unmodifiableList(celestialBodies);
    }
}
