package com.gtnewhorizons.galaxia.registry.orbital;

import java.util.ArrayList;

import javax.annotation.Nullable;

import net.minecraft.nbt.NBTTagCompound;

import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.core.state.CelestialObjectKeyState;
import com.gtnewhorizons.galaxia.core.state.NbtReader;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialBodyProperties;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;

public final class OrbitalMechanics {

    /** Existing orbital catalogue length scale, independent of Minecraft block coordinates. */
    public static final double METRES_PER_DISTANCE_UNIT = 6_371_000.0;
    private static final double SOLAR_MU_SI = 132_712_440_041.279419e9;
    private static final double SOLAR_MU_ORBITAL = 7.2e7;
    /** Physical seconds per OSU. Game-clock seconds are a separate compressed duration. */
    public static final double PHYSICAL_SECONDS_PER_OSU = Math
        .sqrt(SOLAR_MU_ORBITAL * Math.pow(METRES_PER_DISTANCE_UNIT, 3) / SOLAR_MU_SI);

    private static final double DEFAULT_STANDARD_GRAVITATIONAL_PARAMETER = 0.1764;
    private static final double MINIMUM_AXIS = 1e-8;
    private static final double MINIMUM_MEAN_MOTION = 1e-9;
    private static final double MINIMUM_RADIUS = 1e-8;
    private static final int KEPLER_ITERATIONS = 10;
    private static final ArrayList<MinorBodyOrbitResolver> MINOR_BODY_ORBIT_RESOLVERS = new ArrayList<>();

    private OrbitalMechanics() {}

    public static double distanceToMetres(double distance) {
        return distance * METRES_PER_DISTANCE_UNIT;
    }

    public static double metresToDistance(double metres) {
        return metres / METRES_PER_DISTANCE_UNIT;
    }

    public static double osuToPhysicalSeconds(double osu) {
        return osu * PHYSICAL_SECONDS_PER_OSU;
    }

    public static double physicalSecondsToOsu(double seconds) {
        return seconds / PHYSICAL_SECONDS_PER_OSU;
    }

    public static double velocityToMetresPerSecond(double velocity) {
        return velocity * METRES_PER_DISTANCE_UNIT / PHYSICAL_SECONDS_PER_OSU;
    }

    public static double metresPerSecondToVelocity(double velocity) {
        return velocity * PHYSICAL_SECONDS_PER_OSU / METRES_PER_DISTANCE_UNIT;
    }

    public static double muToSI(double mu) {
        return mu * SOLAR_MU_SI / SOLAR_MU_ORBITAL;
    }

    public static double siToMu(double mu) {
        return mu * SOLAR_MU_ORBITAL / SOLAR_MU_SI;
    }

    /** Actual burnable mixture only. Inert mass includes cargo and any unusable mixture remainder. */
    public record LocalVehicle(double inertMassKg, double usablePropellantKg, double exhaustVelocityMps,
        double thrustNewtons) {

        public LocalVehicle {
            if (!positive(inertMassKg) || !nonNegative(usablePropellantKg)
                || !positive(exhaustVelocityMps)
                || !positive(thrustNewtons)) throw new IllegalArgumentException("Invalid propulsion inputs");
        }

        public double wetMassKg() {
            return inertMassKg + usablePropellantKg;
        }

        public double usableDeltaVMps() {
            return exhaustVelocityMps * Math.log1p(usablePropellantKg / inertMassKg);
        }
    }

    /** Explicit provisional cost settings, not an additional celestial or vehicle catalogue. */
    public record CostModel(double gravityLossAtOneGMps, double atmosphereLossAtOneAtmMps,
        double atmosphericLandingCostFraction, double maxOrbitalBurnTimeScaleFraction, double localTimeScale,
        double dockingDeltaVMps, double minimumSurfaceGameSeconds) {

        public CostModel {
            if (!nonNegative(gravityLossAtOneGMps) || !nonNegative(atmosphereLossAtOneAtmMps)
                || !nonNegative(atmosphericLandingCostFraction)
                || atmosphericLandingCostFraction > 1
                || !positive(maxOrbitalBurnTimeScaleFraction)
                || !positive(localTimeScale)
                || !nonNegative(dockingDeltaVMps)
                || !nonNegative(minimumSurfaceGameSeconds))
                throw new IllegalArgumentException("Invalid maneuver cost settings");
        }
    }

    public enum LocalStatus {
        FEASIBLE,
        UNAVAILABLE_DATA,
        INSUFFICIENT_THRUST,
        INSUFFICIENT_PROPELLANT,
        APPROXIMATION_LIMIT,
        NOT_FOUND
    }

    public enum ManeuverKind {
        ASCENT,
        UNDOCKING,
        PARKING_COAST,
        DEPARTURE,
        WELL_PASSAGE,
        COAST,
        CAPTURE,
        LANDING,
        DOCKING
    }

