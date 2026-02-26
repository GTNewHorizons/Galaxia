package com.gtnewhorizons.galaxia.orbitalGUI.flightplan;

import com.gtnewhorizons.galaxia.orbitalGUI.Hierarchy.OrbitalCelestialBody;
import java.util.ArrayList;
import java.util.List;

public class TrajectoryCalculator {
    private static final double KEPLER_BASE = 0.42;

    public static List<double[]> getTrajectoryPreview(Spacecraft ship, Maneuver proposed, double duration, int points) {
        List<double[]> path = new ArrayList<>();
        double t = proposed.universalTime();
        double px = ship.getPosX();
        double py = ship.getPosY();
        double vx = ship.getVelX() + proposed.deltaVX();
        double vy = ship.getVelY() + proposed.deltaVY();

        for (int i = 0; i <= points; i++) {
            path.add(new double[]{px, py});
            double r2 = px * px + py * py;
            double r = Math.sqrt(r2);
            if (r < 1e-8) break;
            double ax = -KEPLER_BASE * KEPLER_BASE * px / (r2 * r);
            double ay = -KEPLER_BASE * KEPLER_BASE * py / (r2 * r);
            vx += ax * 0.5;
            vy += ay * 0.5;
            px += vx * 0.5;
            py += vy * 0.5;
        }
        return path;
    }

    public static OrbitalCelestialBody findSOI(OrbitalCelestialBody root, double wx, double wy) {
        OrbitalCelestialBody current = root;
        for (OrbitalCelestialBody child : root.children()) {
            if (Math.hypot(wx - 0, wy - 0) < child.soiRadius()) { // TODO: absolute pos later
                return findSOI(child, wx, wy);
            }
        }
        return current;
    }
}
