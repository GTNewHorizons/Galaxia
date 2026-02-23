package com.gtnewhorizons.galaxia.dimension;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.world.WorldProvider;
import net.minecraftforge.common.DimensionManager;

import com.gtnewhorizons.galaxia.dimension.sky.CelestialBody;
import com.gtnewhorizons.galaxia.dimension.sky.SkyBuilder;

/**
 * Builder class to configure dimensions properly
 */
public class DimensionBuilder {

    // Hashmaps to help retrieve the DimensionDef more readily
    private static final Map<String, DimensionDef> BY_NAME = new HashMap<>();
    private static final Map<Integer, DimensionDef> BY_ID = new HashMap<>();

    /**
     * Getter for the DimensionDef by name
     *
     * @param name The dimension name
     * @return The associated DimensionDef
     */
    public static DimensionDef get(String name) {
        return BY_NAME.get(name.toLowerCase());
    }

    /**
     * Getter for the DimensionDef by ID
     *
     * @param id The dimension ID
     * @return The associated DimensionDef
     */
    public static DimensionDef get(int id) {
        return BY_ID.get(id);
    }

    private int id;
    private double mass;
    private double orbitalRadius;
    private double radius;
    private String name;
    private Class<? extends WorldProvider> providerClass;
    private boolean keepLoaded = true;
    private double gravity = 1;
    private double air_resistance = 1;
    private boolean removeSpeedCancelation = false;
    private List<CelestialBody> celestialBodies = Collections.emptyList();
    private EffectDef effects;

    /**
     * Sets the name and ID based on the ENUM provided
     *
     * @param planet The ENUM related to the planet
     * @return Configured Builder
     */
    public DimensionBuilder enumValue(DimensionEnum planet) {
        if (planet == null) throw new IllegalArgumentException("PlanetEnum cannot be null");
        this.name = planet.getName();
        this.id = planet.getId();
        return this;
    }

    /**
     * Sets the name of the dimension in the builder
     *
     * @param name The required name
     * @return Configured builder
     */
    public DimensionBuilder name(String name) {
        this.name = name;
        return this;
    }

    /**
     * Sets the ID of the dimension in the builder
     *
     * @param id The required id
     * @return Configured builder
     */
    public DimensionBuilder id(int id) {
        this.id = id;
        return this;
    }

    /**
     * Sets the WorldProvider of the dimension
     *
     * @param providerClass The required provider class
     * @return Configured builder
     */
    public DimensionBuilder provider(Class<? extends WorldProvider> providerClass) {
        this.providerClass = providerClass;
        return this;
    }

    /**
     * Sets whether to keep the dimension loaded
     *
     * @param keep True => Always load the dimension
     * @return Configured builder
     */
    public DimensionBuilder keepLoaded(boolean keep) {
        this.keepLoaded = keep;
        return this;
    }

    /**
     * Sets the relative gravity of the planet compared to Overworld
     *
     * @param gravity The required gravity
     * @return Configured builder
     */
    public DimensionBuilder gravity(double gravity) {
        this.gravity = gravity;
        return this;
    }

    /**
     * Sets the relative air resistance of the planet compared to Overworld
     *
     * @param air_resistance The required air resistance
     * @return Configured builder
     */
    public DimensionBuilder airResistance(double air_resistance) {
        this.air_resistance = air_resistance;
        return this;
    }

    /**
     * Sets the skybox builder for the planet
     *
     * @param sky The SkyBuilder used to generate the skybox
     * @return Configured builder
     */
    public DimensionBuilder sky(List<CelestialBody> sky) {
        this.celestialBodies = sky;
        return this;
    }

    public DimensionBuilder sky(SkyBuilder sky) {
        this.celestialBodies = sky.build();
        return this;
    }

    /**
     * Sets the mass of the planet (used in orbital calculations)
     *
     * @param mass The required planetary mass
     * @return Configured builder
     */
    public DimensionBuilder mass(double mass) {
        this.mass = mass;
        return this;
    }

    public DimensionBuilder orbitalRadius(double orbitalRadius) {
        this.orbitalRadius = orbitalRadius;
        return this;
    }

    /**
     * Sets the radius of the planet (used in orbital calculation, does not affect world generation)
     *
     * @param radius The required planetary radius
     * @return Configured builder
     */
    public DimensionBuilder radius(double radius) {
        this.radius = radius;
        return this;
    }

    /**
     * Sets whether to remove speed cancellation on the planet. All entities by default
     * reduce their speed by 9% every tick. Override this to cancel (useful in low gravity dimensions)
     *
     * @return Configured builder
     */
    public DimensionBuilder removeSpeedCancelation() {
        this.removeSpeedCancelation = true;
        return this;
    }

    /**
     * Sets the effect builder for the dimension
     *
     * @param effects The effect builder required for the planet
     * @return Configured builder
     */
    public DimensionBuilder effects(EffectBuilder effects) {
        this.effects = effects.build();
        return this;
    }

    /**
     * Builds a dimension definition based on fields set in previous methods
     *
     * @return DimensionDef containing fields set from this builder
     */
    public DimensionDef build() {
        // Basic checks for provider and name
        if (name == null) throw new IllegalStateException("Name required");
        if (providerClass == null) throw new IllegalStateException("Provider required");

        // Register the dimension
        DimensionManager.registerProviderType(id, providerClass, keepLoaded);
        if (!DimensionManager.isDimensionRegistered(id)) {
            DimensionManager.registerDimension(id, id);
        }

        // Create DEF with given fields
        DimensionDef def = new DimensionDef(
            name,
            id,
            providerClass,
            keepLoaded,
            gravity,
            air_resistance,
            removeSpeedCancelation,
            celestialBodies,
            effects,
            mass,
            orbitalRadius,
            radius);

        // Add dimension to hashmaps
        BY_NAME.put(name.toLowerCase(), def);
        BY_ID.put(id, def);

        return def;
    }
}
