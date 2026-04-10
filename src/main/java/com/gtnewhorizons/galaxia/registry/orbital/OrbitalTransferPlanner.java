package com.gtnewhorizons.galaxia.registry.orbital;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectClass;
import com.gtnewhorizons.galaxia.registry.orbital.Hierarchy.OrbitalCelestialBody;

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
    @Desugar
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
    public static OrbitalCelestialBody findBodyById(OrbitalCelestialBody root, String id) {
        if (root == null || id == null) return null;
        return findBodyByIdRec(root, id);
    }

    private static OrbitalCelestialBody findBodyByIdRec(OrbitalCelestialBody current, String id) {
        if (id.equals(current.id())) return current;
        for (OrbitalCelestialBody child : current.children()) {
            OrbitalCelestialBody found = findBodyByIdRec(child, id);
            if (found != null) return found;
        }
        return null;
    }

    /**
     * Finds the nearest {@link CelestialObjectClass#STAR} ancestor of {@code target}.
     * Returns {@code null} if no star is found above the target.
     */
    public static OrbitalCelestialBody findHostStar(OrbitalCelestialBody root, OrbitalCelestialBody target) {
        if (root == null || target == null) return null;
        return findHostStarRec(root, target, null);
    }

    private static OrbitalCelestialBody findHostStarRec(OrbitalCelestialBody current, OrbitalCelestialBody target,
        OrbitalCelestialBody currentStar) {
        OrbitalCelestialBody nextStar = current.objectClass() == CelestialObjectClass.STAR ? current : currentStar;
        if (current == target) return nextStar;
        for (OrbitalCelestialBody child : current.children()) {
            OrbitalCelestialBody found = findHostStarRec(child, target, nextStar);
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
    public static OrbitalCelestialBody findPlanetaryAnchor(OrbitalCelestialBody root, OrbitalCelestialBody target) {
        if (root == null || target == null) return target;
        OrbitalCelestialBody anchor = findPlanetaryAnchorRec(root, target, null);
        return anchor != null ? anchor : target;
    }

    private static OrbitalCelestialBody findPlanetaryAnchorRec(OrbitalCelestialBody current,
        OrbitalCelestialBody target, OrbitalCelestialBody currentPlanet) {
        CelestialObjectClass cls = current.objectClass();
        OrbitalCelestialBody nextPlanet = (cls == CelestialObjectClass.PLANET || cls == CelestialObjectClass.GAS_GIANT)
            ? current
            : currentPlanet;
        if (current == target) return nextPlanet != null ? nextPlanet : current;
        for (OrbitalCelestialBody child : current.children()) {
            OrbitalCelestialBody found = findPlanetaryAnchorRec(child, target, nextPlanet);
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
    public static TransferRoute computeMinTofRoute(OrbitalCelestialBody root, OrbitalCelestialBody attractor,
        OrbitalCelestialBody source, OrbitalCelestialBody dest, double departureTime) {
        return computeRoute(root, attractor, source, dest, departureTime, RoutePriority.PRIORITIZE_TOF);
    }

    public static TransferRoute computeRoute(OrbitalCelestialBody root, OrbitalCelestialBody attractor,
        OrbitalCelestialBody source, OrbitalCelestialBody dest, double departureTime, RoutePriority priority) {
        if (root == null || attractor == null || source == null || dest == null || source == dest) return null;

        double mu = getBodyMu(attractor);
        if (mu <= 0.0) return null;

        double hohmannTof = getHohmannTof(attractor, source, dest, root, departureTime);
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

        MutableSolution progSol = new MutableSolution();
        MutableSolution retSol = new MutableSolution();
        MutableEvaluation progEval = new MutableEvaluation();
        MutableEvaluation retEval = new MutableEvaluation();

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

            // Skip near-collinear geometries
            double crossZ = r1x0 * r2y - r1y0 * r2x;
            double r1mag = Math.hypot(r1x0, r1y0);
            double r2mag = Math.hypot(r2x, r2y);
            double sinDth = Math.abs(crossZ) / Math.max(1e-20, r1mag * r2mag);
            if (sinDth < 1e-3) continue;

            boolean hasPrograde = evalLambertInto(
                r1x0,
                r1y0,
                r2x,
                r2y,
                tof,
                mu,
                true,
                vsrcX0,
                vsrcY0,
                vdstX,
                vdstY,
                minPeriapsis,
                progSol,
                progEval);
            boolean hasRetrograde = evalLambertInto(
                r1x0,
                r1y0,
                r2x,
                r2y,
                tof,
                mu,
                false,
                vsrcX0,
                vsrcY0,
                vdstX,
                vdstY,
                minPeriapsis,
                retSol,
                retEval);

            MutableEvaluation best;
            if (hasPrograde && (!hasRetrograde || progEval.totalDv <= retEval.totalDv)) {
                best = progEval;
            } else if (hasRetrograde) {
                best = retEval;
            } else {
                continue;
            }

            boolean acceptCandidate;
            if (bestRoute == null) {
                acceptCandidate = true;
            } else if (effectivePriority == RoutePriority.PRIORITIZE_DV) {
                acceptCandidate = best.totalDv < bestRoute.totalDv()
                    || (Math.abs(best.totalDv - bestRoute.totalDv()) < 1e-9 && tof < bestRoute.tofOsu());
            } else {
                acceptCandidate = tof < bestRoute.tofOsu()
                    || (Math.abs(tof - bestRoute.tofOsu()) < 1e-9 && best.totalDv < bestRoute.totalDv());
            }

            if (acceptCandidate) {
                bestRoute = new TransferRoute(tof, best.totalDv, best.depDv);
            }
        }

        return bestRoute;
    }

    // -------------------------------------------------------------------------
    // Lambert solver helpers (Izzo's method, N=0 single revolution)
    // Based on: D. Izzo, "Revisiting Lambert's Problem"
    // -------------------------------------------------------------------------

    private static double getHohmannTof(OrbitalCelestialBody attractor, OrbitalCelestialBody source,
        OrbitalCelestialBody dest, OrbitalCelestialBody root, double time) {
        OrbitalMechanics.OrbitalState aState = OrbitalMechanics.resolveWorldState(root, attractor, time);
        OrbitalMechanics.OrbitalState sState = OrbitalMechanics.resolveWorldState(root, source, time);
        OrbitalMechanics.OrbitalState dState = OrbitalMechanics.resolveWorldState(root, dest, time);
        if (aState == null || sState == null || dState == null) return 100.0;
        double r1 = Math.hypot(sState.x() - aState.x(), sState.y() - aState.y());
        double r2 = Math.hypot(dState.x() - aState.x(), dState.y() - aState.y());
        double mu = Math.max(1e-6, getBodyMu(attractor));
        double sma = (r1 + r2) * 0.5;
        return Math.PI * Math.sqrt(sma * sma * sma / mu);
    }

    private static double getBodyMu(OrbitalCelestialBody body) {
        if (body == null || body.properties() == null) return 0.0;
        return Math.max(
            0.0,
            body.properties()
                .standardGravitationalParameter());
    }

    private static boolean evalLambertInto(double r1x, double r1y, double r2x, double r2y, double tof, double mu,
        boolean prograde, double vsrcX, double vsrcY, double vdstX, double vdstY, double minPeriapsis,
        MutableSolution solOut, MutableEvaluation evalOut) {
        evalOut.valid = false;
        if (!solveLambertInto(r1x, r1y, r2x, r2y, tof, mu, prograde, solOut)) return false;
        double peri = computePeriapsis(r1x, r1y, solOut.dvx1, solOut.dvy1, mu);
        if (peri < minPeriapsis) return false;
        double depDv = Math.hypot(solOut.dvx1 - vsrcX, solOut.dvy1 - vsrcY);
        double capDv = Math.hypot(vdstX - solOut.dvx2, vdstY - solOut.dvy2);
        evalOut.depDv = depDv;
        evalOut.capDv = capDv;
        evalOut.totalDv = depDv + capDv;
        evalOut.valid = true;
        return true;
    }

    private static double computePeriapsis(double rx, double ry, double vx, double vy, double mu) {
        double r = Math.hypot(rx, ry);
        if (r < 1e-10) return 0.0;
        double v2 = vx * vx + vy * vy;
        double energy = 0.5 * v2 - mu / r;
        double h = rx * vy - ry * vx;
        double p = h * h / Math.max(1e-30, mu);
        double disc = 1.0 + 2.0 * energy * p / mu;
        double ecc = Math.sqrt(Math.max(0.0, disc));
        return p / (1.0 + ecc);
    }

    private static boolean solveLambertInto(double rx1, double ry1, double rx2, double ry2, double tof, double mu,
        boolean prograde, MutableSolution out) {
        if (out == null || tof <= 0.0 || mu <= 0.0) return false;
        out.valid = false;

        double r1 = Math.hypot(rx1, ry1);
        double r2 = Math.hypot(rx2, ry2);
        if (r1 < 1e-10 || r2 < 1e-10) return false;

        double cdx = rx2 - rx1, cdy = ry2 - ry1;
        double c = Math.hypot(cdx, cdy);
        if (c < 1e-10) return false;

        double s = (r1 + r2 + c) * 0.5;
        if (s < 1e-10) return false;

        double dot = rx1 * rx2 + ry1 * ry2;
        double crossZ = rx1 * ry2 - ry1 * rx2;
        double dthCCW = Math.atan2(crossZ, dot);
        if (dthCCW < 0.0) dthCCW += 2.0 * Math.PI;
        double dth = prograde ? dthCCW : (2.0 * Math.PI - dthCCW);
        if (dth < 1e-10 || dth > 2.0 * Math.PI - 1e-10) return false;

        double lambda = Math.sqrt(Math.max(0.0, 1.0 - c / s));
        if (dth > Math.PI) lambda = -lambda;

        double T = tof * Math.sqrt(2.0 * mu / (s * s * s));
        double T00 = Math.acos(lambda) + lambda * Math.sqrt(1.0 - lambda * lambda);
        double T1 = 2.0 / 3.0 * (1.0 - lambda * lambda * lambda);

        double x0;
        if (T >= T00) {
            x0 = T00 / T - 1.0;
        } else if (T <= T1) {
            x0 = T1 > 1e-12 ? 2.0 / 3.0 * (1.0 - T / T1) : 0.0;
        } else {
            x0 = (T00 > 1e-12 && T1 > 1e-12) ? Math.exp(Math.log(T / T00) / Math.log(T1 / T00)) - 1.0 : 0.0;
        }
        x0 = Math.max(-0.99, Math.min(0.99, x0));

        double x = x0;
        for (int i = 0; i < 50; i++) {
            double Tx = tofNormalized(x, lambda);
            double err = T - Tx;
            if (Math.abs(err) < 1e-12) break;
            double dTdx = dTofDx(x, Tx, lambda);
            if (Math.abs(dTdx) < 1e-15) break;
            double dx = err / dTdx;
            x = Math.max(-0.999, Math.min(0.999, x + dx));
            if (Math.abs(dx) < 1e-13) break;
        }

        if (Math.abs(T - tofNormalized(x, lambda)) > 0.01 * Math.max(1e-10, T)) return false;

        double y = Math.sqrt(1.0 - lambda * lambda + lambda * lambda * x * x);
        double gamma = Math.sqrt(mu * s / 2.0);
        double rho = (r1 - r2) / c;
        double sigma = Math.sqrt(Math.max(0.0, 1.0 - rho * rho));

        double vr1 = gamma / r1 * ((lambda * y - x) - rho * (lambda * y + x));
        double vt1 = gamma / r1 * sigma * (y + lambda * x);
        double vr2 = -gamma / r2 * ((lambda * y - x) + rho * (lambda * y + x));
        double vt2 = gamma / r2 * sigma * (y + lambda * x);

        double urx1 = rx1 / r1, ury1 = ry1 / r1;
        double urx2 = rx2 / r2, ury2 = ry2 / r2;
        double sign = prograde ? 1.0 : -1.0;
        double utx1 = sign * (-ury1), uty1 = sign * urx1;
        double utx2 = sign * (-ury2), uty2 = sign * urx2;

        out.dvx1 = vr1 * urx1 + vt1 * utx1;
        out.dvy1 = vr1 * ury1 + vt1 * uty1;
        out.dvx2 = vr2 * urx2 + vt2 * utx2;
        out.dvy2 = vr2 * ury2 + vt2 * uty2;
        out.valid = true;
        return true;
    }

    private static double tofNormalized(double x, double lambda) {
        double e = 1.0 - x * x;
        double sinHalfBeta = lambda * Math.sqrt(Math.max(0.0, e));
        sinHalfBeta = Math.max(-1.0, Math.min(1.0, sinHalfBeta));
        double beta = 2.0 * Math.asin(sinHalfBeta);
        double alpha = 2.0 * Math.acos(x);

        double a2 = alpha * alpha;
        double ams = a2 < 0.01 ? alpha * a2 / 6.0 * (1.0 - a2 / 20.0 * (1.0 - a2 / 42.0)) : alpha - Math.sin(alpha);
        double b2 = beta * beta;
        double bms = b2 < 0.01 ? beta * b2 / 6.0 * (1.0 - b2 / 20.0 * (1.0 - b2 / 42.0)) : beta - Math.sin(beta);

        return (ams - bms) / (2.0 * Math.pow(Math.max(1e-30, e), 1.5));
    }

    private static double dTofDx(double x, double T, double lambda) {
        double l2 = lambda * lambda;
        double omx2 = Math.max(1e-30, 1.0 - x * x);
        double sigma = Math.sqrt(Math.max(0.0, 1.0 - l2 * omx2));
        return (3.0 * x * T - 2.0 + 2.0 * l2 * lambda * x / sigma) / omx2;
    }

    // -------------------------------------------------------------------------
    // Mutable scratch objects (avoid allocation in tight loops)
    // -------------------------------------------------------------------------

    private static final class MutableSolution {

        double dvx1, dvy1, dvx2, dvy2;
        boolean valid;
    }

    private static final class MutableEvaluation {

        double depDv, capDv, totalDv;
        boolean valid;
    }
}
