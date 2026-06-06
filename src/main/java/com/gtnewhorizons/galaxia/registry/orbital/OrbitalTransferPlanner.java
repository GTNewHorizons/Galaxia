package com.gtnewhorizons.galaxia.registry.orbital;

import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;

/**
 * Shared (non-client) utilities for interplanetary trajectory planning.
 *
 * <p>
 * Contains a self-contained copy of Izzo's Lambert solver and associated helpers so
 * that the server-side logistics engine can compute transfer routes without touching any
 * client-only classes.
 *
 * <p>
 * Time unit convention:
 * <ul>
 * <li>All orbital times are in "orbital simulation units" (OSU). The client viewer
 * advances OSU at {@code OSU_PER_SECOND}× real time: {@code osu = world_ticks * OSU_PER_TICK}.</li>
 * <li>To convert a TOF in OSU to real seconds: {@code tofSeconds = tof / OSU_PER_SECOND}.</li>
 * <li>To convert to server ticks: {@code ticks = (int)(tof / OSU_PER_TICK)}.</li>
 * </ul>
 */
public final class OrbitalTransferPlanner {

    /** OSU advance per server tick. */
    public static final double OSU_PER_TICK = 20.0 / 20.0;
    /** OSU advance per real second at 20 TPS. */
    public static final double OSU_PER_SECOND = OSU_PER_TICK * 20.0;
    private static final double MIN_TRANSFER_ANGLE_SIN = 1e-3;
    private static final double BRANCH_TIE_DV_EPS = 1e-7;
    private static final int MAX_TRAJECTORY_INTEGRATION_SUBSTEPS_PER_SEGMENT = 16;
    private static final double TRAJECTORY_INTEGRATION_TIME_SCALE_FRACTION = 0.03;

    private OrbitalTransferPlanner() {}

    public enum RoutePriority {

        PRIORITIZE_TOF,
        PRIORITIZE_DV;

        public RoutePriority toggled() {
            return this == PRIORITIZE_TOF ? PRIORITIZE_DV : PRIORITIZE_TOF;
        }
    }

    // -------------------------------------------------------------------------
    // Public result type
    // -------------------------------------------------------------------------

    /**
     * Result of a minimum-TOF Lambert route scan.
     *
     * @param tofOsu             time-of-flight in orbital simulation units
     * @param totalDv            total delta-V (departure + capture), orbital velocity units
     * @param departureDv        departure delta-V only
     * @param captureDv          capture delta-V only
     * @param attractorBodyId    central body used by Lambert
     * @param anchorX            central body world X at departure
     * @param anchorY            central body world Y at departure
     * @param r1x                departure relative X from attractor
     * @param r1y                departure relative Y from attractor
     * @param departureVelocityX inertial transfer velocity X relative to attractor at departure
     * @param departureVelocityY inertial transfer velocity Y relative to attractor at departure
     * @param prograde           true when the selected Lambert branch is counter-clockwise
     */

    public record TransferRoute(double tofOsu, double totalDv, double departureDv, double captureDv,
        CelestialObjectId attractorBodyId, double anchorX, double anchorY, double r1x, double r1y,
        double departureVelocityX, double departureVelocityY, boolean prograde) {

        public TransferRoute(double tofOsu, double totalDv, double departureDv) {
            this(
                tofOsu,
                totalDv,
                departureDv,
                Math.max(0.0, totalDv - departureDv),
                CelestialObjectId.INVALID,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                true);
        }

        /** Converts TOF to real seconds. */
        public double tofSeconds() {
            return tofOsu / OSU_PER_SECOND;
        }

        /** Converts TOF to server ticks (minimum 1). */
        public int tofTicks() {
            return Math.max(1, (int) (tofOsu / OSU_PER_TICK));
        }

        public boolean hasTrajectoryGeometry() {
            return attractorBodyId != null && attractorBodyId != CelestialObjectId.INVALID
                && Double.isFinite(anchorX)
                && Double.isFinite(anchorY)
                && Double.isFinite(r1x)
                && Double.isFinite(r1y)
                && Double.isFinite(departureVelocityX)
                && Double.isFinite(departureVelocityY)
                && Double.isFinite(tofOsu)
                && tofOsu > 0.0;
        }
    }

    // -------------------------------------------------------------------------
    // Celestial tree helpers
    // -------------------------------------------------------------------------

    /**
     * Finds a body in the hierarchy by id, starting from {@code root}.
     * Returns {@code null} if not found.
     */
    public static CelestialObject findBodyById(CelestialObject root, String id) {
        if (root == null || id == null) return null;
        return findBodyByIdRec(root, id);
    }