    /** Approximate cost obligation. It is not a numerically integrated trajectory or an exact steering program. */
    public record ManeuverObligation(ManeuverKind kind, CelestialObjectKey bodyKey, double deltaVMps,
        double propellantKg, double physicalSeconds, double burnPhysicalSeconds, double startEpochOsu,
        double endEpochOsu, double thrustToWeight, LocalStatus status) {

        public ManeuverObligation {
            if (kind == null || bodyKey == null
                || status == null
                || !nonNegative(deltaVMps)
                || !nonNegative(propellantKg)
                || !nonNegative(physicalSeconds)
                || !nonNegative(burnPhysicalSeconds)
                || burnPhysicalSeconds > physicalSeconds + 1e-6
                || !Double.isFinite(startEpochOsu)
                || !Double.isFinite(endEpochOsu)
                || endEpochOsu < startEpochOsu
                || !nonNegative(thrustToWeight)) throw new IllegalArgumentException("Invalid maneuver obligation");
            boolean coast = kind == ManeuverKind.COAST || kind == ManeuverKind.PARKING_COAST
                || kind == ManeuverKind.WELL_PASSAGE;
            if (coast && (deltaVMps != 0 || propellantKg != 0
                || burnPhysicalSeconds != 0
                || thrustToWeight != 0
                || Math.abs(endEpochOsu - startEpochOsu - physicalSecondsToOsu(physicalSeconds)) > 1e-7))
                throw new IllegalArgumentException("Invalid ballistic coast obligation");
            if (!coast && (Math.abs(physicalSeconds - burnPhysicalSeconds) > 1e-7
                || (deltaVMps > 0 && (propellantKg <= 0 || burnPhysicalSeconds <= 0
                    || (status == LocalStatus.FEASIBLE && endEpochOsu <= startEpochOsu)))))
                throw new IllegalArgumentException("Invalid powered maneuver obligation");
            if (status == LocalStatus.FEASIBLE && (kind == ManeuverKind.ASCENT || kind == ManeuverKind.LANDING)
                && thrustToWeight <= 1) throw new IllegalArgumentException("Insufficient surface maneuver thrust");
        }

        public NBTTagCompound serializeNBT() {
            NBTTagCompound out = new NBTTagCompound();
            out.setString("kind", kind.name());
            out.setTag("body", CelestialObjectKeyState.encode(bodyKey));
            out.setDouble("dv", deltaVMps);
            out.setDouble("propellant", propellantKg);
            out.setDouble("seconds", physicalSeconds);
            out.setDouble("burnSeconds", burnPhysicalSeconds);
            out.setDouble("start", startEpochOsu);
            out.setDouble("end", endEpochOsu);
            out.setDouble("twr", thrustToWeight);
            out.setString("status", status.name());
            return out;
        }

        public static ManeuverObligation deserializeNBT(NBTTagCompound encoded) {
            var in = new NbtReader(encoded, "maneuverObligation");
            return new ManeuverObligation(
                in.enumValue(ManeuverKind.class, "kind"),
                CelestialObjectKeyState.decode(in.compound("body")),
                in.doubleValue("dv"),
                in.doubleValue("propellant"),
                in.doubleValue("seconds"),
                in.doubleValue("burnSeconds"),
                in.doubleValue("start"),
                in.doubleValue("end"),
                in.doubleValue("twr"),
                in.enumValue(LocalStatus.class, "status"));
        }
    }

    public static boolean hasManeuverData(CelestialBodyProperties body) {
        return body.radiusEarthRelative() > 0 && body.standardGravitationalParameter() > 0
            && body.parkingOrbitRadius() > body.radiusEarthRelative();
    }

    /** Two-impulse energy cost from a stationary surface to parking, plus explicit provisional losses. */
    public static double surfaceManeuverDeltaV(CelestialBodyProperties body, CostModel model, boolean landing) {
        if (!hasManeuverData(body)) return Double.NaN;
        double radius = body.radiusMetres(), parking = distanceToMetres(body.parkingOrbitRadius());
        double mu = muToSI(body.standardGravitationalParameter()), axis = (radius + parking) / 2;
        double ideal = Math.sqrt(mu * (2 / radius - 1 / axis)) + Math.sqrt(mu / parking)
            - Math.sqrt(mu * (2 / parking - 1 / axis));
        double gravity = mu / (radius * radius) / 9.80665;
        double pressure = body.surfacePressurePa() / 101325;
        double cost = ideal + model.gravityLossAtOneGMps() * gravity;
        if (landing) return cost * (1 - (1 - model.atmosphericLandingCostFraction()) * Math.min(1, pressure));
        return cost + model.atmosphereLossAtOneAtmMps() * pressure;
    }

