package com.gtnewhorizons.galaxia.registry.entity.rocket;

public enum ModuleType {

    ENGINE(1, 0.4f, 0.4f, 0.4f),
    FUEL_TANK(1, 0.1f, 0.6f, 1.0f),
    STORAGE(1, 0.6f, 0.4f, 0.2f),
    CAPSULE(2, 0.9f, 0.9f, 1.0f);

    public final int height;
    public final float r, g, b;

    ModuleType(int height, float r, float g, float b) {
        this.height = height;
        this.r = r;
        this.g = g;
        this.b = b;
    }
}
