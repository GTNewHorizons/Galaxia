package com.gtnewhorizons.galaxia.utility;

// THIS CLASS IS TEMPORARY TO ACT AS A DATACLASS UNTIL ROCKETS ARE WORKING AS A FULL CLASS
public class Rocket {

    private int dryMass;
    private double specificImpulse;
    private int fuelMass;

    public Rocket(int dryMass, double specificImpulse, int fuelMass) {
        this.dryMass = dryMass;
        this.specificImpulse = specificImpulse;
        this.fuelMass = fuelMass;
    }

    public int getDryMass() {
        return dryMass;
    }

    public void setDryMass(int dryMass) {
        this.dryMass = dryMass;
    }

    public double getSpecificImpulse() {
        return specificImpulse;
    }

    public void setSpecificImpulse(double specificImpulse) {
        this.specificImpulse = specificImpulse;
    }

    public int getFuelMass() {
        return fuelMass;
    }

    public void setFuelMass(int fuelMass) {
        this.fuelMass = fuelMass;
    }

    public int getTotalMass() {
        return dryMass + fuelMass;
    }
}