    private static CelestialObject findBodyByIdRec(CelestialObject current, String id) {
        if (id.equals(current.id())) return current;
        for (CelestialObject child : GalaxiaCelestialAPI.getChildren(current)) {
            CelestialObject found = findBodyByIdRec(child, id);
            if (found != null) return found;
        }
        return null;
    }

    /**
     * Finds the nearest {@link CelestialObject.Class#STAR} ancestor of {@code target}.
     * Returns {@code null} if no star is found above the target.
     */
    public static CelestialObject findHostStar(CelestialObject root, CelestialObject target) {
        if (root == null || target == null) return null;
        return findHostStarRec(root, target, null);
    }

    private static CelestialObject findHostStarRec(CelestialObject current, CelestialObject target,
        CelestialObject currentStar) {
        CelestialObject nextStar = current.objectClass() == CelestialObject.Class.STAR ? current : currentStar;
        if (current == target) return nextStar;
        for (CelestialObject child : GalaxiaCelestialAPI.getChildren(current)) {
            CelestialObject found = findHostStarRec(child, target, nextStar);
            if (found != null) return found;
        }
        return null;
    }

    /**
     * Returns the "planetary anchor" for {@code target}:
     * <ul>
     * <li>If the target is a PLANET or GAS_GIANT → returns itself.</li>
     * <li>If the target is a MOON (or ASTEROID, STATION, etc.) → returns the nearest
     * PLANET/GAS_GIANT ancestor, or the target itself if none is found.</li>
     * </ul>
     */
    public static CelestialObject findPlanetaryAnchor(CelestialObject root, CelestialObject target) {
        if (root == null || target == null) return target;
        CelestialObject anchor = findPlanetaryAnchorRec(root, target, null);
        return anchor != null ? anchor : target;
    }