    /** Tangential hyperbolic periapsis injection/capture cost. Geometry supplies the actual asymptotic speed. */
    public static double parkingWellDeltaV(CelestialBodyProperties body, double infinitySpeedMps) {
        if (!hasManeuverData(body) || !nonNegative(infinitySpeedMps)) return Double.NaN;
        double r = distanceToMetres(body.parkingOrbitRadius()), mu = muToSI(body.standardGravitationalParameter());
        return Math.sqrt(infinitySpeedMps * infinitySpeedMps + 2 * mu / r) - Math.sqrt(mu / r);
    }

    public static ManeuverObligation approximateBurn(ManeuverKind kind, CelestialObject body, LocalVehicle vehicle,
        double currentMassKg, double deltaV, double epoch, CostModel model) {
        double propellant = -currentMassKg * Math.expm1(-deltaV / vehicle.exhaustVelocityMps());
        double seconds = propellant * vehicle.exhaustVelocityMps() / vehicle.thrustNewtons();
        double radius = body.properties()
            .radiusMetres();
        double mu = muToSI(body.mu());
        double twr = vehicle.thrustNewtons() / (currentMassKg * mu / (radius * radius));
        LocalStatus status = LocalStatus.FEASIBLE;
        if (propellant > currentMassKg - vehicle.inertMassKg() + 1e-7) status = LocalStatus.INSUFFICIENT_PROPELLANT;
        else if ((kind == ManeuverKind.ASCENT || kind == ManeuverKind.LANDING) && twr <= 1)
            status = LocalStatus.INSUFFICIENT_THRUST;
        else if (kind == ManeuverKind.DEPARTURE || kind == ManeuverKind.CAPTURE
            || kind == ManeuverKind.UNDOCKING
            || kind == ManeuverKind.DOCKING) {
                double parking = distanceToMetres(
                    body.properties()
                        .parkingOrbitRadius());
                double tau = Math.sqrt(parking * parking * parking / mu);
                if (seconds / tau > model.maxOrbitalBurnTimeScaleFraction()) status = LocalStatus.APPROXIMATION_LIMIT;
            }
        double gameSeconds = seconds / model.localTimeScale();
        if (kind == ManeuverKind.ASCENT || kind == ManeuverKind.LANDING)
            gameSeconds = Math.max(gameSeconds, model.minimumSurfaceGameSeconds());
        return new ManeuverObligation(
            kind,
            body.key(),
            deltaV,
            propellant,
            seconds,
            seconds,
            epoch,
            epoch + gameSeconds * OrbitalTransferPlanner.OSU_PER_SECOND,
            twr,
            status);
    }

    public static ManeuverObligation approximateCoast(ManeuverKind kind, CelestialObject body, double seconds,
        double epoch) {
        return new ManeuverObligation(
            kind,
            body.key(),
            0,
            0,
            seconds,
            0,
            epoch,
            epoch + physicalSecondsToOsu(seconds),
            0,
            LocalStatus.FEASIBLE);
    }

    /** Analytic hyperbolic branch at a physical child encounter point, with velocities in SI. */
    public record HyperbolicPassage(double vxMps, double vyMps, double radialMps, double periapsisRadiusM) {}

    public static HyperbolicPassage hyperbolicVelocityAtPosition(double mu, double xM, double yM, double infinityVxMps,
        double infinityVyMps, boolean outgoing, int angularMomentumSign) {
        double r = Math.hypot(xM, yM), u = Math.hypot(infinityVxMps, infinityVyMps);
        if (!positive(mu) || !positive(r) || !nonNegative(u) || (angularMomentumSign != 1 && angularMomentumSign != -1))
            throw new IllegalArgumentException("Invalid hyperbolic passage");
        double nx = xM / r, ny = yM / r;
        if (u < 1e-9) {
            double vt = angularMomentumSign * Math.sqrt(2 * mu / r);
            return new HyperbolicPassage(-ny * vt, nx * vt, 0, r);
        }
        double qx = infinityVxMps / u, qy = infinityVyMps / u;
        double c = Math.max(-1, Math.min(1, nx * qx + ny * qy)), s = -ny * qx + nx * qy;
        double b = r * u * s, product = mu * r * (outgoing ? 1 - c : 1 + c);
        double discriminant = Math.sqrt(b * b + 4 * product);
        double h = angularMomentumSign > 0 ? (b >= 0 ? (b + discriminant) / 2 : 2 * product / (discriminant - b))
            : b <= 0 ? (b - discriminant) / 2 : -2 * product / (discriminant + b);
        if (Math.abs(h) < 1e-9) {
            double vr = (outgoing ? 1 : -1) * Math.sqrt(u * u + 2 * mu / r);
            return new HyperbolicPassage(nx * vr, ny * vr, vr, 0);
        }
        double vr = u * c + (outgoing ? 1 : -1) * mu * s / h, vt = h / r;
        double eccentricity = Math.sqrt(1 + u * u * h * h / (mu * mu));
        double periapsis = h * h / (mu * (1 + eccentricity));
        return new HyperbolicPassage(nx * vr - ny * vt, ny * vr + nx * vt, vr, periapsis);
    }

