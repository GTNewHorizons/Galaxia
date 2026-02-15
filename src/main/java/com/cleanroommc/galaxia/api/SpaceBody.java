package com.cleanroommc.galaxia.api;

public class SpaceBody {

    private double mass;
    private double radius;
    private double systemRadius;

    public SpaceBody(double mass, double radius, double systemRadius) {
        this.mass = mass;
        this.radius = radius;
        this.systemRadius = systemRadius;
    }

    public double getMass() {
        return mass;
    }

    public void setMass(double mass) {
        this.mass = mass;
    }

    public double getRadius() {
        return radius;
    }

    public void setRadius(double radius) {
        this.radius = radius;
    }

    public double getSystemRadius() {
        return systemRadius;
    }

    public void setSystemRadius(double systemRadius) {
        this.systemRadius = systemRadius;
    }

}
