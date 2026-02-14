package com.cleanroommc.galaxia.api;

public class Rocket {
    private double baseMass;
    private double fuelMass;
    private double maxFuelStorage;
    private Fuel propellant;

    public Rocket(double baseMass, double maxFuelStorage) {
        this.baseMass = baseMass;
        this.maxFuelStorage = maxFuelStorage;
        this.fuelMass = 0;
    }

    public double getBaseMass() {
        return baseMass;
    }

    public void setBaseMass(double baseMass) {
        this.baseMass = baseMass;
    }

    public double getMaxFuelStorage() {
        return maxFuelStorage;
    }

    public void setMaxFuelStorage(double maxFuelStorage) {
        this.maxFuelStorage = maxFuelStorage;
    }

    public Fuel getPropellant() {
        return propellant;
    }

    public void setPropellant(Fuel propellant) {
        this.propellant = propellant;
    }

    public double getFuelMass() {
        return fuelMass;
    }

    public void setFuelMass(double fuelMass) {
        this.fuelMass = fuelMass;
    }

    public void addFuel(double volume) {
        if ( getMaxFuelStorage() - getFuelMass() < volume) {
            return;
        }
        this.fuelMass += volume;
    }

    public double getTotalMass() {
        return fuelMass + baseMass;
    }
}