    private static boolean positive(double value) {
        return Double.isFinite(value) && value > 0;
    }

    private static boolean nonNegative(double value) {
        return Double.isFinite(value) && value >= 0;
    }

    public static void registerMinorBodyResolver(MinorBodyOrbitResolver resolver) {
        if (resolver == null) throw new IllegalArgumentException("minor body orbit resolver is required");
        if (!MINOR_BODY_ORBIT_RESOLVERS.contains(resolver)) {
            MINOR_BODY_ORBIT_RESOLVERS.add(resolver);
        }
    }

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

    /** Resolves the same authored parent frames from a supplied headless numerical definition set. */
    @Nullable
    public static OrbitalState resolveWorldState(
        java.util.Map<com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey, CelestialObject> bodies,
        com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey key, double epoch) {
        java.util.List<CelestialObject> path = new java.util.ArrayList<>();
        CelestialObject body = bodies.get(key);
        while (body != null) {
            if (path.size() >= bodies.size()) throw new IllegalArgumentException("Cyclic numerical hierarchy");
            path.add(body);
            body = bodies.get(body.parentKey());
        }
        if (path.isEmpty()) return null;
        OrbitalState state = new OrbitalState(0, 0, 0, 0);
        for (int i = path.size() - 2; i >= 0; i--)
            state = resolveChildWorldState(path.get(i + 1), path.get(i), state, epoch);
        return state;
    }

    public static OrbitalState resolveWorldState(CelestialObject root, CelestialObject target, double globalTime) {
        if (root == null || target == null) return null;
        OrbitalState traversedState = resolveWorldState(root, target, new OrbitalState(0.0, 0.0, 0.0, 0.0), globalTime);
        if (traversedState != null || target.parentKey() == null) return traversedState;

        CelestialObject parent = GalaxiaCelestialAPI.findBodyByKey(root, target.parentKey());
        if (parent == null || parent == target) return null;
        OrbitalState parentState = resolveWorldState(root, parent, globalTime);
        return parentState == null ? null : resolveChildWorldState(parent, target, parentState, globalTime);
    }

    public static OrbitalState resolveChildWorldState(CelestialObject parent, CelestialObject child,
        OrbitalState parentState, double globalTime) {
        OrbitalState safeParentState = parentState == null ? new OrbitalState(0.0, 0.0, 0.0, 0.0) : parentState;
        if (usesAbsolutePosition(parent, child)) {
            AbsolutePosition absolute = child.absolutePosition();
            return new OrbitalState(absolute.x(), absolute.y(), 0.0, 0.0);
        }
        OrbitalState minorBodyState = resolveMinorBodyWorldState(parent, child, safeParentState);
        if (minorBodyState != null) return minorBodyState;
        OrbitalState localState = calculateOrbitalState(
            child.orbitalParams(),
            resolveAttractorMu(parent, child.orbitalParams()),
            globalTime);
        return safeParentState.add(localState);
    }

    public static boolean usesMinorBodyResolvedPosition(CelestialObject parent, CelestialObject child) {
        return resolveMinorBodyWorldState(parent, child, new OrbitalState(0.0, 0.0, 0.0, 0.0)) != null;
    }

    static void resetMinorBodyResolversForTesting() {
        MINOR_BODY_ORBIT_RESOLVERS.clear();
    }

    private static OrbitalState resolveMinorBodyWorldState(CelestialObject parent, CelestialObject child,
        OrbitalState parentState) {
        if (child == null || child.key() == null
            || !child.key()
                .isMinorBody()) {
            return null;
        }
        if (parent == null || parent.key() == null
            || !parent.key()
                .isRegistered()) {
            throw new IllegalStateException("Minor celestial body requires a registered parent body");
        }

        CelestialObjectId parentId = parent.key()
            .registeredBodyId();
        if (child.key()
            .minorBodyId()
            .parentBodyId() != parentId) {
            throw new IllegalStateException(
                "Minor celestial body parent does not match traversal parent: " + child.key());
        }

        for (MinorBodyOrbitResolver resolver : MINOR_BODY_ORBIT_RESOLVERS) {
            OrbitalState resolved = resolver.resolveWorldState(parent, child, parentState);
            if (resolved != null) return resolved;
        }
        return null;
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
        if (eccentricity == 0) return meanAnomaly;
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
