package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import net.minecraft.client.gui.Gui;

import org.lwjgl.opengl.GL11;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.viewport.GuiContext;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.utils.GlStateManager;
import com.cleanroommc.modularui.value.DoubleValue;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.SliderWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.galaxia.client.EnumColors;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectClass;
import com.gtnewhorizons.galaxia.registry.orbital.Hierarchy.OrbitalCelestialBody;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalMechanics;

// ---------------------------------------------------------------------------
// Package-level records
// ---------------------------------------------------------------------------

@Desugar
record InterplanetaryTransferJob(String transferId, String displayName, String inventorySummary,
    OrbitalCelestialBody rootBody, OrbitalCelestialBody sourceBody, OrbitalCelestialBody destinationBody,
    OrbitalCelestialBody orbitAnchorBody, double departureTime, double arrivalTime, double[] trajectoryXs,
    double[] trajectoryYs, int trajectoryPointCount) {

    public InterplanetaryTransferJob {
        transferId = transferId == null ? "" : transferId;
        displayName = displayName == null ? "" : displayName;
        inventorySummary = inventorySummary == null ? "Empty" : inventorySummary;
        trajectoryPointCount = Math.max(
            0,
            Math.min(
                trajectoryPointCount,
                Math.min(
                    trajectoryXs == null ? 0 : trajectoryXs.length,
                    trajectoryYs == null ? 0 : trajectoryYs.length)));
        trajectoryXs = trajectoryPointCount == 0 ? new double[0] : Arrays.copyOf(trajectoryXs, trajectoryPointCount);
        trajectoryYs = trajectoryPointCount == 0 ? new double[0] : Arrays.copyOf(trajectoryYs, trajectoryPointCount);
    }

    double duration() {
        return Math.max(1e-6, arrivalTime - departureTime);
    }

    double progress(double currentTime) {
        double d = duration();
        double t = (currentTime - departureTime) / d;
        if (t <= 0.0) return 0.0;
        if (t >= 1.0) return 1.0;
        return t;
    }

    boolean isFinished(double currentTime) {
        return currentTime >= arrivalTime;
    }
}

// ---------------------------------------------------------------------------
// Main class
// ---------------------------------------------------------------------------

public final class InterplanetaryTransferSystem {

    private static final int PREVIEW_TRAJECTORY_SAMPLES = 96;

    private InterplanetaryTransferSystem() {}

    static final class MutableLambertSolution {

        private double departureVelocityX;
        private double departureVelocityY;
        private double arrivalVelocityX;
        private double arrivalVelocityY;
        private boolean valid = false;

        double departureVelocityX() {
            return departureVelocityX;
        }

        double departureVelocityY() {
            return departureVelocityY;
        }

        double arrivalVelocityX() {
            return arrivalVelocityX;
        }

        double arrivalVelocityY() {
            return arrivalVelocityY;
        }

        boolean valid() {
            return valid;
        }

        void set(double departureVelocityX, double departureVelocityY, double arrivalVelocityX,
            double arrivalVelocityY) {
            this.departureVelocityX = departureVelocityX;
            this.departureVelocityY = departureVelocityY;
            this.arrivalVelocityX = arrivalVelocityX;
            this.arrivalVelocityY = arrivalVelocityY;
            this.valid = true;
        }

        void clear() {
            departureVelocityX = 0.0;
            departureVelocityY = 0.0;
            arrivalVelocityX = 0.0;
            arrivalVelocityY = 0.0;
            valid = false;
        }
    }

    private static final class MutableLambertEvaluation {

        private double departureDeltaV;
        private double captureDeltaV;
        private double totalDeltaV;
        private boolean valid = false;

        double departureDeltaV() {
            return departureDeltaV;
        }

        double captureDeltaV() {
            return captureDeltaV;
        }

        double totalDeltaV() {
            return totalDeltaV;
        }

        boolean valid() {
            return valid;
        }

        void set(double departureDeltaV, double captureDeltaV) {
            this.departureDeltaV = departureDeltaV;
            this.captureDeltaV = captureDeltaV;
            this.totalDeltaV = departureDeltaV + captureDeltaV;
            this.valid = true;
        }

        void clear() {
            departureDeltaV = 0.0;
            captureDeltaV = 0.0;
            totalDeltaV = 0.0;
            valid = false;
        }
    }

    public static final class LambertStressReport {

        private final int requestedSimulations;
        private final int executedSimulations;
        private final int candidatePlanetCount;
        private final int successfulTransfers;
        private final double averageTotalDv;
        private final double bestTotalDv;
        private final double worstTotalDv;

        LambertStressReport(int requestedSimulations, int executedSimulations, int candidatePlanetCount,
            int successfulTransfers, double averageTotalDv, double bestTotalDv, double worstTotalDv) {
            this.requestedSimulations = Math.max(0, requestedSimulations);
            this.executedSimulations = Math.max(0, executedSimulations);
            this.candidatePlanetCount = Math.max(0, candidatePlanetCount);
            this.successfulTransfers = Math.max(0, successfulTransfers);
            this.averageTotalDv = averageTotalDv;
            this.bestTotalDv = bestTotalDv;
            this.worstTotalDv = worstTotalDv;
        }

        public int requestedSimulations() {
            return requestedSimulations;
        }

        public int executedSimulations() {
            return executedSimulations;
        }

        public int candidatePlanetCount() {
            return candidatePlanetCount;
        }

        public int successfulTransfers() {
            return successfulTransfers;
        }

        public int failedTransfers() {
            return Math.max(0, executedSimulations - successfulTransfers);
        }

        public boolean hasEnoughPlanets() {
            return candidatePlanetCount >= 2;
        }

        public boolean hasSuccesses() {
            return successfulTransfers > 0;
        }

        public double averageTotalDv() {
            return averageTotalDv;
        }

        public double bestTotalDv() {
            return bestTotalDv;
        }

        public double worstTotalDv() {
            return worstTotalDv;
        }
    }

    public static final class MutableTransferPoint {

        private double worldX = 0.0;
        private double worldY = 0.0;
        private boolean valid = false;

        public double worldX() {
            return worldX;
        }

        public double worldY() {
            return worldY;
        }

        public boolean valid() {
            return valid;
        }

        private void set(double worldX, double worldY) {
            this.worldX = worldX;
            this.worldY = worldY;
            this.valid = true;
        }

        private void clear() {
            this.worldX = 0.0;
            this.worldY = 0.0;
            this.valid = false;
        }
    }

    // -----------------------------------------------------------------------
    // Public API: getCurrentTransferPoint
    // -----------------------------------------------------------------------

    public static boolean writeCurrentTransferPoint(InterplanetaryTransferJob transfer, double currentTime,
        MutableTransferPoint out) {
        if (out == null) return false;
        if (transfer == null) {
            out.clear();
            return false;
        }
        int pointCount = transfer.trajectoryPointCount();
        if (pointCount <= 0) {
            out.set(0.0, 0.0);
            return true;
        }
        if (pointCount == 1) {
            out.set(transfer.trajectoryXs()[0], transfer.trajectoryYs()[0]);
            return true;
        }
        double dep = transfer.departureTime();
        double arr = transfer.arrivalTime();
        double dur = Math.max(1e-12, arr - dep);
        double t = (currentTime - dep) / dur;
        if (t <= 0.0) {
            out.set(transfer.trajectoryXs()[0], transfer.trajectoryYs()[0]);
            return true;
        }
        if (t >= 1.0) {
            out.set(transfer.trajectoryXs()[pointCount - 1], transfer.trajectoryYs()[pointCount - 1]);
            return true;
        }
        double idx = t * (pointCount - 1);
        int lo = (int) idx;
        int hi = Math.min(lo + 1, pointCount - 1);
        double frac = idx - lo;
        double ax = transfer.trajectoryXs()[lo];
        double ay = transfer.trajectoryYs()[lo];
        double bx = transfer.trajectoryXs()[hi];
        double by = transfer.trajectoryYs()[hi];
        out.set(ax + (bx - ax) * frac, ay + (by - ay) * frac);
        return true;
    }

    // -----------------------------------------------------------------------
    // Izzo Lambert solver (N=0 single revolution)
    // Based on: D. Izzo, "Revisiting Lambert's Problem"
    // Celestial Mechanics and Dynamical Astronomy 121(1), 2015
    // -----------------------------------------------------------------------

