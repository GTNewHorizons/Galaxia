package com.gtnewhorizons.galaxia.dimension;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.world.WorldProvider;
import net.minecraftforge.common.DimensionManager;

import com.gtnewhorizons.galaxia.dimension.sky.CelestialBody;
import com.gtnewhorizons.galaxia.dimension.sky.SkyBuilder;

public class DimensionBuilder {

    private static final Map<String, DimensionDef> BY_NAME = new HashMap<>();
    private static final Map<Integer, DimensionDef> BY_ID = new HashMap<>();

    public static DimensionDef get(String name) {
        return BY_NAME.get(name.toLowerCase());
    }

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

    public DimensionBuilder enumValue(DimensionEnum planet) {
        if (planet == null) throw new IllegalArgumentException("PlanetEnum cannot be null");
        this.name = planet.getName();
        this.id = planet.getId();
        return this;
    }

    public DimensionBuilder name(String name) {
        this.name = name;
        return this;
    }

    public DimensionBuilder id(int id) {
        this.id = id;
        return this;
    }

    public DimensionBuilder provider(Class<? extends WorldProvider> providerClass) {
        this.providerClass = providerClass;
        return this;
    }

    public DimensionBuilder keepLoaded(boolean keep) {
        this.keepLoaded = keep;
        return this;
    }

    public DimensionBuilder gravity(double gravity) {
        this.gravity = gravity;
        return this;
    }

    public DimensionBuilder airResistance(double air_resistance) {
        this.air_resistance = air_resistance;
        return this;
    }

    public DimensionBuilder sky(SkyBuilder sky) {
        this.celestialBodies = sky.build();
        return this;
    }

    public DimensionBuilder mass(double mass) {
        this.mass = mass;
        return this;
    }

    public DimensionBuilder orbitalRadius(double orbitalRadius) {
        this.orbitalRadius = orbitalRadius;
        return this;
    }

    public DimensionBuilder radius(double radius) {
        this.radius = radius;
        return this;
    }

    /**
     * all entities multiply their speed by 0.91 every tick to prevent infinite speed
     * <p>
     * override this to cancel it (useful for 0g dimensions)
     */
    public DimensionBuilder removeSpeedCancelation() {
        this.removeSpeedCancelation = true;
        return this;
    }

    public DimensionBuilder effects(EffectBuilder effects) {
        this.effects = effects.build();
        return this;
    }

    public DimensionDef build() {
        if (name == null) throw new IllegalStateException("Name required");
        if (providerClass == null) throw new IllegalStateException("Provider required");

        DimensionManager.registerProviderType(id, providerClass, keepLoaded);
        if (!DimensionManager.isDimensionRegistered(id)) {
            DimensionManager.registerDimension(id, id);
        }

        DimensionDef def = new DimensionDef(
            name,
            id,
            providerClass,
            keepLoaded,
            gravity,
            air_resistance,
            removeSpeedCancelation,
            celestialBodies,
            mass,
            orbitalRadius,
            radius,
            effects);

        BY_NAME.put(name.toLowerCase(), def);
        BY_ID.put(id, def);

        return def;
    }
}
