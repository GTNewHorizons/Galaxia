package com.gtnewhorizons.galaxia.registry.dimension;

/**
 * ENUM for storing all dimensions
 */
public enum DimensionEnum {

    // Format: ENUMNAME(int ID, String name)
    OVERWORLD_ORBIT(-19, "Overworld_Orbit_Stations", "galaxia.dimension.overworld_orbit"),
    MOON(20, "Moon", "galaxia.dimension.moon"),
    MARS(21, "Mars", "galaxia.dimension.mars"),
    FROZEN_BELT(22, "Frozen_Belt", "galaxia.dimension.frozen_belt"),
    PANSPIRA(23, "Panspira", "galaxia.dimension.panspira"),
    TENEBRAE(24, "Tenebrae", "galaxia.dimension.tenebrae");

    final int id;
    final String name;
    final String translationKey;

    DimensionEnum(int id, String name, String translationKey) {
        this.id = id;
        this.name = name;
        this.translationKey = translationKey;
    }

    public String getName() {
        return this.name;
    }

    public int getId() {
        return this.id;
    }

    public String getTranslationKey() {
        return this.translationKey;
    }

}