    /**
     * Solves Lambert's problem using Izzo's method.
     *
     * @param rx1      departure position X in attractor frame
     * @param ry1      departure position Y in attractor frame
     * @param rx2      arrival position X in attractor frame
     * @param ry2      arrival position Y in attractor frame
     * @param tof      time of flight (same units as mu)
     * @param mu       gravitational parameter of attractor
     * @param prograde true for prograde (CCW) transfer
     * @return [vx1, vy1, vx2, vy2] or null if unsolvable
     */
    static boolean solveLambertInto(double rx1, double ry1, double rx2, double ry2, double tof, double mu,
        boolean prograde, MutableLambertSolution out) {
        if (out == null || tof <= 0.0 || mu <= 0.0) return false;
        out.clear();

        // Step 1: Geometry
        double r1 = Math.hypot(rx1, ry1);
        double r2 = Math.hypot(rx2, ry2);
        if (r1 < 1e-10 || r2 < 1e-10) return false;

        double cdx = rx2 - rx1;
        double cdy = ry2 - ry1;
        double c = Math.hypot(cdx, cdy);
        if (c < 1e-10) return false;

        double s = (r1 + r2 + c) * 0.5;
        if (s < 1e-10) return false;

        // Use atan2 instead of acos+sign(crossZ) for the transfer angle.
        // acos gives dth in [0,π] and requires a crossZ sign check to determine
        // which way around; that sign is noisy near 180° (crossZ ≈ 0) and flips
        // each frame, producing alternating mirror trajectories (the visual jitter).
        // atan2(crossZ, dot) returns the CCW angle in (-π,π] continuously:
        // at exactly 180° atan2(0, negative) = π deterministically — no sign flip.
        double dot = rx1 * rx2 + ry1 * ry2;
        double crossZ = rx1 * ry2 - ry1 * rx2;
        double dthCCW = Math.atan2(crossZ, dot);
        if (dthCCW < 0.0) dthCCW += 2.0 * Math.PI;
        // Prograde = travel the CCW arc; retrograde = travel the CW arc (2π - dthCCW).
        double dth = prograde ? dthCCW : (2.0 * Math.PI - dthCCW);
        if (dth < 1e-10 || dth > 2.0 * Math.PI - 1e-10) return false;

        double lambda = Math.sqrt(Math.max(0.0, 1.0 - c / s));
        if (dth > Math.PI) lambda = -lambda;

        // Step 2: Normalized TOF
        double T = tof * Math.sqrt(2.0 * mu / (s * s * s));

        // Step 5: Initial guess
        double T00 = Math.acos(lambda) + lambda * Math.sqrt(1.0 - lambda * lambda);
        double T1 = 2.0 / 3.0 * (1.0 - lambda * lambda * lambda);

        double x0;
        if (T >= T00) {
            x0 = T00 / T - 1.0;
        } else if (T <= T1) {
            if (T1 > 1e-12) {
                x0 = 2.0 / 3.0 * (1.0 - T / T1);
            } else {
                x0 = 0.0;
            }
        } else {
            if (T00 > 1e-12 && T1 > 1e-12) {
                double logRatio = Math.log(T / T00) / Math.log(T1 / T00);
                x0 = Math.exp(logRatio) - 1.0;
            } else {
                x0 = 0.0;
            }
        }
        x0 = Math.max(-0.99, Math.min(0.99, x0));

        // Step 6: Newton iteration
        double x = x0;
        for (int i = 0; i < 50; i++) {
            double Tx = tofNormalized(x, lambda);
            double err = T - Tx;
            if (Math.abs(err) < 1e-12) break;
            double dTdx = dTofNormalizeddx(x, Tx, lambda);
            if (Math.abs(dTdx) < 1e-15) break;
            double dx = err / dTdx;
            x = Math.max(-0.999, Math.min(0.999, x + dx));
            if (Math.abs(dx) < 1e-13) break;
        }

        // Convergence check
        double finalT = tofNormalized(x, lambda);
        if (Math.abs(T - finalT) > 0.01 * Math.max(1e-10, T)) return false;

        // Step 7: Velocity reconstruction
        double y = Math.sqrt(1.0 - lambda * lambda + lambda * lambda * x * x);
        double gamma = Math.sqrt(mu * s / 2.0);
        double rho = (r1 - r2) / c;
        double sigma = Math.sqrt(Math.max(0.0, 1.0 - rho * rho));

        double vr1 = gamma / r1 * ((lambda * y - x) - rho * (lambda * y + x));
        double vt1 = gamma / r1 * sigma * (y + lambda * x);
        double vr2 = -gamma / r2 * ((lambda * y - x) + rho * (lambda * y + x));
        double vt2 = gamma / r2 * sigma * (y + lambda * x);

        // Unit radial vectors
        double urx1 = rx1 / r1, ury1 = ry1 / r1;
        double urx2 = rx2 / r2, ury2 = ry2 / r2;

        // Tangential unit vectors
        double sign = prograde ? 1.0 : -1.0;
        double utx1 = sign * (-ury1), uty1 = sign * urx1;
        double utx2 = sign * (-ury2), uty2 = sign * urx2;

        // Cartesian velocities
        double vx1 = vr1 * urx1 + vt1 * utx1;
        double vy1 = vr1 * ury1 + vt1 * uty1;
        double vx2 = vr2 * urx2 + vt2 * utx2;
        double vy2 = vr2 * ury2 + vt2 * uty2;

        out.set(vx1, vy1, vx2, vy2);
        return true;
    }

    static double tofNormalized(double x, double lambda) {
        double e = 1.0 - x * x;
        double sinHalfBeta = lambda * Math.sqrt(Math.max(0.0, e));
        sinHalfBeta = Math.max(-1.0, Math.min(1.0, sinHalfBeta));
        double beta = 2.0 * Math.asin(sinHalfBeta);
        double alpha = 2.0 * Math.acos(x);

        // When alpha is small (x near 1), direct subtraction alpha - sin(alpha)
        // loses nearly all significant digits (e.g. x=0.9999: alpha=0.028,
        // sin(alpha)=0.027999... → only 2 sig figs remain).
        // Series expansion a - sin(a) = a³/6·(1 - a²/20·(1 - a²/42)) is exact
        // to ~1e-14 for a < 0.1 and avoids the cancellation entirely.
        // Same issue affects beta when lambda·sqrt(e) is small (near 0° or 180°).
        double a_minus_sina;
        double alpha2 = alpha * alpha;
        if (alpha2 < 0.01) {
            a_minus_sina = alpha * alpha2 / 6.0 * (1.0 - alpha2 / 20.0 * (1.0 - alpha2 / 42.0));
        } else {
            a_minus_sina = alpha - Math.sin(alpha);
        }

        double b_minus_sinb;
        double beta2 = beta * beta;
        if (beta2 < 0.01) {
            b_minus_sinb = beta * beta2 / 6.0 * (1.0 - beta2 / 20.0 * (1.0 - beta2 / 42.0));
        } else {
            b_minus_sinb = beta - Math.sin(beta);
        }

        double denom = Math.pow(Math.max(1e-30, e), 1.5);
        return (a_minus_sina - b_minus_sinb) / (2.0 * denom);
    }

    static double dTofNormalizeddx(double x, double T, double lambda) {
        double lambdaSq = lambda * lambda;
        double oneMinusX2 = Math.max(1e-30, 1.0 - x * x);
        double sigma = Math.sqrt(Math.max(0.0, 1.0 - lambdaSq * oneMinusX2));
        return (3.0 * x * T - 2.0 + 2.0 * lambda * lambda * lambda * x / sigma) / oneMinusX2;
    }

    /**
     * Propagates a 2-body orbit and returns world-frame trajectory points.
     * anchorX/Y is the world position of the attractor at departure time.
     */
    static int sampleTransferArcInto(double ax, double ay, double rx1, double ry1, double vx1, double vy1, double tof,
        double mu, double[] outXs, double[] outYs, int n) {
        if (outXs == null || outYs == null) return 0;
        int sampleCount = Math.max(2, Math.min(n, Math.min(outXs.length, outYs.length)));
        if (sampleCount <= 0) return 0;
        OrbitalMechanics.OrbitalState state = new OrbitalMechanics.OrbitalState(rx1, ry1, vx1, vy1);
        double dt = tof / (sampleCount - 1);
        for (int i = 0; i < sampleCount; i++) {
            if (i > 0) {
                state = OrbitalMechanics.propagateTwoBodyState(state, mu, dt);
                if (state == null) return i;
            }
            outXs[i] = ax + state.x();
            outYs[i] = ay + state.y();
        }
        return sampleCount;
    }

    // -----------------------------------------------------------------------
    // Helper methods
    // -----------------------------------------------------------------------

