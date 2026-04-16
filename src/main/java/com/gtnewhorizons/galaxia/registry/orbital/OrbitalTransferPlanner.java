package com.gtnewhorizons.galaxia.registry.orbital;

import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;

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
 * advances OSU at 42× real time: {@code osu = world_ticks / 20.0 * 42.0}.</li>
 * <li>To convert a TOF in OSU to real seconds: {@code tofSeconds = tof / 42.0}.</li>
 * <li>To convert to server ticks: {@code ticks = (int)(tof * 20.0 / 42.0)}.</li>
 * </ul>
 */
public final class OrbitalTransferPlanner {

    /** OSU advance per server tick (20 TPS × 42 OSU/s). */
    public static final double OSU_PER_TICK = 42.0 / 20.0;

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
     * @param tofOsu      time-of-flight in orbital simulation units
     * @param totalDv     total delta-V (departure + capture), orbital velocity units
     * @param departureDv departure delta-V only
     */

    public record TransferRoute(double tofOsu, double totalDv, double departureDv) {

        /** Converts TOF to real seconds. */
        public double tofSeconds() {
            return tofOsu / 42.0;
        }

        /** Converts TOF to server ticks (minimum 1). */
        public int tofTicks() {
            return Math.max(1, (int) (tofOsu * 20.0 / 42.0));
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
            if (tof <= 0.0) continue;

            OrbitalMechanics.OrbitalState dstState = OrbitalMechanics
                .resolveWorldState(root, dest, departureTime + tof);
            OrbitalMechanics.OrbitalState attractorAtArr = OrbitalMechanics
                .resolveWorldState(root, attractor, departureTime + tof);
            if (dstState == null || attractorAtArr == null) continue;

            double r2x = dstState.x() - attractorAtArr.x();
            double r2y = dstState.y() - attractorAtArr.y();
            double vdstX = dstState.vx() - attractorAtArr.vx();
            double vdstY = dstState.vy() - attractorAtArr.vy();

            double crossZ = r1x0 * r2y - r1y0 * r2x;
            double r1mag = Math.hypot(r1x0, r1y0);
            double r2mag = Math.hypot(r2x, r2y);
            double sinDth = Math.abs(crossZ) / Math.max(1e-20, r1mag * r2mag);
            if (sinDth < 1e-3) continue;

            LambertTransfer.Solution progSol = LambertTransfer.between(r1x0, r1y0, r2x, r2y)
                .mu(mu)
                .minPeriapsis(minPeriapsis)
                .timeOfFlight(tof)
                .prograde(true)
                .evaluateAgainst(vsrcX0, vsrcY0, vdstX, vdstY);

            LambertTransfer.Solution retSol = LambertTransfer.between(r1x0, r1y0, r2x, r2y)
                .mu(mu)
                .minPeriapsis(minPeriapsis)
                .timeOfFlight(tof)
                .prograde(false)
                .evaluateAgainst(vsrcX0, vsrcY0, vdstX, vdstY);

            LambertTransfer.Solution best;
            if (progSol.valid() && (!retSol.valid() || progSol.totalDv() <= retSol.totalDv())) {
                best = progSol;
            } else if (retSol.valid()) {
                best = retSol;
            } else {
                continue;
            }

            boolean acceptCandidate;
            if (bestRoute == null) {
                acceptCandidate = true;
            } else if (effectivePriority == RoutePriority.PRIORITIZE_DV) {
                acceptCandidate = best.totalDv() < bestRoute.totalDv()
                    || (Math.abs(best.totalDv() - bestRoute.totalDv()) < 1e-9 && tof < bestRoute.tofOsu());
            } else {
                acceptCandidate = tof < bestRoute.tofOsu()
                    || (Math.abs(tof - bestRoute.tofOsu()) < 1e-9 && best.totalDv() < bestRoute.totalDv());
            }

            if (acceptCandidate) {
                bestRoute = new TransferRoute(tof, best.totalDv(), best.depDv());
            }
        }

        return bestRoute;
    }
}
