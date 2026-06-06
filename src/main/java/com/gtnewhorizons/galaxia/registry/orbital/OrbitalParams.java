package com.gtnewhorizons.galaxia.registry.orbital;

public record OrbitalParams(double semiMajorAxis, double eccentricity, double inclination,
    double longitudeOfAscendingNode, double argumentOfPeriapsis, double meanAnomalyAtEpoch, double orbitSpeed) {

    public static OrbitalParams circular(double radius, double orbitSpeed) {
        return new OrbitalParams(radius, 0.0, 0.0, 0.0, 0.0, 0.0, orbitSpeed);
    }

    public static OrbitalParams circular(double radius, double orbitSpeed, double meanAnomalyAtEpoch) {
        return new OrbitalParams(radius, 0.0, 0.0, 0.0, 0.0, meanAnomalyAtEpoch, orbitSpeed);
    }

    public double apogee() {
        return semiMajorAxis * (1 + eccentricity);
    }

    public double perigee() {
        return semiMajorAxis * (1 - eccentricity);
    }
}