    private static OrbitalCelestialBody findHostStar(OrbitalCelestialBody root, OrbitalCelestialBody target) {
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

    private static double getBodyMu(OrbitalCelestialBody body) {
        if (body == null || body.properties() == null) return 0.0;
        return Math.max(
            0.0,
            body.properties()
                .standardGravitationalParameter());
    }

    public static LambertStressReport runLambertStress(OrbitalCelestialBody root, OrbitalCelestialBody star,
        double globalTime, int simulations, double maxDvLimit) {
        int requested = Math.max(0, simulations);
        if (requested == 0 || root == null || star == null || star.objectClass() != CelestialObjectClass.STAR) {
            return new LambertStressReport(requested, 0, 0, 0, 0.0, 0.0, 0.0);
        }

        List<OrbitalCelestialBody> candidatePlanets = new ArrayList<>();
        collectStressPlanets(star, candidatePlanets);
        if (candidatePlanets.size() < 2) {
            return new LambertStressReport(requested, 0, candidatePlanets.size(), 0, 0.0, 0.0, 0.0);
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();
        int successCount = 0;
        double sumDv = 0.0;
        double bestDv = Double.POSITIVE_INFINITY;
        double worstDv = 0.0;
        double dvLimit = Math.max(0.0, maxDvLimit);

        for (int i = 0; i < requested; i++) {
            int sourceIndex = random.nextInt(candidatePlanets.size());
            int destinationIndex = random.nextInt(candidatePlanets.size() - 1);
            if (destinationIndex >= sourceIndex) destinationIndex++;

            OrbitalCelestialBody source = candidatePlanets.get(sourceIndex);
            OrbitalCelestialBody destination = candidatePlanets.get(destinationIndex);

            double departureOffset = random.nextDouble(0.0, 600.0);
            double departureTime = globalTime + departureOffset;
            double solvedDv = findBestLambertWithinDvLimit(root, star, source, destination, departureTime, dvLimit);
            if (solvedDv < 0.0) continue;

            successCount++;
            sumDv += solvedDv;
            if (solvedDv < bestDv) bestDv = solvedDv;
            if (solvedDv > worstDv) worstDv = solvedDv;
        }

        double avgDv = successCount > 0 ? sumDv / successCount : 0.0;
        double clampedBestDv = successCount > 0 ? bestDv : 0.0;
        double clampedWorstDv = successCount > 0 ? worstDv : 0.0;
        return new LambertStressReport(
            requested,
            requested,
            candidatePlanets.size(),
            successCount,
            avgDv,
            clampedBestDv,
            clampedWorstDv);
    }

    private static void collectStressPlanets(OrbitalCelestialBody current, List<OrbitalCelestialBody> out) {
        if (current == null || out == null) return;
        CelestialObjectClass objectClass = current.objectClass();
        if (objectClass == CelestialObjectClass.PLANET || objectClass == CelestialObjectClass.GAS_GIANT) {
            out.add(current);
        }
        for (OrbitalCelestialBody child : current.children()) {
            collectStressPlanets(child, out);
        }
    }

    private static double findBestLambertWithinDvLimit(OrbitalCelestialBody root, OrbitalCelestialBody star,
        OrbitalCelestialBody origin, OrbitalCelestialBody destination, double departureTime, double dvLimit) {
        if (root == null || star == null || origin == null || destination == null || origin == destination) return -1.0;

        double mu = Math.max(1e-6, getBodyMu(star));
        double hohmannTof = getHohmannTof(star, origin, destination, root, departureTime);
        if (hohmannTof <= 0.0) return -1.0;

        double minPeriapsis = Math.max(0.05, star.spriteSize() * 0.5);
        OrbitalMechanics.OrbitalState srcStateDep = OrbitalMechanics.resolveWorldState(root, origin, departureTime);
        OrbitalMechanics.OrbitalState starAtDep = OrbitalMechanics.resolveWorldState(root, star, departureTime);
        if (srcStateDep == null || starAtDep == null) return -1.0;

        double r1x0 = srcStateDep.x() - starAtDep.x();
        double r1y0 = srcStateDep.y() - starAtDep.y();
        double vsrcX0 = srcStateDep.vx() - starAtDep.vx();
        double vsrcY0 = srcStateDep.vy() - starAtDep.vy();

        MutableLambertSolution progradeSolution = new MutableLambertSolution();
        MutableLambertSolution retrogradeSolution = new MutableLambertSolution();
        MutableLambertEvaluation progradeEvaluation = new MutableLambertEvaluation();
        MutableLambertEvaluation retrogradeEvaluation = new MutableLambertEvaluation();

        int nScan = 64;
        double bestTof = -1.0;
        double bestDv = -1.0;

        for (int i = 0; i < nScan; i++) {
            double frac = 0.1 + (3.0 - 0.1) * i / (nScan - 1);
            double tof = hohmannTof * frac;
            if (tof <= 0.0) continue;

            OrbitalMechanics.OrbitalState dstState = OrbitalMechanics
                .resolveWorldState(root, destination, departureTime + tof);
            OrbitalMechanics.OrbitalState starAtArr = OrbitalMechanics
                .resolveWorldState(root, star, departureTime + tof);
            if (dstState == null || starAtArr == null) continue;

            double r1x = r1x0;
            double r1y = r1y0;
            double r2x = dstState.x() - starAtArr.x();
            double r2y = dstState.y() - starAtArr.y();
            double vsrcX = vsrcX0;
            double vsrcY = vsrcY0;
            double vdstX = dstState.vx() - starAtArr.vx();
            double vdstY = dstState.vy() - starAtArr.vy();

            double crossZ = r1x * r2y - r1y * r2x;
            double r1mag = Math.hypot(r1x, r1y);
            double r2mag = Math.hypot(r2x, r2y);
            double sinDth = Math.abs(crossZ) / Math.max(1e-20, r1mag * r2mag);
            if (sinDth < 1e-3) continue;

            boolean hasPrograde = evalLambertInto(
                r1x,
                r1y,
                r2x,
                r2y,
                tof,
                mu,
                true,
                vsrcX,
                vsrcY,
                vdstX,
                vdstY,
                minPeriapsis,
                progradeSolution,
                progradeEvaluation);
            boolean hasRetrograde = evalLambertInto(
                r1x,
                r1y,
                r2x,
                r2y,
                tof,
                mu,
                false,
                vsrcX,
                vsrcY,
                vdstX,
                vdstY,
                minPeriapsis,
                retrogradeSolution,
                retrogradeEvaluation);

            MutableLambertEvaluation selectedEvaluation;
            if (hasPrograde
                && (!hasRetrograde || progradeEvaluation.totalDeltaV() <= retrogradeEvaluation.totalDeltaV())) {
                selectedEvaluation = progradeEvaluation;
            } else if (hasRetrograde) {
                selectedEvaluation = retrogradeEvaluation;
            } else {
                continue;
            }

            double totalDv = selectedEvaluation.totalDeltaV();
            if (totalDv <= dvLimit && (bestTof < 0.0 || tof < bestTof)) {
                bestTof = tof;
                bestDv = totalDv;
                // TOF scan is ascending, so first valid candidate is already the minimum-TOF one.
                break;
            }
        }

        return bestDv;
    }

    private static double getHohmannTof(OrbitalCelestialBody star, OrbitalCelestialBody source,
        OrbitalCelestialBody dest, OrbitalCelestialBody root, double time) {
        OrbitalMechanics.OrbitalState starState = OrbitalMechanics.resolveWorldState(root, star, time);
        OrbitalMechanics.OrbitalState srcState = OrbitalMechanics.resolveWorldState(root, source, time);
        OrbitalMechanics.OrbitalState dstState = OrbitalMechanics.resolveWorldState(root, dest, time);
        if (starState == null || srcState == null || dstState == null) return 100.0;
        double r1 = Math.hypot(srcState.x() - starState.x(), srcState.y() - starState.y());
        double r2 = Math.hypot(dstState.x() - starState.x(), dstState.y() - starState.y());
        double mu = Math.max(1e-6, getBodyMu(star));
        double sma = (r1 + r2) * 0.5;
        return Math.PI * Math.sqrt(sma * sma * sma / mu);
    }

    /**
     * Computes periapsis distance for an orbit defined by position and velocity.
     * Works for elliptic, parabolic and hyperbolic orbits.
     */
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

    /**
     * Evaluates one Lambert candidate and returns [dvDep, dvCap, totalDv] or null if invalid.
     * Rejects orbits whose periapsis falls below minPeriapsis.
     */
    private static boolean evalLambertInto(double r1x, double r1y, double r2x, double r2y, double tof, double mu,
        boolean prograde, double vsrcX, double vsrcY, double vdstX, double vdstY, double minPeriapsis,
        MutableLambertSolution solutionOut, MutableLambertEvaluation evaluationOut) {
        if (solutionOut == null || evaluationOut == null) return false;
        evaluationOut.clear();
        if (!solveLambertInto(r1x, r1y, r2x, r2y, tof, mu, prograde, solutionOut)) return false;
        double peri = computePeriapsis(
            r1x,
            r1y,
            solutionOut.departureVelocityX(),
            solutionOut.departureVelocityY(),
            mu);
        if (peri < minPeriapsis) return false;
        double dvDep = Math.hypot(solutionOut.departureVelocityX() - vsrcX, solutionOut.departureVelocityY() - vsrcY);
        double dvCap = Math.hypot(vdstX - solutionOut.arrivalVelocityX(), vdstY - solutionOut.arrivalVelocityY());
        evaluationOut.set(dvDep, dvCap);
        return true;
    }

    // -----------------------------------------------------------------------
    // updatePreview (called from OrbitalView)
    // -----------------------------------------------------------------------

    public static void updatePreview(OrbitalTransferSimulatorState state, OrbitalCelestialBody root,
        double globalTime) {
        if (state == null || !state.isOpen()) return;
        OrbitalCelestialBody origin = state.originBody();
        OrbitalCelestialBody dest = state.destinationBody();
        if (origin == null || dest == null || origin == dest) {
            state.clearPreview();
            return;
        }

        OrbitalCelestialBody star = findHostStar(root, origin);
        OrbitalCelestialBody destStar = findHostStar(root, dest);
        if (star == null || star != destStar) {
            state.clearPreview();
            return;
        }

        double mu = Math.max(1e-6, getBodyMu(star));
        double hohmannTof = getHohmannTof(star, origin, dest, root, globalTime);
        double sliderDv = state.sliderDv();
        if (sliderDv <= 0.0) {
            state.clearPreview();
            return;
        }
        TransferOptimizationMode optimizationMode = state.optimizationMode();

        // Minimum periapsis: reject transfers that skim through the star
        double minPeriapsis = Math.max(0.05, star.spriteSize() * 0.5);

        // Scan 64 TOFs from 0.1x to 3.0x Hohmann
        int nScan = 64;
        double bestTof = -1.0;
        double bestTotalDv = Double.POSITIVE_INFINITY;
        double bestAnchorX = 0, bestAnchorY = 0;
        double bestR1x = 0, bestR1y = 0, bestR2x = 0, bestR2y = 0;
        OrbitalMechanics.OrbitalState bestDstState = null;
        OrbitalMechanics.OrbitalState bestStarAtArr = null;
        MutableLambertSolution bestLambert = state.previewBestLambert();
        MutableLambertSolution progradeSolution = state.previewProgradeSolution();
        MutableLambertSolution retrogradeSolution = state.previewRetrogradeSolution();
        MutableLambertEvaluation progradeEvaluation = state.previewProgradeEvaluation();
        MutableLambertEvaluation retrogradeEvaluation = state.previewRetrogradeEvaluation();
        bestLambert.clear();

        // Cache departure state — it's the same for every TOF sample
        OrbitalMechanics.OrbitalState srcStateDep = OrbitalMechanics.resolveWorldState(root, origin, globalTime);
        OrbitalMechanics.OrbitalState starAtDep = OrbitalMechanics.resolveWorldState(root, star, globalTime);
        if (srcStateDep == null || starAtDep == null) {
            state.clearPreview();
            return;
        }
        double r1x0 = srcStateDep.x() - starAtDep.x();
        double r1y0 = srcStateDep.y() - starAtDep.y();
        double vsrcX0 = srcStateDep.vx() - starAtDep.vx();
        double vsrcY0 = srcStateDep.vy() - starAtDep.vy();

        for (int i = 0; i < nScan; i++) {
            double frac = 0.1 + (3.0 - 0.1) * i / (nScan - 1);
            double tof = hohmannTof * frac;
            if (tof <= 0.0) continue;

            OrbitalMechanics.OrbitalState dstState = OrbitalMechanics.resolveWorldState(root, dest, globalTime + tof);
            OrbitalMechanics.OrbitalState starAtArr = OrbitalMechanics.resolveWorldState(root, star, globalTime + tof);
            if (dstState == null || starAtArr == null) continue;

            double r1x = r1x0;
            double r1y = r1y0;
            double r2x = dstState.x() - starAtArr.x();
            double r2y = dstState.y() - starAtArr.y();
            double vsrcX = vsrcX0;
            double vsrcY = vsrcY0;
            double vdstX = dstState.vx() - starAtArr.vx();
            double vdstY = dstState.vy() - starAtArr.vy();

            // Near-180° (and near-0°) singularity guard:
            // crossZ / (r1*r2) = sin(Δθ), which approaches 0 at both 0° and 180°.
            // At exactly 180°, the orbit plane is undefined in 2D — skip these cases.
            double crossZ = r1x * r2y - r1y * r2x;
            double r1mag = Math.hypot(r1x, r1y);
            double r2mag = Math.hypot(r2x, r2y);
            double sinDth = Math.abs(crossZ) / Math.max(1e-20, r1mag * r2mag);
            if (sinDth < 1e-3) continue;

            // Try both prograde and retrograde; pick whichever has lower dV
            boolean hasPrograde = evalLambertInto(
                r1x,
                r1y,
                r2x,
                r2y,
                tof,
                mu,
                true,
                vsrcX,
                vsrcY,
                vdstX,
                vdstY,
                minPeriapsis,
                progradeSolution,
                progradeEvaluation);
            boolean hasRetrograde = evalLambertInto(
                r1x,
                r1y,
                r2x,
                r2y,
                tof,
                mu,
                false,
                vsrcX,
                vsrcY,
                vdstX,
                vdstY,
                minPeriapsis,
                retrogradeSolution,
                retrogradeEvaluation);

            MutableLambertSolution selectedSolution;
            MutableLambertEvaluation selectedEvaluation;
            if (hasPrograde
                && (!hasRetrograde || progradeEvaluation.totalDeltaV() <= retrogradeEvaluation.totalDeltaV())) {
                selectedSolution = progradeSolution;
                selectedEvaluation = progradeEvaluation;
            } else if (hasRetrograde) {
                selectedSolution = retrogradeSolution;
                selectedEvaluation = retrogradeEvaluation;
            } else {
                continue;
            }

            double totalDv = selectedEvaluation.totalDeltaV();
            boolean acceptCandidate = false;
            if (totalDv <= sliderDv) {
                if (optimizationMode == TransferOptimizationMode.MIN_TOF) {
                    acceptCandidate = (bestTof < 0 || tof < bestTof);
                } else {
                    acceptCandidate = (bestTof < 0 || totalDv < bestTotalDv
                        || (Math.abs(totalDv - bestTotalDv) < 1e-9 && tof < bestTof));
                }
            }

            if (acceptCandidate) {
                bestTof = tof;
                bestTotalDv = totalDv;
                bestLambert.set(
                    selectedSolution.departureVelocityX(),
                    selectedSolution.departureVelocityY(),
                    selectedSolution.arrivalVelocityX(),
                    selectedSolution.arrivalVelocityY());
                bestAnchorX = starAtDep.x();
                bestAnchorY = starAtDep.y();
                bestR1x = r1x;
                bestR1y = r1y;
                bestR2x = r2x;
                bestR2y = r2y;
                bestDstState = dstState;
                bestStarAtArr = starAtArr;
                if (optimizationMode == TransferOptimizationMode.MIN_TOF) {
                    // TOF scan is ascending, so first valid candidate is already the minimum-TOF one.
                    break;
                }
            }
        }

        if (!bestLambert.valid() || bestTof < 0) {
            state.clearPreview();
            return;
        }

        // Calculate dV details for display
        double dvDep = 0, dvCap = 0;
        if (srcStateDep != null && bestDstState != null && starAtDep != null && bestStarAtArr != null) {
            double vsrcX = srcStateDep.vx() - starAtDep.vx();
            double vsrcY = srcStateDep.vy() - starAtDep.vy();
            double vdstX = bestDstState.vx() - bestStarAtArr.vx();
            double vdstY = bestDstState.vy() - bestStarAtArr.vy();
            dvDep = Math.hypot(bestLambert.departureVelocityX() - vsrcX, bestLambert.departureVelocityY() - vsrcY);
            dvCap = Math.hypot(vdstX - bestLambert.arrivalVelocityX(), vdstY - bestLambert.arrivalVelocityY());
        }

        state.ensurePreviewCapacity(PREVIEW_TRAJECTORY_SAMPLES);
        int previewPointCount = sampleTransferArcInto(
            bestAnchorX,
            bestAnchorY,
            bestR1x,
            bestR1y,
            bestLambert.departureVelocityX(),
            bestLambert.departureVelocityY(),
            bestTof,
            mu,
            state.previewXs(),
            state.previewYs(),
            PREVIEW_TRAJECTORY_SAMPLES);

        state.setPreview(previewPointCount, bestTof, dvDep + dvCap, dvDep, dvCap);
    }

    // -----------------------------------------------------------------------
    // OrbitalTransferState
    // -----------------------------------------------------------------------

    public static final class OrbitalTransferState {

        private final List<InterplanetaryTransferJob> transfers = new ArrayList<>();
        private int version = 0;
        private InterplanetaryTransferJob hoveredTransfer = null;
        private int hoverX = 0;
        private int hoverY = 0;

        List<InterplanetaryTransferJob> transfers() {
            return transfers;
        }

        int version() {
            return version;
        }

        InterplanetaryTransferJob hoveredTransfer() {
            return hoveredTransfer;
        }

        int hoverX() {
            return hoverX;
        }

        int hoverY() {
            return hoverY;
        }

        void addTransfer(InterplanetaryTransferJob transfer) {
            if (transfer == null) return;
            transfers.add(transfer);
            version++;
        }

        void updateHoveredTransfer(InterplanetaryTransferJob transfer, int mouseX, int mouseY) {
            hoveredTransfer = transfer;
            hoverX = mouseX;
            hoverY = mouseY;
        }

        void pruneFinishedTransfers(double currentTime) {
            if (transfers.isEmpty()) return;
            if (transfers.removeIf(t -> t.isFinished(currentTime))) {
                version++;
                if (hoveredTransfer != null && hoveredTransfer.isFinished(currentTime)) hoveredTransfer = null;
            }
        }
    }

    // -----------------------------------------------------------------------
    // OrbitalTransferSupport
    // -----------------------------------------------------------------------

    public static final class OrbitalTransferSupport {

        private static final double DEFAULT_TRANSFER_DURATION = 72.0;
        private static final int TRAJECTORY_SAMPLES = 96;

        InterplanetaryTransferJob createTransferJob(OrbitalCelestialBody root, OrbitalCelestialBody sourceBody,
            OrbitalCelestialBody destinationBody, String transferName, String inventorySummary, double departureTime) {
            return createTransferJob(
                root,
                sourceBody,
                destinationBody,
                transferName,
                inventorySummary,
                departureTime,
                getTransferDuration(sourceBody, destinationBody));
        }

        InterplanetaryTransferJob createTransferJob(OrbitalCelestialBody root, OrbitalCelestialBody sourceBody,
            OrbitalCelestialBody destinationBody, String transferName, String inventorySummary, double departureTime,
            double duration) {
            if (root == null || sourceBody == null || destinationBody == null) return null;
            OrbitalCelestialBody star = findHostStar(root, sourceBody);
            OrbitalCelestialBody destStar = findHostStar(root, destinationBody);
            if (star == null || star != destStar) return null;

            double tof = Math.max(1.0, duration);
            double mu = Math.max(1e-6, getBodyMu(star));

            OrbitalMechanics.OrbitalState starAtDep = OrbitalMechanics.resolveWorldState(root, star, departureTime);
            OrbitalMechanics.OrbitalState srcState = OrbitalMechanics
                .resolveWorldState(root, sourceBody, departureTime);
            OrbitalMechanics.OrbitalState dstState = OrbitalMechanics
                .resolveWorldState(root, destinationBody, departureTime + tof);
            OrbitalMechanics.OrbitalState starAtArr = OrbitalMechanics
                .resolveWorldState(root, star, departureTime + tof);
            if (starAtDep == null || srcState == null || dstState == null || starAtArr == null) return null;

            double r1x = srcState.x() - starAtDep.x();
            double r1y = srcState.y() - starAtDep.y();
            double r2x = dstState.x() - starAtArr.x();
            double r2y = dstState.y() - starAtArr.y();

            MutableLambertSolution sol = new MutableLambertSolution();
            if (!solveLambertInto(r1x, r1y, r2x, r2y, tof, mu, true, sol)) {
                if (!solveLambertInto(r1x, r1y, r2x, r2y, tof, mu, false, sol)) sol.clear();
            }

            double[] trajectoryXs;
            double[] trajectoryYs;
            int trajectoryPointCount;
            if (sol.valid()) {
                trajectoryXs = new double[TRAJECTORY_SAMPLES];
                trajectoryYs = new double[TRAJECTORY_SAMPLES];
                trajectoryPointCount = sampleTransferArcInto(
                    starAtDep.x(),
                    starAtDep.y(),
                    r1x,
                    r1y,
                    sol.departureVelocityX(),
                    sol.departureVelocityY(),
                    tof,
                    mu,
                    trajectoryXs,
                    trajectoryYs,
                    TRAJECTORY_SAMPLES);
            } else {
                // Fallback: linear interpolation
                trajectoryXs = new double[TRAJECTORY_SAMPLES];
                trajectoryYs = new double[TRAJECTORY_SAMPLES];
                trajectoryPointCount = TRAJECTORY_SAMPLES;
                for (int i = 0; i < TRAJECTORY_SAMPLES; i++) {
                    double frac = i / (double) (TRAJECTORY_SAMPLES - 1);
                    trajectoryXs[i] = srcState.x() + (dstState.x() - srcState.x()) * frac;
                    trajectoryYs[i] = srcState.y() + (dstState.y() - srcState.y()) * frac;
                }
            }

            String id = sourceBody.id() + "->" + destinationBody.id() + "@" + Math.round(departureTime * 1000.0);
            String inv = (inventorySummary == null || inventorySummary.isEmpty()) ? "Empty" : inventorySummary;
            return new InterplanetaryTransferJob(
                id,
                transferName,
                inv,
                root,
                sourceBody,
                destinationBody,
                star,
                departureTime,
                departureTime + tof,
                trajectoryXs,
                trajectoryYs,
                trajectoryPointCount);
        }

        double getTransferDuration(OrbitalCelestialBody sourceBody, OrbitalCelestialBody destinationBody) {
            if (sourceBody == null || destinationBody == null
                || sourceBody.orbitalParams() == null
                || destinationBody.orbitalParams() == null) {
                return DEFAULT_TRANSFER_DURATION;
            }
            double sourceRadius = sourceBody.orbitalParams()
                .semiMajorAxis();
            double destinationRadius = destinationBody.orbitalParams()
                .semiMajorAxis();
            double orbitDistance = Math.abs(sourceRadius - destinationRadius);
            return DEFAULT_TRANSFER_DURATION + orbitDistance * 18.0;
        }

        double getTransferDurationForSpeedFactor(OrbitalCelestialBody root, OrbitalCelestialBody sourceBody,
            OrbitalCelestialBody destinationBody, double departureTime, double speedFactor) {
            if (root == null || sourceBody == null || destinationBody == null || speedFactor <= 0.0) {
                return getTransferDuration(sourceBody, destinationBody);
            }
            OrbitalCelestialBody star = findHostStar(root, sourceBody);
            if (star == null) return getTransferDuration(sourceBody, destinationBody);
            double hohmann = getHohmannTof(star, sourceBody, destinationBody, root, departureTime);
            return Math.max(1.0, hohmann / Math.max(0.05, speedFactor));
        }
    }

    // -----------------------------------------------------------------------
    // OrbitalTransferRenderer
    // -----------------------------------------------------------------------

    public static final class OrbitalTransferRenderer {

        public interface Callbacks {

            float worldToScreenX(double worldX);

            float worldToScreenY(double worldY);

            double[] getWorldPosition(OrbitalCelestialBody body);
        }

        private static final int PATH_COLOR = EnumColors.MAP_COLOR_TRANSFER_PATH.getColor();
        private static final int PREVIEW_PATH_COLOR = EnumColors.MAP_COLOR_TRANSFER_PREVIEW_PATH.getColor();
        private static final float DOT_HALF_SIZE = 4.0f;
        private static final float DOT_HIT_RADIUS = 7.0f;

        private final Callbacks callbacks;
        private final MutableTransferPoint transferPoint = new MutableTransferPoint();

        public OrbitalTransferRenderer(Callbacks callbacks) {
            this.callbacks = callbacks;
        }

        void drawTransferPaths(OrbitalTransferState state, double currentTime, float alpha) {
            if (state.transfers()
                .isEmpty() || alpha <= 0.01f) return;
            state.pruneFinishedTransfers(currentTime);
            GlStateManager.disableTexture2D();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            for (InterplanetaryTransferJob transfer : state.transfers()) {
                drawTransferPath(transfer, alpha);
            }
            GlStateManager.color(1f, 1f, 1f, 1f);
            GlStateManager.enableTexture2D();
        }

        void drawTransferDots(OrbitalTransferState state, double currentTime, float alpha) {
            if (state.transfers()
                .isEmpty() || alpha <= 0.01f) return;
            GlStateManager.disableDepth();
            GlStateManager.disableTexture2D();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            for (InterplanetaryTransferJob transfer : state.transfers()) {
                drawTransferDot(transfer, currentTime, alpha);
            }
            GlStateManager.color(1f, 1f, 1f, 1f);
            GlStateManager.enableTexture2D();
            GlStateManager.enableDepth();
        }

        void drawPreviewTrajectory(OrbitalTransferSimulatorState state, float alpha) {
            if (state == null || !state.isOpen() || alpha <= 0.01f || !state.hasPreview()) return;

            GlStateManager.disableTexture2D();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

            int color = withAlpha(PREVIEW_PATH_COLOR, alpha);
            applyColor(color);
            GL11.glLineWidth(1.8f);
            GL11.glBegin(GL11.GL_LINE_STRIP);
            for (int i = 0; i < state.previewPointCount(); i++) {
                GL11.glVertex2f(
                    callbacks.worldToScreenX(state.previewX(i)),
                    callbacks.worldToScreenY(state.previewY(i)));
            }
            GL11.glEnd();
            GL11.glLineWidth(1f);

            GlStateManager.color(1f, 1f, 1f, 1f);
            GlStateManager.enableTexture2D();
        }

        InterplanetaryTransferJob findHoveredTransfer(OrbitalTransferState state, double currentTime, float mouseX,
            float mouseY) {
            for (int i = state.transfers()
                .size() - 1; i >= 0; i--) {
                InterplanetaryTransferJob transfer = state.transfers()
                    .get(i);
                if (!writeCurrentTransferPoint(transfer, currentTime, transferPoint) || !transferPoint.valid()) {
                    continue;
                }
                float sx = callbacks.worldToScreenX(transferPoint.worldX());
                float sy = callbacks.worldToScreenY(transferPoint.worldY());
                float dx = mouseX - sx;
                float dy = mouseY - sy;
                if (dx * dx + dy * dy <= DOT_HIT_RADIUS * DOT_HIT_RADIUS) return transfer;
            }
            return null;
        }

        private void drawTransferPath(InterplanetaryTransferJob transfer, float alpha) {
            if (transfer.trajectoryPointCount() <= 0) return;
            int color = withAlpha(PATH_COLOR, alpha);
            applyColor(color);
            GL11.glLineWidth(1.8f);
            GL11.glBegin(GL11.GL_LINE_STRIP);
            for (int i = 0; i < transfer.trajectoryPointCount(); i++) {
                GL11.glVertex2f(
                    callbacks.worldToScreenX(transfer.trajectoryXs()[i]),
                    callbacks.worldToScreenY(transfer.trajectoryYs()[i]));
            }
            GL11.glEnd();
            GL11.glLineWidth(1f);
        }

        private void drawTransferDot(InterplanetaryTransferJob transfer, double currentTime, float alpha) {
            if (!writeCurrentTransferPoint(transfer, currentTime, transferPoint) || !transferPoint.valid()) return;
            float sx = callbacks.worldToScreenX(transferPoint.worldX());
            float sy = callbacks.worldToScreenY(transferPoint.worldY());
            GlStateManager.color(1f, 1f, 1f, alpha);
            Gui.drawRect(
                Math.round(sx - DOT_HALF_SIZE),
                Math.round(sy - DOT_HALF_SIZE),
                Math.round(sx + DOT_HALF_SIZE),
                Math.round(sy + DOT_HALF_SIZE),
                EnumColors.MAP_COLOR_TRANSFER_DOT.getColor());
        }

        private void applyColor(int color) {
            float r = ((color >> 16) & 0xFF) / 255f;
            float g = ((color >> 8) & 0xFF) / 255f;
            float b = (color & 0xFF) / 255f;
            float a = ((color >> 24) & 0xFF) / 255f;
            GlStateManager.color(r, g, b, a);
        }

        private int withAlpha(int color, float alpha) {
            int a = Math.max(0, Math.min(255, (int) (((color >> 24) & 0xFF) * alpha)));
            return (color & 0x00FFFFFF) | (a << 24);
        }
    }

    // -----------------------------------------------------------------------
    // TransferPickMode
    // -----------------------------------------------------------------------

    public enum TransferPickMode {
        NONE,
        ORIGIN,
        DESTINATION
    }

    public enum TransferOptimizationMode {

        MIN_TOF,
        MIN_DV;

        TransferOptimizationMode toggled() {
            return this == MIN_TOF ? MIN_DV : MIN_TOF;
        }
    }

    // -----------------------------------------------------------------------
    // OrbitalTransferSimulatorState
    // -----------------------------------------------------------------------

    public static final class OrbitalTransferSimulatorState {

        private boolean open = false;
        private TransferPickMode pickMode = TransferPickMode.NONE;
        private TransferOptimizationMode optimizationMode = TransferOptimizationMode.MIN_TOF;
        private OrbitalCelestialBody originBody = null;
        private OrbitalCelestialBody destinationBody = null;
        private int version = 0;

        // New dV fields
        private double maxDv = 5.0;
        private double sliderDv = 0.0;

        // Preview data
        private double[] previewXs = new double[0];
        private double[] previewYs = new double[0];
        private int previewPointCount = 0;
        private double previewTof = 0.0;
        private double previewTotalDv = 0.0;
        private double previewDvDep = 0.0;
        private double previewDvCap = 0.0;
        private final MutableLambertSolution previewBestLambert = new MutableLambertSolution();
        private final MutableLambertSolution previewProgradeSolution = new MutableLambertSolution();
        private final MutableLambertSolution previewRetrogradeSolution = new MutableLambertSolution();
        private final MutableLambertEvaluation previewProgradeEvaluation = new MutableLambertEvaluation();
        private final MutableLambertEvaluation previewRetrogradeEvaluation = new MutableLambertEvaluation();

        boolean isOpen() {
            return open;
        }

        TransferPickMode pickMode() {
            return pickMode;
        }

        OrbitalCelestialBody originBody() {
            return originBody;
        }

        OrbitalCelestialBody destinationBody() {
            return destinationBody;
        }

        TransferOptimizationMode optimizationMode() {
            return optimizationMode;
        }

        void toggleOptimizationMode() {
            optimizationMode = optimizationMode.toggled();
            clearPreview();
            version++;
        }

        int version() {
            return version;
        }

        boolean isWaitingForPick() {
            return open && pickMode != TransferPickMode.NONE;
        }

        void open() {
            if (open) return;
            open = true;
            pickMode = TransferPickMode.NONE;
            version++;
        }

        void close() {
            if (!open && pickMode == TransferPickMode.NONE && originBody == null && destinationBody == null) return;
            open = false;
            pickMode = TransferPickMode.NONE;
            originBody = null;
            destinationBody = null;
            clearPreview();
            version++;
        }

        void beginPick(TransferPickMode mode) {
            if (!open || mode == null) return;
            pickMode = mode;
            version++;
        }

        void cancelPick() {
            if (pickMode == TransferPickMode.NONE) return;
            pickMode = TransferPickMode.NONE;
            version++;
        }

        void resetSelection() {
            if (pickMode == TransferPickMode.NONE && originBody == null && destinationBody == null) return;
            pickMode = TransferPickMode.NONE;
            originBody = null;
            destinationBody = null;
            clearPreview();
            version++;
        }

        void applyPickedBody(OrbitalCelestialBody body) {
            if (!open || pickMode == TransferPickMode.NONE || body == null) return;
            if (pickMode == TransferPickMode.ORIGIN) originBody = body;
            else if (pickMode == TransferPickMode.DESTINATION) destinationBody = body;
            pickMode = TransferPickMode.NONE;
            clearPreview();
            version++;
        }

        double maxDv() {
            return maxDv;
        }

        void setMaxDv(double value) {
            this.maxDv = Math.max(0.001, value);
            if (sliderDv > this.maxDv) sliderDv = this.maxDv;
            version++;
        }

        double sliderDv() {
            return sliderDv;
        }

        void setSliderDv(double value) {
            this.sliderDv = Math.max(0.0, Math.min(maxDv, value));
        }

        void ensurePreviewCapacity(int count) {
            int required = Math.max(0, count);
            if (previewXs.length >= required && previewYs.length >= required) return;
            previewXs = new double[required];
            previewYs = new double[required];
        }

        void setPreview(int pointCount, double tof, double totalDv, double dvDep, double dvCap) {
            this.previewPointCount = Math.max(0, pointCount);
            this.previewTof = tof;
            this.previewTotalDv = totalDv;
            this.previewDvDep = dvDep;
            this.previewDvCap = dvCap;
        }

        void clearPreview() {
            this.previewPointCount = 0;
            this.previewTof = 0.0;
            this.previewTotalDv = 0.0;
            this.previewDvDep = 0.0;
            this.previewDvCap = 0.0;
        }

        boolean hasPreview() {
            return previewPointCount > 0;
        }

        int previewPointCount() {
            return previewPointCount;
        }

        double[] previewXs() {
            return previewXs;
        }

        double[] previewYs() {
            return previewYs;
        }

        double previewX(int index) {
            return previewXs[index];
        }

        double previewY(int index) {
            return previewYs[index];
        }

        double previewTof() {
            return previewTof;
        }

        double previewTotalDv() {
            return previewTotalDv;
        }

        double previewDvDep() {
            return previewDvDep;
        }

        double previewDvCap() {
            return previewDvCap;
        }

        MutableLambertSolution previewBestLambert() {
            return previewBestLambert;
        }

        MutableLambertSolution previewProgradeSolution() {
            return previewProgradeSolution;
        }

        MutableLambertSolution previewRetrogradeSolution() {
            return previewRetrogradeSolution;
        }

        MutableLambertEvaluation previewProgradeEvaluation() {
            return previewProgradeEvaluation;
        }

        MutableLambertEvaluation previewRetrogradeEvaluation() {
            return previewRetrogradeEvaluation;
        }
    }

    // -----------------------------------------------------------------------
    // OrbitalTransferSimulatorWidget
    // -----------------------------------------------------------------------

    public static final class OrbitalTransferSimulatorWidget extends ParentWidget<OrbitalTransferSimulatorWidget> {

        public interface Callbacks {

            void closeTransferSimulator();

            void beginTransferPick(TransferPickMode pickMode);

            OrbitalCelestialBody getCurrentSystemBody();

            void onPreviewNeeded();

            void dispatchTransfer();

            void runLambertStressTest();

            double getTimeScale();
        }

        private static final int PANEL_LEFT = 28;
        private static final int PANEL_TOP = 80;
        private static final int PANEL_WIDTH = 300;
        private static final int PANEL_HEIGHT = 260;
        private static final int CONTENT_X = 16;
        private static final int PICK_BUTTON_WIDTH = 96;
        private static final int PICK_BUTTON_HEIGHT = 20;
        private static final int INPUT_FIELD_WIDTH = 80;
        private static final int INPUT_FIELD_HEIGHT = 18;

        private final OrbitalTransferSimulatorState state;
        private final Callbacks callbacks;
        private final TextFieldWidget maxDvField;
        private int panelLeft = PANEL_LEFT;
        private int panelTop = PANEL_TOP;
        private int lastVersion = -1;

        // Track the DoubleValue for the slider
        private DoubleValue sliderValue;

        // Dynamic text widgets replaced with cached strings for IKey.dynamic
        private String cachedDvLabel = "dV: --";
        private String cachedTof = "TOF: --";
        private String cachedDepDv = "Dep dV: --";
        private String cachedCapDv = "Cap dV: --";
        private String cachedTotalDv = "Total dV: --";

        private double lastSliderDv = -1;
        private double lastPreviewTof = -1;
        private double lastPreviewDvDep = -1;
        private double lastPreviewDvCap = -1;
        private double lastPreviewTotalDv = -1;
        private boolean lastHasPreview = false;
        private double lastTimeScale = -1;

        OrbitalTransferSimulatorWidget(OrbitalTransferSimulatorState state, Callbacks callbacks) {
            this.state = state;
            this.callbacks = callbacks;
            this.maxDvField = createInputField("Max dV");
            maxDvField.setText(String.valueOf(state.maxDv()));
            this.sliderValue = new DoubleValue(state.sliderDv());
            setEnabled(false);
        }

        boolean isPointInPanel(int localX, int localY) {
            return state.isOpen() && localX >= panelLeft
                && localX <= panelLeft + PANEL_WIDTH
                && localY >= panelTop
                && localY <= panelTop + PANEL_HEIGHT;
        }

        @Override
        public void onUpdate() {
            super.onUpdate();
            if (!state.isOpen()) {
                if (isEnabled()) {
                    removeAll();
                    scheduleResize();
                }
                lastVersion = -1;
                setEnabled(false);
                return;
            }
            setEnabled(true);
            if (state.version() != lastVersion) {
                rebuildChildren();
                lastVersion = state.version();
                lastSliderDv = -1;
                lastPreviewTof = -1;
            }
            // Poll slider value for changes
            if (sliderValue != null) {
                double newVal = sliderValue.getDoubleValue();
                double oldVal = state.sliderDv();
                if (Math.abs(newVal - oldVal) > 1e-9) {
                    state.setSliderDv(newVal);
                    callbacks.onPreviewNeeded();
                }
            }

            // Update dynamic widgets without allocations
            double currentSliderDv = state.sliderDv();
            if (Math.abs(currentSliderDv - lastSliderDv) > 1e-6) {
                cachedDvLabel = "dV: " + formatFixed1(currentSliderDv);
                lastSliderDv = currentSliderDv;
            }

            boolean hasPreview = state.hasPreview();
            if (hasPreview != lastHasPreview) {
                lastHasPreview = hasPreview;
                lastPreviewTof = -1; // force update
            }

            double currentTimeScale = callbacks.getTimeScale();

            if (hasPreview) {
                if (Math.abs(state.previewTof() - lastPreviewTof) > 1e-6
                    || Math.abs(currentTimeScale - lastTimeScale) > 1e-6) {
                    cachedTof = "TOF: " + formatFixed1(state.previewTof() / Math.max(1e-6, currentTimeScale)) + "s";
                    lastPreviewTof = state.previewTof();
                    lastTimeScale = currentTimeScale;
                }
                if (Math.abs(state.previewDvDep() - lastPreviewDvDep) > 1e-6) {
                    cachedDepDv = "Dep dV: " + formatFixed1(state.previewDvDep());
                    lastPreviewDvDep = state.previewDvDep();
                }
                if (Math.abs(state.previewDvCap() - lastPreviewDvCap) > 1e-6) {
                    cachedCapDv = "Cap dV: " + formatFixed1(state.previewDvCap());
                    lastPreviewDvCap = state.previewDvCap();
                }
                if (Math.abs(state.previewTotalDv() - lastPreviewTotalDv) > 1e-6) {
                    cachedTotalDv = "Total dV: " + formatFixed1(state.previewTotalDv());
                    lastPreviewTotalDv = state.previewTotalDv();
                }
            } else {
                if (lastPreviewTof != -2) {
                    cachedTof = "TOF: --";
                    cachedDepDv = "Dep dV: --";
                    cachedCapDv = "Cap dV: --";
                    cachedTotalDv = "Total dV: --";
                    lastPreviewTof = -2;
                }
            }
        }

        @Override
        public void drawBackground(ModularGuiContext context, WidgetThemeEntry widgetTheme) {
            if (!state.isOpen()) return;
            super.drawBackground(context, widgetTheme);
        }

        private void rebuildChildren() {
            String dvText = maxDvField.getText();
            removeAll();

            panelLeft = PANEL_LEFT;
            panelTop = PANEL_TOP;
            ParentWidget<?> panel = new ParentWidget<>().pos(panelLeft, panelTop)
                .size(PANEL_WIDTH, PANEL_HEIGHT);

            // Background
            PassiveLayer backgroundLayer = new PassiveLayer().pos(0, 0)
                .widthRel(1f)
                .heightRel(1f)
                .background(
                    drawable(
                        (ctx, x, y, w, h) -> Gui
                            .drawRect(x, y, x + w, y + h, EnumColors.MAP_COLOR_MODAL_BG.getColor())));
            panel.child(backgroundLayer);
            panel.child(WidgetOutline.create(backgroundLayer, 3, EnumColors.MAP_COLOR_MODAL_ACCENT.getColor()));

            // Title
            panel.child(
                new TextWidget<>(IKey.str("Transfer Planner")).color(EnumColors.MAP_COLOR_TEXT_TITLE.getColor())
                    .shadow(true)
                    .pos(CONTENT_X, 12));

            // Close button
            panel.child(
                createButton(
                    "X",
                    EnumColors.MAP_COLOR_BTN_ENABLED_DEFAULT.getColor(),
                    EnumColors.MAP_COLOR_BTN_BORDER_ENABLED.getColor(),
                    callbacks::closeTransferSimulator).pos(PANEL_WIDTH - 30, 8)
                        .size(20, 18));

            // System row
            panel.child(
                createInfoRow(
                    "System:",
                    callbacks.getCurrentSystemBody() != null ? callbacks.getCurrentSystemBody()
                        .displayName() : "None",
                    36));

            // Origin row with pick button
            panel.child(createInfoRow("Origin:", formatBodyLabel(state.originBody(), "None"), 58));
            panel.child(
                createButton(
                    state.pickMode() == TransferPickMode.ORIGIN ? "Picking..." : "Pick",
                    EnumColors.MAP_COLOR_BTN_ENABLED_DEFAULT.getColor(),
                    EnumColors.MAP_COLOR_BTN_BORDER_ENABLED.getColor(),
                    () -> callbacks.beginTransferPick(TransferPickMode.ORIGIN)).pos(PANEL_WIDTH - 114, 54)
                        .size(PICK_BUTTON_WIDTH, PICK_BUTTON_HEIGHT));

            // Destination row with pick button
            panel.child(createInfoRow("Destination:", formatBodyLabel(state.destinationBody(), "None"), 82));
            panel.child(
                createButton(
                    state.pickMode() == TransferPickMode.DESTINATION ? "Picking..." : "Pick",
                    EnumColors.MAP_COLOR_BTN_ENABLED_DEFAULT.getColor(),
                    EnumColors.MAP_COLOR_BTN_BORDER_ENABLED.getColor(),
                    () -> callbacks.beginTransferPick(TransferPickMode.DESTINATION)).pos(PANEL_WIDTH - 114, 78)
                        .size(PICK_BUTTON_WIDTH, PICK_BUTTON_HEIGHT));

            // Separator
            panel.child(createSeparator(106));

            // Ship dV row: label + field + Set button
            panel.child(
                new TextWidget<>(IKey.str("Ship dV:")).color(EnumColors.MAP_COLOR_TEXT_SECTION.getColor())
                    .shadow(true)
                    .pos(CONTENT_X, 116));

            // dV field background
            panel.child(
                new PassiveLayer().pos(80, 112)
                    .size(INPUT_FIELD_WIDTH, INPUT_FIELD_HEIGHT)
                    .background(drawable((ctx, x, y, w, h) -> {
                        Gui.drawRect(x, y, x + w, y + h, EnumColors.MAP_COLOR_BTN_ENABLED_DEFAULT.getColor());
                        Gui.drawRect(x, y, x + w, y + 1, EnumColors.MAP_COLOR_BTN_BORDER_ENABLED.getColor());
                        Gui.drawRect(x, y + h - 1, x + w, y + h, EnumColors.MAP_COLOR_BTN_BORDER_ENABLED.getColor());
                        Gui.drawRect(x, y, x + 1, y + h, EnumColors.MAP_COLOR_BTN_BORDER_ENABLED.getColor());
                        Gui.drawRect(x + w - 1, y, x + w, y + h, EnumColors.MAP_COLOR_BTN_BORDER_ENABLED.getColor());
                    })));

            panel.child(
                createButton(
                    "Set",
                    EnumColors.MAP_COLOR_BTN_ENABLED_DEFAULT.getColor(),
                    EnumColors.MAP_COLOR_BTN_BORDER_ENABLED.getColor(),
                    this::applyMaxDv).pos(168, 112)
                        .size(36, 18));

            panel.child(
                createButton(
                    formatOptimizationModeLabel(state.optimizationMode()),
                    EnumColors.MAP_COLOR_BTN_ENABLED_DEFAULT.getColor(),
                    EnumColors.MAP_COLOR_BTN_BORDER_ENABLED.getColor(),
                    this::toggleOptimizationMode).pos(210, 112)
                        .size(74, 18));

            // Slider row
            double maxDvVal = state.maxDv();
            sliderValue = new DoubleValue(Math.max(0.0, Math.min(maxDvVal, state.sliderDv())));
            SliderWidget slider = new SliderWidget().value(sliderValue)
                .bounds(0.0, maxDvVal)
                .pos(CONTENT_X, 138)
                .size(PANEL_WIDTH - CONTENT_X * 2, 14);
            panel.child(slider);

            // dV label (dynamic: show current sliderDv)
            panel.child(
                new TextWidget<>(IKey.dynamic(() -> cachedDvLabel)).color(EnumColors.MAP_COLOR_TEXT_BODY.getColor())
                    .shadow(true)
                    .pos(CONTENT_X, 158));

            // Separator before info
            panel.child(createSeparator(172));

            // Preview info rows
            panel.child(
                new TextWidget<>(IKey.dynamic(() -> cachedTof)).color(EnumColors.MAP_COLOR_TEXT_BODY.getColor())
                    .shadow(true)
                    .pos(CONTENT_X, 180));

            panel.child(
                new TextWidget<>(IKey.dynamic(() -> cachedDepDv)).color(EnumColors.MAP_COLOR_TEXT_BODY.getColor())
                    .shadow(true)
                    .pos(CONTENT_X, 194));

            panel.child(
                new TextWidget<>(IKey.dynamic(() -> cachedCapDv)).color(EnumColors.MAP_COLOR_TEXT_BODY.getColor())
                    .shadow(true)
                    .pos(CONTENT_X, 208));

            panel.child(
                new TextWidget<>(IKey.dynamic(() -> cachedTotalDv)).color(EnumColors.MAP_COLOR_TEXT_BODY.getColor())
                    .shadow(true)
                    .pos(CONTENT_X, 222));

            // Dispatch button at bottom
            panel.child(
                createButton(
                    "Stress",
                    EnumColors.MAP_COLOR_BTN_ENABLED_DEFAULT.getColor(),
                    EnumColors.MAP_COLOR_BTN_BORDER_ENABLED.getColor(),
                    callbacks::runLambertStressTest).pos(CONTENT_X, 240)
                        .size(72, 16));

            panel.child(
                createButton(
                    "Dispatch Transfer",
                    EnumColors.MAP_COLOR_BTN_ENABLED_DEFAULT.getColor(),
                    EnumColors.MAP_COLOR_BTN_BORDER_ENABLED.getColor(),
                    callbacks::dispatchTransfer).pos(CONTENT_X + 80, 240)
                        .size(PANEL_WIDTH - 32 - 80, 16));

            // Position maxDv text field
            maxDvField.setText(dvText);
            maxDvField.pos(panelLeft + 80 + 4, panelTop + 112 + 3)
                .size(INPUT_FIELD_WIDTH - 8, INPUT_FIELD_HEIGHT - 6);

            child(panel);
            child(maxDvField);
            scheduleResize();

            // Trigger preview if both bodies are selected and dV is set
            if (state.originBody() != null && state.destinationBody() != null && state.sliderDv() > 0.0) {
                callbacks.onPreviewNeeded();
            }
        }

        private void applyMaxDv() {
            String text = maxDvField.getText()
                .trim()
                .replace(',', '.');
            if (text.isEmpty()) return;
            try {
                double val = Double.parseDouble(text);
                if (val > 0.0) {
                    state.setMaxDv(val);
                    // Start slider at half of max dV for immediate feedback
                    if (state.sliderDv() <= 0.0) state.setSliderDv(val * 0.5);
                    callbacks.onPreviewNeeded();
                    rebuildChildren();
                    lastVersion = state.version();
                }
            } catch (NumberFormatException ignored) {}
        }

        private void toggleOptimizationMode() {
            state.toggleOptimizationMode();
            callbacks.onPreviewNeeded();
            rebuildChildren();
            lastVersion = state.version();
        }

        private String formatOptimizationModeLabel(TransferOptimizationMode mode) {
            if (mode == TransferOptimizationMode.MIN_DV) return "Mode: MIN dV";
            return "Mode: MIN TOF";
        }

        private TextFieldWidget createInputField(String hintText) {
            return new TextFieldWidget().setMaxLength(12)
                .setTextColor(EnumColors.MAP_COLOR_TEXT_BODY.getColor())
                .hintText(hintText)
                .hintColor(EnumColors.MAP_COLOR_TEXT_MUTED.getColor())
                .setFocusOnGuiOpen(false);
        }

        private ParentWidget<?> createInfoRow(String label, String value, int y) {
            ParentWidget<?> row = new ParentWidget<>().pos(CONTENT_X, y)
                .size(PANEL_WIDTH - CONTENT_X * 2, 20);
            row.child(
                new TextWidget<>(IKey.str(label)).color(EnumColors.MAP_COLOR_TEXT_SECTION.getColor())
                    .shadow(true)
                    .pos(0, 0));
            row.child(
                new TextWidget<>(IKey.str(value)).color(EnumColors.MAP_COLOR_TEXT_BODY.getColor())
                    .shadow(true)
                    .pos(72, 0)
                    .width(PANEL_WIDTH - CONTENT_X * 2 - 72 - PICK_BUTTON_WIDTH - 12));
            return row;
        }

        private PassiveLayer createSeparator(int y) {
            return new PassiveLayer().pos(CONTENT_X, y)
                .size(PANEL_WIDTH - CONTENT_X * 2, 1)
                .background(
                    drawable(
                        (ctx, x, yy, w, h) -> Gui
                            .drawRect(x, yy, x + w, yy + 1, EnumColors.MAP_COLOR_BTN_BORDER_DISABLED.getColor())));
        }

        private ButtonWidget<?> createButton(String label, int backgroundColor, int borderColor, Runnable onClick) {
            return new ButtonWidget<>().background(drawable((ctx, x, y, w, h) -> {
                Gui.drawRect(x, y, x + w, y + h, backgroundColor);
                Gui.drawRect(x, y, x + w, y + 1, borderColor);
                Gui.drawRect(x, y + h - 1, x + w, y + h, borderColor);
                Gui.drawRect(x, y, x + 1, y + h, borderColor);
                Gui.drawRect(x + w - 1, y, x + w, y + h, borderColor);
            }))
                .hoverBackground(drawable((ctx, x, y, w, h) -> {
                    Gui.drawRect(x, y, x + w, y + h, EnumColors.MAP_COLOR_BTN_ENABLED_HOVERED.getColor());
                    Gui.drawRect(x, y, x + w, y + 1, borderColor);
                    Gui.drawRect(x, y + h - 1, x + w, y + h, borderColor);
                    Gui.drawRect(x, y, x + 1, y + h, borderColor);
                    Gui.drawRect(x + w - 1, y, x + w, y + h, borderColor);
                }))
                .overlay(drawable((ctx, x, y, w, h) -> {
                    net.minecraft.client.gui.FontRenderer fr = net.minecraft.client.Minecraft
                        .getMinecraft().fontRenderer;
                    int textW = fr.getStringWidth(label);
                    fr.drawStringWithShadow(
                        label,
                        x + (w - textW) / 2,
                        y + (h - fr.FONT_HEIGHT) / 2 + 1,
                        EnumColors.MAP_COLOR_TEXT_BODY.getColor());
                }))
                .onMousePressed(btn -> {
                    if (btn != 0) return false;
                    onClick.run();
                    return true;
                });
        }

        private String formatBodyLabel(OrbitalCelestialBody body, String fallback) {
            return body == null ? fallback : body.displayName();
        }

        private IDrawable drawable(DrawCommand cmd) {
            return (ctx, x, y, w, h, theme) -> cmd.draw(ctx, x, y, w, h);
        }
    }

    // -----------------------------------------------------------------------
    // OrbitalTransferTooltipWidget
    // -----------------------------------------------------------------------

    public static final class OrbitalTransferTooltipWidget extends ParentWidget<OrbitalTransferTooltipWidget> {

        public interface Callbacks {

            InterplanetaryTransferJob getHoveredTransfer();

            int getTooltipMouseX();

            int getTooltipMouseY();

            double getCurrentTime();

            double getTimeScale();
        }

        private static final int PANEL_WIDTH = 148;
        private static final int PANEL_HEIGHT = 60;
        private static final int PADDING = 10;

        private final Callbacks callbacks;
        private InterplanetaryTransferJob activeTransfer;
        private ParentWidget<?> rootPanel;

        private String cachedTitle = "";
        private String cachedProgress = "";
        private String cachedRemaining = "";

        private long lastProgress = -1;
        private long lastRemaining = -1;

        public OrbitalTransferTooltipWidget(Callbacks callbacks) {
            this.callbacks = callbacks;
            setEnabled(false);
        }

        @Override
        public void onUpdate() {
            super.onUpdate();
            InterplanetaryTransferJob transfer = callbacks.getHoveredTransfer();
            if (transfer == null) {
                if (isEnabled()) {
                    removeAll();
                    scheduleResize();
                }
                activeTransfer = null;
                rootPanel = null;
                setEnabled(false);
                return;
            }
            setEnabled(true);
            if (transfer != activeTransfer) {
                cachedTitle = transfer.displayName();
                rebuildChildren(transfer);
                activeTransfer = transfer;
                lastProgress = -1;
                lastRemaining = -1;
            } else {
                updateTooltipPosition();
            }

            if (activeTransfer != null) {
                double pct = activeTransfer.progress(callbacks.getCurrentTime()) * 100.0;
                long currentProgress = Math.round(pct);
                if (currentProgress != lastProgress) {
                    cachedProgress = "Progress: " + currentProgress + "%";
                    lastProgress = currentProgress;
                }

                double timeScale = Math.max(1e-6, callbacks.getTimeScale());
                double remainingSec = Math.max(0.0, activeTransfer.arrivalTime() - callbacks.getCurrentTime())
                    / timeScale;
                long currentRemaining = Math.round(remainingSec * 10.0);
                if (currentRemaining != lastRemaining) {
                    cachedRemaining = "Remaining: " + formatFixed1(remainingSec) + "s";
                    lastRemaining = currentRemaining;
                }
            }
        }

        private void rebuildChildren(InterplanetaryTransferJob transfer) {
            removeAll();
            rootPanel = new ParentWidget<>().size(PANEL_WIDTH, PANEL_HEIGHT);
            PassiveLayer backgroundLayer = new PassiveLayer().pos(0, 0)
                .widthRel(1f)
                .heightRel(1f)
                .background(
                    drawable(
                        (ctx, x, y, w, h) -> Gui
                            .drawRect(x, y, x + w, y + h, EnumColors.MAP_COLOR_TRANSFER_TOOLTIP_BG.getColor())));
            rootPanel.child(backgroundLayer);
            rootPanel.child(WidgetOutline.create(backgroundLayer, 3, EnumColors.MAP_COLOR_MODAL_ACCENT.getColor()));

            rootPanel.child(
                new TextWidget<>(IKey.dynamic(() -> cachedTitle)).color(EnumColors.MAP_COLOR_TEXT_TITLE.getColor())
                    .shadow(true)
                    .pos(PADDING, 8));

            rootPanel.child(
                new TextWidget<>(IKey.dynamic(() -> cachedProgress)).color(EnumColors.MAP_COLOR_TEXT_BODY.getColor())
                    .shadow(true)
                    .pos(PADDING, 24));

            rootPanel.child(
                new TextWidget<>(IKey.dynamic(() -> cachedRemaining)).color(EnumColors.MAP_COLOR_TEXT_BODY.getColor())
                    .shadow(true)
                    .pos(PADDING, 38));

            updateTooltipPosition();
            child(rootPanel);
        }

        @Override
        public void drawBackground(ModularGuiContext context, WidgetThemeEntry widgetTheme) {
            if (!isEnabled()) return;
            super.drawBackground(context, widgetTheme);
        }

        private String formatProgress(InterplanetaryTransferJob transfer) {
            double pct = transfer.progress(callbacks.getCurrentTime()) * 100.0;
            return Math.round(pct) + "%";
        }

        private String formatRemaining(InterplanetaryTransferJob transfer) {
            double timeScale = Math.max(1e-6, callbacks.getTimeScale());
            double remaining = Math.max(0.0, transfer.arrivalTime() - callbacks.getCurrentTime()) / timeScale;
            return formatFixed1(remaining) + "s";
        }

        private void updateTooltipPosition() {
            if (rootPanel == null) return;
            int localMouseX = callbacks.getTooltipMouseX() - getArea().rx;
            int localMouseY = callbacks.getTooltipMouseY() - getArea().ry;
            int left = Math.max(8, localMouseX + 12);
            int top = Math.max(8, localMouseY - PANEL_HEIGHT / 2);
            if (left + PANEL_WIDTH > getArea().width - 8) left = Math.max(8, localMouseX - 12 - PANEL_WIDTH);
            if (top + PANEL_HEIGHT > getArea().height - 8) top = Math.max(8, getArea().height - 8 - PANEL_HEIGHT);
            rootPanel.pos(left, top);
        }

        private IDrawable drawable(DrawCommand cmd) {
            return (ctx, x, y, w, h, theme) -> cmd.draw(ctx, x, y, w, h);
        }
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    @FunctionalInterface
    private interface DrawCommand {

        void draw(GuiContext context, int x, int y, int width, int height);
    }

    private static String formatFixed1(double value) {
        return Long.toString(Math.round(value * 10.0) / 10L) + "." + Math.abs(Math.round(value * 10.0) % 10L);
    }

    private static final class PassiveLayer extends ParentWidget<PassiveLayer> {

        @Override
        public boolean canHover() {
            return false;
        }

        @Override
        public boolean canHoverThrough() {
            return true;
        }
    }
}
