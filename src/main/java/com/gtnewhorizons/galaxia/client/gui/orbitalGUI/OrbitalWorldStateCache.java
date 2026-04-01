package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import java.util.IdentityHashMap;
import java.util.Map;

import com.gtnewhorizons.galaxia.orbitalGUI.Hierarchy.AbsolutePosition;
import com.gtnewhorizons.galaxia.orbitalGUI.Hierarchy.OrbitalCelestialBody;
import com.gtnewhorizons.galaxia.orbitalGUI.Hierarchy.OrbitalParams;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectClass;

final class OrbitalWorldStateCache {

    private final Map<OrbitalCelestialBody, BodyWorldState> states = new IdentityHashMap<>();
    private double cachedTime = Double.NaN;

    void ensure(OrbitalCelestialBody root, double globalTime) {
        if (root == null) {
            states.clear();
            cachedTime = Double.NaN;
            return;
        }
        if (!states.isEmpty() && Double.compare(cachedTime, globalTime) == 0) {
            return;
        }
        rebuild(root, globalTime);
    }

    double[] getWorldPosition(OrbitalCelestialBody body) {
        BodyWorldState state = states.get(body);
        if (state == null) {
            return null;
        }
        return new double[] { state.worldX, state.worldY };
    }

    OrbitalCelestialBody getParent(OrbitalCelestialBody body) {
        BodyWorldState state = states.get(body);
        return state == null ? null : state.parent;
    }

    private void rebuild(OrbitalCelestialBody root, double globalTime) {
        states.clear();
        populate(root, null, 0.0, 0.0, globalTime);
        cachedTime = globalTime;
    }

    private void populate(OrbitalCelestialBody body, OrbitalCelestialBody parent, double worldX, double worldY, double globalTime) {
        states.put(body, new BodyWorldState(parent, worldX, worldY));
        for (OrbitalCelestialBody child : body.children()) {
            double[] childWorldPos = resolveChildWorldPos(body, child, worldX, worldY, globalTime);
            populate(child, body, childWorldPos[0], childWorldPos[1], globalTime);
        }
    }

    static boolean usesAbsolutePosition(OrbitalCelestialBody parent, OrbitalCelestialBody child) {
        return parent != null && parent.objectClass() == CelestialObjectClass.GALAXY && child.absolutePosition() != null;
    }

    static double[] resolveChildWorldPos(OrbitalCelestialBody parent, OrbitalCelestialBody child, double parentWX,
        double parentWY, double globalTime) {
        if (usesAbsolutePosition(parent, child)) {
            AbsolutePosition absolute = child.absolutePosition();
            return new double[] { absolute.x(), absolute.y() };
        }
        double[] local = calculatePosition(child.orbitalParams(), globalTime);
        return new double[] { parentWX + local[0], parentWY + local[1] };
    }

    static double[] calculatePosition(OrbitalParams p, double t) {
        double a = p.semiMajorAxis();
        if (a < 1e-8) return new double[] { 0.0, 0.0 };

        double n = p.orbitSpeed() > 0 ? p.orbitSpeed() : 0.42 * Math.pow(a, -1.5);
        double M = p.meanAnomalyAtEpoch() + n * t;
        double e = p.eccentricity();

        double E = M;
        for (int i = 0; i < 8; i++) {
            E = M + e * Math.sin(E);
        }

        double nu = 2.0 * Math.atan2(Math.sqrt(1.0 + e) * Math.sin(E / 2.0), Math.sqrt(1.0 - e) * Math.cos(E / 2.0));
        double r = a * (1.0 - e * e) / (1.0 + e * Math.cos(nu));
        double ag = nu + p.argumentOfPeriapsis();
        return new double[] { r * Math.cos(ag), r * Math.sin(ag) };
    }

    private static final class BodyWorldState {

        private final OrbitalCelestialBody parent;
        private final double worldX;
        private final double worldY;

        private BodyWorldState(OrbitalCelestialBody parent, double worldX, double worldY) {
            this.parent = parent;
            this.worldX = worldX;
            this.worldY = worldY;
        }
    }
}
