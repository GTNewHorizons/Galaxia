package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint;

public enum RocketPartType {
    CAPSULE,
    FUEL_TANK,
    ENGINE,
    DECOUPLER,
    STRUCTURAL,
    FUNCTIONAL,
    LANDER,
    RIDER;

    public boolean isStackable() {
        return this == FUEL_TANK || this == ENGINE;
    }

    public int maxRadialCount() {
        return isStackable() ? 4 : 0;
    }
}
