package com.gtnewhorizons.galaxia.dimension;

/**
 * ENUM for storing all dimensions
 */
public enum DimensionEnum {

    // Format: ENUMNAME(int ID, String name)
    THEIA(20, "Theia"),
    HEMATERIA(21, "Hemateria"),
    FROZEN_BELT(22, "Frozen_Belt");

    final int id;
    final String name;

    DimensionEnum(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getName() {
        return this.name;
    }

    public int getId() {
        return this.id;
    }

}
