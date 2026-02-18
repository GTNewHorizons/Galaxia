package com.gtnewhorizons.galaxia.dimension;

public enum DimensionEnum {

    CALX(20, "Calx"),
    DUNIA(21, "Dunia"),
    FROZEN_BELT(22, "Frozen Belt");

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
