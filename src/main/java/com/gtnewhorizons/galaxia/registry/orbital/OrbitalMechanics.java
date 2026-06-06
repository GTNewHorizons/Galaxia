package com.gtnewhorizons.galaxia.registry.orbital;

import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;

public final class OrbitalMechanics {

    private static final double DEFAULT_STANDARD_GRAVITATIONAL_PARAMETER = 0.1764;
    private static final double MINIMUM_AXIS = 1e-8;
    private static final double MINIMUM_MEAN_MOTION = 1e-9;
    private static final double MINIMUM_RADIUS = 1e-8;
    private static final int KEPLER_ITERATIONS = 10;

    private OrbitalMechanics() {}

    public record OrbitalState(double x, double y, double vx, double vy) {

        public OrbitalState add(OrbitalState other) {
            if (other == null) return this;
            return new OrbitalState(x + other.x(), y + other.y(), vx + other.vx(), vy + other.vy());
        }

        public double radius() {
            return Math.hypot(x, y);
        }

        public double speed() {
            return Math.hypot(vx, vy);
        }
    }

    public record AbsolutePosition(double x, double y) {}

    public static OrbitalState resolveWorldState(CelestialObject root, CelestialObject target, double globalTime) {
        if (root == null || target == null) return null;
        return resolveWorldState(root, target, new OrbitalState(0.0, 0.0, 0.0, 0.0), globalTime);
    }

    public static OrbitalState resolveChildWorldState(CelestialObject parent, CelestialObject child,
        OrbitalState parentState, double globalTime) {
        OrbitalState safeParentState = parentState == null ? new OrbitalState(0.0, 0.0, 0.0, 0.0) : parentState;
        if (usesAbsolutePosition(parent, child)) {
            AbsolutePosition absolute = child.absolutePosition();
            return new OrbitalState(absolute.x(), absolute.y(), 0.0, 0.0);
        }
        OrbitalState localState = calculateOrbitalState(
            child.orbitalParams(),
            resolveAttractorMu(parent, child.orbitalParams()),
            globalTime);
        return safeParentState.add(localState);
    }

    public static OrbitalState calculateOrbitalState(OrbitalParams params, double attractorMu, double globalTime) {
        if (params == null) return new OrbitalState(0.0, 0.0, 0.0, 0.0);

        double semiMajorAxis = params.semiMajorAxis();
        if (semiMajorAxis < MINIMUM_AXIS) return new OrbitalState(0.0, 0.0, 0.0, 0.0);

        double eccentricity = clampEccentricity(params.eccentricity());
        double directionSign = getOrbitDirectionSign(params);
        double meanMotion = resolveMeanMotion(params, attractorMu);
        double meanAnomaly = params.meanAnomalyAtEpoch() + directionSign * meanMotion * globalTime;
        double eccentricAnomaly = solveEccentricAnomaly(meanAnomaly, eccentricity);
        double cosE = Math.cos(eccentricAnomaly);
        double sinE = Math.sin(eccentricAnomaly);
        double sqrtOneMinusESq = Math.sqrt(Math.max(0.0, 1.0 - eccentricity * eccentricity));
        double orbitalX = semiMajorAxis * (cosE - eccentricity);
        double orbitalY = semiMajorAxis * sqrtOneMinusESq * sinE;
        double eccentricAnomalyRate = directionSign * meanMotion / Math.max(MINIMUM_RADIUS, 1.0 - eccentricity * cosE);
        double orbitalVx = -semiMajorAxis * sinE * eccentricAnomalyRate;
        double orbitalVy = semiMajorAxis * sqrtOneMinusESq * cosE * eccentricAnomalyRate;
        double orbitRotation = params.argumentOfPeriapsis() + params.longitudeOfAscendingNode();
        double cosRotation = Math.cos(orbitRotation);
        double sinRotation = Math.sin(orbitRotation);

        return new OrbitalState(
            orbitalX * cosRotation - orbitalY * sinRotation,
            orbitalX * sinRotation + orbitalY * cosRotation,
            orbitalVx * cosRotation - orbitalVy * sinRotation,
            orbitalVx * sinRotation + orbitalVy * cosRotation);
    }

    public static OrbitalState propagateTwoBodyState(OrbitalState state, double attractorMu, double deltaTime) {
        if (state == null) return null;
        if (Math.abs(deltaTime) < 1e-12 || attractorMu <= 0.0) return state;

        OrbitalDerivative k1 = calculateTwoBodyDerivative(state, attractorMu);
        OrbitalDerivative k2 = calculateTwoBodyDerivative(applyDerivative(state, k1, deltaTime * 0.5), attractorMu);
        OrbitalDerivative k3 = calculateTwoBodyDerivative(applyDerivative(state, k2, deltaTime * 0.5), attractorMu);
        OrbitalDerivative k4 = calculateTwoBodyDerivative(applyDerivative(state, k3, deltaTime), attractorMu);

        return new OrbitalState(
            state.x() + deltaTime * (k1.dx() + 2.0 * k2.dx() + 2.0 * k3.dx() + k4.dx()) / 6.0,
            state.y() + deltaTime * (k1.dy() + 2.0 * k2.dy() + 2.0 * k3.dy() + k4.dy()) / 6.0,
            state.vx() + deltaTime * (k1.dvx() + 2.0 * k2.dvx() + 2.0 * k3.dvx() + k4.dvx()) / 6.0,
            state.vy() + deltaTime * (k1.dvy() + 2.0 * k2.dvy() + 2.0 * k3.dvy() + k4.dvy()) / 6.0);
    }

