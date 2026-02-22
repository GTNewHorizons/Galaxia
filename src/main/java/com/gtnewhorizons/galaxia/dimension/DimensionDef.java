package com.gtnewhorizons.galaxia.dimension;

import java.util.Collections;
import java.util.List;

import net.minecraft.world.WorldProvider;

import com.gtnewhorizons.galaxia.dimension.sky.CelestialBody;

/**
 * Class to hold characteristics of the dimension (effectively a posh dataclass)
 */
public final class DimensionDef {

    public final String name;
    public final int id;
    public final Class<? extends WorldProvider> provider;
    public final boolean keepLoaded;
    public final double gravity;
    public final double air_resistance;
    public final boolean removeSpeedCancelation;
    public final List<CelestialBody> celestialBodies;
    public final EffectDef effects;

    // Used in Orbital Calculator primarily
    public final double mass;
    public final double orbitalRadius;
    public final double radius;

    /**
     * Constructs a definition based on the given parameters
     * @param name The dimension name (from ENUM)
     * @param id The dimension ID (from ENUM)
     * @param provider The world provider used
     * @param keepLoaded Whether to keep the dimension loaded (True -> Loaded)
     * @param gravity The gravity multiplier (1 = Overworld, 0.1 = 10% of Overworld)
     * @param airResistance The air resistance on planet
     * @param removeSpeedCancelation Whether to cancel speed over time (see method in builder)
     * @param celestialBodies Celestial Bodies List for sky-box buildiong
     * @param mass The mass of the planet (used in calculations)
     * @param orbitalRadius The distance from the system centre to the planet (used in calculations)
     * @param radius The radius of the planet (used in calculations)
     * @param effects The EffectDef containing all effects for this planet
     */
    DimensionDef(String name, int id, Class<? extends WorldProvider> provider, boolean keepLoaded, double gravity,
        double airResistance, boolean removeSpeedCancelation, List<CelestialBody> celestialBodies, double mass,
        double orbitalRadius, double radius, EffectDef effects) {
        this.name = name;
        this.id = id;
        this.mass = mass;
        this.orbitalRadius = orbitalRadius;
        this.radius = radius;
        this.provider = provider;
        this.keepLoaded = keepLoaded;
        this.gravity = gravity;
        this.air_resistance = airResistance;
        this.removeSpeedCancelation = removeSpeedCancelation;
        this.celestialBodies = celestialBodies == null ? Collections.emptyList()
            : Collections.unmodifiableList(celestialBodies);
        this.effects = effects;
    }
}
