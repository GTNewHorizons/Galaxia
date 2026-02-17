package com.gtnewhorizons.galaxia.dimension;

public enum PlanetEnum {

    CALX(20, "Calx"),
    Dunia(21, "Dunia"),
    VULCANOIDS(22, "Vulcanoids");

    final int id;
    final String name;

    PlanetEnum(int id, String name) {
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
