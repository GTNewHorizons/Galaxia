package com.gtnewhorizons.galaxia.orbitalGUI.flightplan;

import com.gtnewhorizons.galaxia.orbitalGUI.Hierarchy.OrbitalCelestialBody;
import java.util.ArrayList;
import java.util.List;

public class Spacecraft {
    private final double mass;
    private final double isp;
    private OrbitalCelestialBody currentParent;
    private double posX, posY;
    private double velX, velY;

    private final List<Maneuver> maneuvers = new ArrayList<>();

    public Spacecraft(OrbitalCelestialBody startParent, double x, double y, double vx, double vy,
                      double mass, double isp) {
        this.currentParent = startParent;
        this.posX = x;
        this.posY = y;
        this.velX = vx;
        this.velY = vy;
        this.mass = mass;
        this.isp = isp;
    }

    public void addManeuver(Maneuver m) {
        maneuvers.add(m);
        velX += m.deltaVX();
        velY += m.deltaVY();
    }

    public List<Maneuver> getManeuvers() { return new ArrayList<>(maneuvers); }
    public OrbitalCelestialBody getCurrentParent() { return currentParent; }
    public double getPosX() { return posX; }
    public double getPosY() { return posY; }
    public double getVelX() { return velX; }
    public double getVelY() { return velY; }
}