    private static CelestialObject findPlanetaryAnchorRec(CelestialObject current, CelestialObject target,
        CelestialObject currentPlanet) {
        CelestialObject.Class cls = current.objectClass();
        CelestialObject nextPlanet = (cls == CelestialObject.Class.PLANET || cls == CelestialObject.Class.GAS_GIANT)
            ? current
            : currentPlanet;
        if (current == target) return nextPlanet != null ? nextPlanet : current;
        for (CelestialObject child : GalaxiaCelestialAPI.getChildren(current)) {
            CelestialObject found = findPlanetaryAnchorRec(child, target, nextPlanet);
            if (found != null) return found;
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Route computation
    // -------------------------------------------------------------------------

    /**
     * Scans 64 TOF candidates from 0.1× to 3.0× Hohmann and returns the valid route
     * with the minimum time-of-flight.
     *
     * <p>
     * Uses {@code attractor} as the central body for Lambert (typically the host star
     * for SYSTEM-scope, or the planetary anchor for PLANETARY-scope transfers).
     *
     * @return the minimum-TOF {@link TransferRoute}, or {@code null} if no valid route exists
     */

    public static TransferRoute computeRoute(CelestialObject root, CelestialObject attractor, CelestialObject source,
        CelestialObject dest, double departureTime, RoutePriority priority) {
        if (root == null || attractor == null || source == null || dest == null || source == dest) return null;

        double mu = attractor.mu();
        if (mu <= 0.0) return null;

        double hohmannTof = attractor.getHohmannTof(source, dest, root, departureTime);
        if (hohmannTof <= 0.0) return null;

        double minPeriapsis = Math.max(0.05, attractor.spriteSize() * 0.5);
        OrbitalMechanics.OrbitalState srcStateDep = OrbitalMechanics.resolveWorldState(root, source, departureTime);
        OrbitalMechanics.OrbitalState attractorAtDep = OrbitalMechanics
            .resolveWorldState(root, attractor, departureTime);
        if (srcStateDep == null || attractorAtDep == null) return null;

        double r1x0 = srcStateDep.x() - attractorAtDep.x();
        double r1y0 = srcStateDep.y() - attractorAtDep.y();
        double vsrcX0 = srcStateDep.vx() - attractorAtDep.vx();
        double vsrcY0 = srcStateDep.vy() - attractorAtDep.vy();

        RoutePriority effectivePriority = priority != null ? priority : RoutePriority.PRIORITIZE_TOF;
        TransferRoute bestRoute = null;
        int nScan = 64;
        for (int i = 0; i < nScan; i++) {
            double frac = 0.1 + (3.0 - 0.1) * i / (nScan - 1);
            double tof = hohmannTof * frac;
            OrbitalMechanics.OrbitalState dstState = OrbitalMechanics
                .resolveWorldState(root, dest, departureTime + tof);
            OrbitalMechanics.OrbitalState attractorAtArr = OrbitalMechanics
                .resolveWorldState(root, attractor, departureTime + tof);
            if (dstState == null || attractorAtArr == null) continue;

            TransferRoute route = solveFixedRoute(
                attractor.id(),
                mu,
                minPeriapsis,
                attractorAtDep.x(),
                attractorAtDep.y(),
                r1x0,
                r1y0,
                vsrcX0,
                vsrcY0,
                dstState.x() - attractorAtArr.x(),
                dstState.y() - attractorAtArr.y(),
                dstState.vx() - attractorAtArr.vx(),
                dstState.vy() - attractorAtArr.vy(),
                tof);
            if (route == null) continue;

            boolean acceptCandidate;
            if (bestRoute == null) {
                acceptCandidate = true;
            } else if (effectivePriority == RoutePriority.PRIORITIZE_DV) {
                acceptCandidate = route.totalDv() < bestRoute.totalDv()
                    || (Math.abs(route.totalDv() - bestRoute.totalDv()) < 1e-9 && route.tofOsu() < bestRoute.tofOsu());
            } else {
                acceptCandidate = route.tofOsu() < bestRoute.tofOsu()
                    || (Math.abs(route.tofOsu() - bestRoute.tofOsu()) < 1e-9 && route.totalDv() < bestRoute.totalDv());
            }

            if (acceptCandidate) {
                bestRoute = route;
            }
        }

        return bestRoute;
    }

    public static TransferRoute computeFixedRoute(CelestialObject root, CelestialObject attractor,
        CelestialObject source, CelestialObject dest, double departureTime, double tof) {
        if (root == null || attractor == null || source == null || dest == null || source == dest || tof <= 0.0)
            return null;

        double mu = attractor.mu();
        if (mu <= 0.0) return null;

        OrbitalMechanics.OrbitalState srcStateDep = OrbitalMechanics.resolveWorldState(root, source, departureTime);
        OrbitalMechanics.OrbitalState attractorAtDep = OrbitalMechanics
            .resolveWorldState(root, attractor, departureTime);
        OrbitalMechanics.OrbitalState dstState = OrbitalMechanics.resolveWorldState(root, dest, departureTime + tof);
        OrbitalMechanics.OrbitalState attractorAtArr = OrbitalMechanics
            .resolveWorldState(root, attractor, departureTime + tof);
        if (srcStateDep == null || attractorAtDep == null || dstState == null || attractorAtArr == null) return null;

        return solveFixedRoute(
            attractor.id(),
            mu,
            Math.max(0.05, attractor.spriteSize() * 0.5),
            attractorAtDep.x(),
            attractorAtDep.y(),
            srcStateDep.x() - attractorAtDep.x(),
            srcStateDep.y() - attractorAtDep.y(),
            srcStateDep.vx() - attractorAtDep.vx(),
            srcStateDep.vy() - attractorAtDep.vy(),
            dstState.x() - attractorAtArr.x(),
            dstState.y() - attractorAtArr.y(),
            dstState.vx() - attractorAtArr.vx(),
            dstState.vy() - attractorAtArr.vy(),
            tof);
    }

    static TransferRoute solveFixedRoute(CelestialObjectId attractorBodyId, double mu, double minPeriapsis,
        double anchorX, double anchorY, double r1x, double r1y, double vsrcX, double vsrcY, double r2x, double r2y,
        double vdstX, double vdstY, double tof) {
        if (mu <= 0.0 || tof <= 0.0 || isDegenerateTransferGeometry(r1x, r1y, r2x, r2y)) return null;

        boolean preferPrograde = preferredProgradeForTransfer(r1x, r1y, vsrcX, vsrcY, r2x, r2y);
        LambertTransfer.Solution prograde = LambertTransfer.between(r1x, r1y, r2x, r2y)
            .mu(mu)
            .minPeriapsis(minPeriapsis)
            .timeOfFlight(tof)
            .prograde(true)
            .evaluateAgainst(vsrcX, vsrcY, vdstX, vdstY);
        LambertTransfer.Solution retrograde = LambertTransfer.between(r1x, r1y, r2x, r2y)
            .mu(mu)
            .minPeriapsis(minPeriapsis)
            .timeOfFlight(tof)
            .prograde(false)
            .evaluateAgainst(vsrcX, vsrcY, vdstX, vdstY);
        LambertTransfer.Solution best = chooseLowerDvSolution(prograde, retrograde, preferPrograde);
        if (best == null || !best.valid()) return null;

        boolean selectedPrograde = best == prograde;
        return new TransferRoute(
            tof,
            best.totalDv(),
            best.depDv(),
            Math.max(0.0, best.totalDv() - best.depDv()),
            attractorBodyId == null ? CelestialObjectId.INVALID : attractorBodyId,
            anchorX,
            anchorY,
            r1x,
            r1y,
            best.dvx1(),
            best.dvy1(),
            selectedPrograde);
    }

    public static boolean isDegenerateTransferGeometry(double r1x, double r1y, double r2x, double r2y) {
        double r1mag = Math.hypot(r1x, r1y);
        double r2mag = Math.hypot(r2x, r2y);
        if (r1mag <= 1e-10 || r2mag <= 1e-10) return true;
        double scale = Math.max(1e-20, r1mag * r2mag);
        double crossZ = r1x * r2y - r1y * r2x;
        double dot = r1x * r2x + r1y * r2y;
        double sinDth = Math.abs(crossZ) / scale;
        double cosDth = dot / scale;
        return sinDth < MIN_TRANSFER_ANGLE_SIN && cosDth > 0.0;
    }

    public static boolean preferredProgradeForTransfer(double r1x, double r1y, double vsrcX, double vsrcY, double r2x,
        double r2y) {
        double r1mag = Math.hypot(r1x, r1y);
        double r2mag = Math.hypot(r2x, r2y);
        double transferCross = r1x * r2y - r1y * r2x;
        double sinDth = Math.abs(transferCross) / Math.max(1e-20, r1mag * r2mag);
        if (sinDth < MIN_TRANSFER_ANGLE_SIN) {
            double orbitCross = r1x * vsrcY - r1y * vsrcX;
            double velocityScale = Math.max(1.0, r1mag * Math.hypot(vsrcX, vsrcY));
            if (Math.abs(orbitCross) > 1e-12 * velocityScale) return orbitCross >= 0.0;
        }
        return transferCross >= 0.0;
    }

    public static LambertTransfer.Solution chooseLowerDvSolution(LambertTransfer.Solution prograde,
        LambertTransfer.Solution retrograde, boolean preferPrograde) {
        boolean progradeValid = prograde != null && prograde.valid();
        boolean retrogradeValid = retrograde != null && retrograde.valid();
        if (progradeValid && !retrogradeValid) return prograde;
        if (!progradeValid && retrogradeValid) return retrograde;
        if (!progradeValid) return null;
        double dvDelta = prograde.totalDv() - retrograde.totalDv();
        if (Math.abs(dvDelta) <= BRANCH_TIE_DV_EPS) return preferPrograde ? prograde : retrograde;
        return dvDelta < 0.0 ? prograde : retrograde;
    }

    public static int sampleTransferArcInto(double ax, double ay, double rx1, double ry1, double vx1, double vy1,
        double tof, double mu, double[] outXs, double[] outYs, int n) {
        if (outXs == null || outYs == null) return 0;
        int sampleCount = Math.max(2, Math.min(n, Math.min(outXs.length, outYs.length)));
        if (sampleCount <= 0) return 0;
        OrbitalMechanics.OrbitalState state = new OrbitalMechanics.OrbitalState(rx1, ry1, vx1, vy1);
        double segmentDt = tof / (sampleCount - 1);
        outXs[0] = ax + state.x();
        outYs[0] = ay + state.y();
        for (int i = 1; i < sampleCount; i++) {
            int substeps = trajectoryIntegrationSubsteps(state, mu, segmentDt);
            double integrationDt = segmentDt / substeps;
            for (int step = 0; step < substeps; step++) {
                state = OrbitalMechanics.propagateTwoBodyState(state, mu, integrationDt);
                if (state == null) return i;
            }
            outXs[i] = ax + state.x();
            outYs[i] = ay + state.y();
        }
        return sampleCount;
    }

    private static int trajectoryIntegrationSubsteps(OrbitalMechanics.OrbitalState state, double mu, double segmentDt) {
        if (state == null || mu <= 0.0 || segmentDt <= 0.0) return 1;
        double radius = Math.hypot(state.x(), state.y());
        if (radius <= 1e-9) return MAX_TRAJECTORY_INTEGRATION_SUBSTEPS_PER_SEGMENT;
        double localTimeScale = Math.sqrt(radius * radius * radius / mu);
        if (!Double.isFinite(localTimeScale) || localTimeScale <= 1e-9) {
            return MAX_TRAJECTORY_INTEGRATION_SUBSTEPS_PER_SEGMENT;
        }
        int substeps = (int) Math.ceil(segmentDt / (localTimeScale * TRAJECTORY_INTEGRATION_TIME_SCALE_FRACTION));
        return Math.max(1, Math.min(MAX_TRAJECTORY_INTEGRATION_SUBSTEPS_PER_SEGMENT, substeps));
    }
}