    public static double resolveAttractorMu(CelestialObject attractor, OrbitalParams orbit) {
        if (attractor != null && attractor.properties() != null
            && attractor.properties()
                .standardGravitationalParameter() > 0.0) {
            return attractor.properties()
                .standardGravitationalParameter();
        }
        if (orbit != null && orbit.semiMajorAxis() >= MINIMUM_AXIS) {
            double orbitSpeed = Math.abs(orbit.orbitSpeed());
            if (orbitSpeed > MINIMUM_MEAN_MOTION) {
                return orbitSpeed * orbitSpeed * Math.pow(orbit.semiMajorAxis(), 3.0);
            }
        }
        return DEFAULT_STANDARD_GRAVITATIONAL_PARAMETER;
    }

    public static double getOrbitalPeriod(CelestialObject attractor, OrbitalParams orbit) {
        double meanMotion = resolveMeanMotion(orbit, resolveAttractorMu(attractor, orbit));
        if (meanMotion <= MINIMUM_MEAN_MOTION) return Double.POSITIVE_INFINITY;
        return Math.PI * 2.0 / meanMotion;
    }

    public static double getOrbitDirectionSign(CelestialObject body) {
        return body == null ? 1.0 : getOrbitDirectionSign(body.orbitalParams());
    }

    public static double getOrbitDirectionSign(OrbitalParams params) {
        if (params == null) return 1.0;
        return params.orbitSpeed() < 0.0 ? -1.0 : 1.0;
    }

    public static double getSphereOfInfluenceRadius(CelestialObject parent, CelestialObject body) {
        if (body == null) return 0.0;
        if (body.properties() != null && body.properties()
            .sphereOfInfluenceRadius() > 0.0) {
            return body.properties()
                .sphereOfInfluenceRadius();
        }

        OrbitalParams orbit = body.orbitalParams();
        if (orbit == null || orbit.semiMajorAxis() < MINIMUM_AXIS) {
            return Math.max(0.08, body.spriteSize() * 0.75);
        }

        double parentMu = resolveAttractorMu(parent, orbit);
        double childMu = body.properties() == null ? 0.0
            : body.properties()
                .standardGravitationalParameter();
        if (parentMu <= 0.0 || childMu <= 0.0) {
            return Math.max(0.08, body.spriteSize() * 0.75);
        }
        return Math.max(0.08, orbit.semiMajorAxis() * Math.pow(childMu / parentMu, 0.4));
    }

    public static boolean usesAbsolutePosition(CelestialObject parent, CelestialObject child) {
        return parent != null && child != null
            && parent.objectClass() == CelestialObject.Class.GALAXY
            && child.absolutePosition() != null;
    }

    private static OrbitalState resolveWorldState(CelestialObject current, CelestialObject target,
        OrbitalState currentState, double globalTime) {
        if (current == target) return currentState;
        for (CelestialObject child : GalaxiaCelestialAPI.getChildren(current)) {
            OrbitalState childState = resolveChildWorldState(current, child, currentState, globalTime);
            OrbitalState resolved = resolveWorldState(child, target, childState, globalTime);
            if (resolved != null) return resolved;
        }
        return null;
    }

    private static double resolveMeanMotion(OrbitalParams params, double attractorMu) {
        if (params == null || params.semiMajorAxis() < MINIMUM_AXIS) return 0.0;
        if (attractorMu > 0.0) {
            return Math.sqrt(attractorMu / Math.pow(params.semiMajorAxis(), 3.0));
        }
        return Math.abs(params.orbitSpeed());
    }

    private static double solveEccentricAnomaly(double meanAnomaly, double eccentricity) {
        double eccentricAnomaly = meanAnomaly;
        for (int i = 0; i < KEPLER_ITERATIONS; i++) {
            double value = eccentricAnomaly - eccentricity * Math.sin(eccentricAnomaly) - meanAnomaly;
            double derivative = 1.0 - eccentricity * Math.cos(eccentricAnomaly);
            if (Math.abs(derivative) < 1e-10) break;
            eccentricAnomaly -= value / derivative;
        }
        return eccentricAnomaly;
    }

    private static double clampEccentricity(double eccentricity) {
        if (eccentricity <= 0.0) return 0.0;
        if (eccentricity >= 0.99) return 0.99;
        return eccentricity;
    }

    private static OrbitalDerivative calculateTwoBodyDerivative(OrbitalState state, double attractorMu) {
        double radiusSquared = state.x() * state.x() + state.y() * state.y();
        double inverseRadiusCubed = 1.0 / Math.pow(Math.max(MINIMUM_RADIUS * MINIMUM_RADIUS, radiusSquared), 1.5);
        double accelerationScale = -attractorMu * inverseRadiusCubed;
        return new OrbitalDerivative(
            state.vx(),
            state.vy(),
            state.x() * accelerationScale,
            state.y() * accelerationScale);
    }

    private static OrbitalState applyDerivative(OrbitalState state, OrbitalDerivative derivative, double deltaTime) {
        return new OrbitalState(
            state.x() + derivative.dx() * deltaTime,
            state.y() + derivative.dy() * deltaTime,
            state.vx() + derivative.dvx() * deltaTime,
            state.vy() + derivative.dvy() * deltaTime);
    }

    private record OrbitalDerivative(double dx, double dy, double dvx, double dvy) {}
}
