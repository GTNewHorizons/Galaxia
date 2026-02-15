package com.cleanroommc.galaxia.api;

public class Fuel {

    private final double specificImpulse;

    public Fuel(double specificImpulse) {
        this.specificImpulse = specificImpulse;
    }

    public double getSpecificImpulse() {
        return specificImpulse;
    }
}
