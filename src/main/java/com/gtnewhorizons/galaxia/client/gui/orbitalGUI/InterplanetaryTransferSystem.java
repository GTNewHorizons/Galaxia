package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.client.gui.Gui;

import org.lwjgl.opengl.GL11;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.viewport.GuiContext;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.utils.GlStateManager;
import com.cleanroommc.modularui.value.DoubleValue;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.SliderWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.galaxia.orbitalGUI.Hierarchy.OrbitalCelestialBody;
import com.gtnewhorizons.galaxia.orbitalGUI.OrbitalMechanics;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectClass;
import com.gtnewhorizons.galaxia.utility.EnumColors;

// ---------------------------------------------------------------------------
// Package-level records
// ---------------------------------------------------------------------------

@Desugar
record TransferTrajectoryPoint(double worldX, double worldY) {}

@Desugar
record InterplanetaryTransferJob(String transferId, String displayName, String inventorySummary,
    OrbitalCelestialBody rootBody, OrbitalCelestialBody sourceBody, OrbitalCelestialBody destinationBody,
    OrbitalCelestialBody orbitAnchorBody, double departureTime, double arrivalTime,
    List<TransferTrajectoryPoint> trajectory) {

    public InterplanetaryTransferJob {
        transferId = transferId == null ? "" : transferId;
        displayName = displayName == null ? "" : displayName;
        inventorySummary = inventorySummary == null ? "Empty" : inventorySummary;
        trajectory = trajectory == null ? Collections.emptyList()
            : Collections.unmodifiableList(new ArrayList<>(trajectory));
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

    private InterplanetaryTransferSystem() {}

    // -----------------------------------------------------------------------
    // Public API: getCurrentTransferPoint
    // -----------------------------------------------------------------------

    public static TransferTrajectoryPoint getCurrentTransferPoint(InterplanetaryTransferJob transfer,
        double currentTime) {
        if (transfer == null) return null;
        List<TransferTrajectoryPoint> pts = transfer.trajectory();
        if (pts.isEmpty()) return new TransferTrajectoryPoint(0.0, 0.0);
        if (pts.size() == 1) return pts.get(0);
        double dep = transfer.departureTime();
        double arr = transfer.arrivalTime();
        double dur = Math.max(1e-12, arr - dep);
        double t = (currentTime - dep) / dur;
        if (t <= 0.0) return pts.get(0);
        if (t >= 1.0) return pts.get(pts.size() - 1);
        double idx = t * (pts.size() - 1);
        int lo = (int) idx;
        int hi = Math.min(lo + 1, pts.size() - 1);
        double frac = idx - lo;
        TransferTrajectoryPoint a = pts.get(lo);
        TransferTrajectoryPoint b = pts.get(hi);
        return new TransferTrajectoryPoint(a.worldX() + (b.worldX() - a.worldX()) * frac,
            a.worldY() + (b.worldY() - a.worldY()) * frac);
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
    static double[] solveLambert(double rx1, double ry1, double rx2, double ry2, double tof, double mu,
        boolean prograde) {
        if (tof <= 0.0 || mu <= 0.0) return null;

        // Step 1: Geometry
        double r1 = Math.hypot(rx1, ry1);
        double r2 = Math.hypot(rx2, ry2);
        if (r1 < 1e-10 || r2 < 1e-10) return null;

        double cdx = rx2 - rx1;
        double cdy = ry2 - ry1;
        double c = Math.hypot(cdx, cdy);
        if (c < 1e-10) return null;

        double s = (r1 + r2 + c) * 0.5;
        if (s < 1e-10) return null;

        double crossZ = rx1 * ry2 - ry1 * rx2;
        double cosDth = (rx1 * rx2 + ry1 * ry2) / (r1 * r2);
        cosDth = Math.max(-1.0, Math.min(1.0, cosDth));
        double dth = Math.acos(cosDth);

        boolean longWay = (prograde && crossZ < 0.0) || (!prograde && crossZ > 0.0);
        if (longWay) dth = 2.0 * Math.PI - dth;

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
        if (Math.abs(T - finalT) > 0.01 * Math.max(1e-10, T)) return null;

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

        return new double[] { vx1, vy1, vx2, vy2 };
    }

    static double tofNormalized(double x, double lambda) {
        double alpha = 2.0 * Math.acos(x);
        double sinHalfBeta = lambda * Math.sqrt(Math.max(0.0, 1.0 - x * x));
        sinHalfBeta = Math.max(-1.0, Math.min(1.0, sinHalfBeta));
        double beta = 2.0 * Math.asin(sinHalfBeta);
        double denom = Math.pow(Math.max(1e-30, 1.0 - x * x), 1.5);
        return ((alpha - Math.sin(alpha)) - (beta - Math.sin(beta))) / (2.0 * denom);
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
    static List<double[]> sampleTransferArc(double ax, double ay, double rx1, double ry1, double vx1, double vy1,
        double tof, double mu, int n) {
        if (n < 2) n = 2;
        List<double[]> pts = new ArrayList<>(n);
        OrbitalMechanics.OrbitalState state = new OrbitalMechanics.OrbitalState(rx1, ry1, vx1, vy1);
        double dt = tof / (n - 1);
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                state = OrbitalMechanics.propagateTwoBodyState(state, mu, dt);
                if (state == null) break;
            }
            pts.add(new double[] { ax + state.x(), ay + state.y() });
        }
        return pts;
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
        return Math.max(0.0, body.properties().standardGravitationalParameter());
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

        // Scan 64 TOFs from 0.1x to 3.0x Hohmann
        int nScan = 64;
        double bestTof = -1.0;
        double bestDv = Double.MAX_VALUE;
        double[] bestLambert = null;
        double bestVbodySrcX = 0, bestVbodySrcY = 0;
        double bestAnchorX = 0, bestAnchorY = 0;
        double bestR1x = 0, bestR1y = 0, bestR2x = 0, bestR2y = 0;

        OrbitalMechanics.OrbitalState starState = OrbitalMechanics.resolveWorldState(root, star, globalTime);
        if (starState == null) {
            state.clearPreview();
            return;
        }

        for (int i = 0; i < nScan; i++) {
            double frac = 0.1 + (3.0 - 0.1) * i / (nScan - 1);
            double tof = hohmannTof * frac;
            if (tof <= 0.0) continue;

            OrbitalMechanics.OrbitalState srcState = OrbitalMechanics.resolveWorldState(root, origin, globalTime);
            OrbitalMechanics.OrbitalState dstState = OrbitalMechanics
                .resolveWorldState(root, dest, globalTime + tof);
            OrbitalMechanics.OrbitalState starAtDep = OrbitalMechanics.resolveWorldState(root, star, globalTime);
            OrbitalMechanics.OrbitalState starAtArr = OrbitalMechanics.resolveWorldState(root, star, globalTime + tof);
            if (srcState == null || dstState == null || starAtDep == null || starAtArr == null) continue;

            // Positions relative to star
            double r1x = srcState.x() - starAtDep.x();
            double r1y = srcState.y() - starAtDep.y();
            double r2x = dstState.x() - starAtArr.x();
            double r2y = dstState.y() - starAtArr.y();

            // Velocity of origin body relative to star at departure
            double vsrcX = srcState.vx() - starAtDep.vx();
            double vsrcY = srcState.vy() - starAtDep.vy();
            // Velocity of dest body relative to star at arrival
            double vdstX = dstState.vx() - starAtArr.vx();
            double vdstY = dstState.vy() - starAtArr.vy();

            double[] sol = solveLambert(r1x, r1y, r2x, r2y, tof, mu, true);
            if (sol == null) {
                // Try retrograde
                sol = solveLambert(r1x, r1y, r2x, r2y, tof, mu, false);
            }
            if (sol == null) continue;

            // dV at departure: |v_lambert_dep - v_origin_body|
            double dvDepX = sol[0] - vsrcX;
            double dvDepY = sol[1] - vsrcY;
            double dvDep = Math.hypot(dvDepX, dvDepY);

            // dV at arrival: |v_dest_body - v_lambert_arr|
            double dvCapX = vdstX - sol[2];
            double dvCapY = vdstY - sol[3];
            double dvCap = Math.hypot(dvCapX, dvCapY);

            double totalDv = dvDep + dvCap;
            if (totalDv <= sliderDv && (bestTof < 0 || tof < bestTof)) {
                bestTof = tof;
                bestDv = totalDv;
                bestLambert = sol;
                bestVbodySrcX = vsrcX;
                bestVbodySrcY = vsrcY;
                bestAnchorX = starAtDep.x();
                bestAnchorY = starAtDep.y();
                bestR1x = r1x;
                bestR1y = r1y;
                bestR2x = r2x;
                bestR2y = r2y;
            }
        }

        if (bestLambert == null || bestTof < 0) {
            state.clearPreview();
            return;
        }

        // Calculate dV details for display
        OrbitalMechanics.OrbitalState srcStateF = OrbitalMechanics.resolveWorldState(root, origin, globalTime);
        OrbitalMechanics.OrbitalState dstStateF = OrbitalMechanics
            .resolveWorldState(root, dest, globalTime + bestTof);
        OrbitalMechanics.OrbitalState starAtDepF = OrbitalMechanics.resolveWorldState(root, star, globalTime);
        OrbitalMechanics.OrbitalState starAtArrF = OrbitalMechanics
            .resolveWorldState(root, star, globalTime + bestTof);

        double dvDep = 0, dvCap = 0;
        if (srcStateF != null && dstStateF != null && starAtDepF != null && starAtArrF != null) {
            double vsrcX = srcStateF.vx() - starAtDepF.vx();
            double vsrcY = srcStateF.vy() - starAtDepF.vy();
            double vdstX = dstStateF.vx() - starAtArrF.vx();
            double vdstY = dstStateF.vy() - starAtArrF.vy();
            dvDep = Math.hypot(bestLambert[0] - vsrcX, bestLambert[1] - vsrcY);
            dvCap = Math.hypot(vdstX - bestLambert[2], vdstY - bestLambert[3]);
        }

        // Sample trajectory: 96 points
        List<double[]> pts = sampleTransferArc(bestAnchorX, bestAnchorY, bestR1x, bestR1y, bestLambert[0],
            bestLambert[1], bestTof, mu, 96);

        state.setPreview(pts, bestTof, dvDep + dvCap, dvDep, dvCap);
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
            OrbitalMechanics.OrbitalState srcState = OrbitalMechanics.resolveWorldState(root, sourceBody, departureTime);
            OrbitalMechanics.OrbitalState dstState = OrbitalMechanics
                .resolveWorldState(root, destinationBody, departureTime + tof);
            OrbitalMechanics.OrbitalState starAtArr = OrbitalMechanics
                .resolveWorldState(root, star, departureTime + tof);
            if (starAtDep == null || srcState == null || dstState == null || starAtArr == null) return null;

            double r1x = srcState.x() - starAtDep.x();
            double r1y = srcState.y() - starAtDep.y();
            double r2x = dstState.x() - starAtArr.x();
            double r2y = dstState.y() - starAtArr.y();

            double[] sol = solveLambert(r1x, r1y, r2x, r2y, tof, mu, true);
            if (sol == null) sol = solveLambert(r1x, r1y, r2x, r2y, tof, mu, false);

            List<TransferTrajectoryPoint> trajectory;
            if (sol != null) {
                List<double[]> pts = sampleTransferArc(
                    starAtDep.x(),
                    starAtDep.y(),
                    r1x,
                    r1y,
                    sol[0],
                    sol[1],
                    tof,
                    mu,
                    TRAJECTORY_SAMPLES);
                trajectory = new ArrayList<>(pts.size());
                for (double[] pt : pts) {
                    trajectory.add(new TransferTrajectoryPoint(pt[0], pt[1]));
                }
            } else {
                // Fallback: linear interpolation
                trajectory = new ArrayList<>(TRAJECTORY_SAMPLES);
                for (int i = 0; i < TRAJECTORY_SAMPLES; i++) {
                    double frac = i / (double) (TRAJECTORY_SAMPLES - 1);
                    double wx = srcState.x() + (dstState.x() - srcState.x()) * frac;
                    double wy = srcState.y() + (dstState.y() - srcState.y()) * frac;
                    trajectory.add(new TransferTrajectoryPoint(wx, wy));
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
                trajectory);
        }

        double getTransferDuration(OrbitalCelestialBody sourceBody, OrbitalCelestialBody destinationBody) {
            if (sourceBody == null || destinationBody == null || sourceBody.orbitalParams() == null
                || destinationBody.orbitalParams() == null) {
                return DEFAULT_TRANSFER_DURATION;
            }
            double sourceRadius = sourceBody.orbitalParams().semiMajorAxis();
            double destinationRadius = destinationBody.orbitalParams().semiMajorAxis();
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

        private static final int PATH_COLOR = 0x886DDCFF;
        private static final int PREVIEW_PATH_COLOR = 0xAA00FFCC;
        private static final float DOT_HALF_SIZE = 4.0f;
        private static final float DOT_HIT_RADIUS = 7.0f;

        private final Callbacks callbacks;

        public OrbitalTransferRenderer(Callbacks callbacks) {
            this.callbacks = callbacks;
        }

        void drawTransferPaths(OrbitalTransferState state, double currentTime, float alpha) {
            if (state.transfers().isEmpty() || alpha <= 0.01f) return;
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
            if (state.transfers().isEmpty() || alpha <= 0.01f) return;
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
            if (state == null || !state.isOpen() || alpha <= 0.01f) return;
            List<double[]> pts = state.previewPoints();
            if (pts == null || pts.isEmpty()) return;

            GlStateManager.disableTexture2D();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

            int color = withAlpha(PREVIEW_PATH_COLOR, alpha);
            applyColor(color);
            GL11.glLineWidth(1.8f);
            GL11.glBegin(GL11.GL_LINE_STRIP);
            for (double[] pt : pts) {
                GL11.glVertex2f(callbacks.worldToScreenX(pt[0]), callbacks.worldToScreenY(pt[1]));
            }
            GL11.glEnd();
            GL11.glLineWidth(1f);

            GlStateManager.color(1f, 1f, 1f, 1f);
            GlStateManager.enableTexture2D();
        }

        InterplanetaryTransferJob findHoveredTransfer(OrbitalTransferState state, double currentTime, float mouseX,
            float mouseY) {
            for (int i = state.transfers().size() - 1; i >= 0; i--) {
                InterplanetaryTransferJob transfer = state.transfers().get(i);
                TransferTrajectoryPoint point = getCurrentTransferPoint(transfer, currentTime);
                if (point == null) continue;
                float sx = callbacks.worldToScreenX(point.worldX());
                float sy = callbacks.worldToScreenY(point.worldY());
                float dx = mouseX - sx;
                float dy = mouseY - sy;
                if (dx * dx + dy * dy <= DOT_HIT_RADIUS * DOT_HIT_RADIUS) return transfer;
            }
            return null;
        }

        private void drawTransferPath(InterplanetaryTransferJob transfer, float alpha) {
            List<TransferTrajectoryPoint> pts = transfer.trajectory();
            if (pts.isEmpty()) return;
            int color = withAlpha(PATH_COLOR, alpha);
            applyColor(color);
            GL11.glLineWidth(1.8f);
            GL11.glBegin(GL11.GL_LINE_STRIP);
            for (TransferTrajectoryPoint pt : pts) {
                GL11.glVertex2f(callbacks.worldToScreenX(pt.worldX()), callbacks.worldToScreenY(pt.worldY()));
            }
            GL11.glEnd();
            GL11.glLineWidth(1f);
        }

        private void drawTransferDot(InterplanetaryTransferJob transfer, double currentTime, float alpha) {
            TransferTrajectoryPoint point = getCurrentTransferPoint(transfer, currentTime);
            if (point == null) return;
            float sx = callbacks.worldToScreenX(point.worldX());
            float sy = callbacks.worldToScreenY(point.worldY());
            GlStateManager.color(1f, 1f, 1f, alpha);
            Gui.drawRect(
                Math.round(sx - DOT_HALF_SIZE),
                Math.round(sy - DOT_HALF_SIZE),
                Math.round(sx + DOT_HALF_SIZE),
                Math.round(sy + DOT_HALF_SIZE),
                0xFFFFFFFF);
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

    // -----------------------------------------------------------------------
    // OrbitalTransferSimulatorState
    // -----------------------------------------------------------------------

    public static final class OrbitalTransferSimulatorState {

        private boolean open = false;
        private TransferPickMode pickMode = TransferPickMode.NONE;
        private OrbitalCelestialBody originBody = null;
        private OrbitalCelestialBody destinationBody = null;
        private int version = 0;

        // New dV fields
        private double maxDv = 5.0;
        private double sliderDv = 0.0;

        // Preview data
        private List<double[]> previewPoints = Collections.emptyList();
        private double previewTof = 0.0;
        private double previewTotalDv = 0.0;
        private double previewDvDep = 0.0;
        private double previewDvCap = 0.0;

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

        void setPreview(List<double[]> pts, double tof, double totalDv, double dvDep, double dvCap) {
            this.previewPoints = pts == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(pts));
            this.previewTof = tof;
            this.previewTotalDv = totalDv;
            this.previewDvDep = dvDep;
            this.previewDvCap = dvCap;
        }

        void clearPreview() {
            this.previewPoints = Collections.emptyList();
            this.previewTof = 0.0;
            this.previewTotalDv = 0.0;
            this.previewDvDep = 0.0;
            this.previewDvCap = 0.0;
        }

        List<double[]> previewPoints() {
            return previewPoints;
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

        OrbitalTransferSimulatorWidget(OrbitalTransferSimulatorState state, Callbacks callbacks) {
            this.state = state;
            this.callbacks = callbacks;
            this.maxDvField = createInputField("Max dV");
            maxDvField.setText(String.valueOf(state.maxDv()));
            this.sliderValue = new DoubleValue(state.sliderDv());
            setEnabled(false);
            child(maxDvField);
        }

        boolean isPointInPanel(int localX, int localY) {
            return state.isOpen()
                && localX >= panelLeft
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
                    maxDvField.pos(0, -1000).size(120, 20);
                    child(maxDvField);
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
            ParentWidget<?> panel = new ParentWidget<>().pos(panelLeft, panelTop).size(PANEL_WIDTH, PANEL_HEIGHT);

            // Background
            PassiveLayer backgroundLayer = new PassiveLayer().pos(0, 0)
                .widthRel(1f)
                .heightRel(1f)
                .background(drawable((ctx, x, y, w, h) -> Gui.drawRect(x, y, x + w, y + h,
                    EnumColors.MAP_COLOR_MODAL_BG.getColor())));
            panel.child(backgroundLayer);
            panel.child(WidgetOutline.create(backgroundLayer, 3, EnumColors.MAP_COLOR_MODAL_ACCENT.getColor()));

            // Title
            panel.child(
                new TextWidget<>("Transfer Planner").color(EnumColors.MAP_COLOR_TEXT_TITLE.getColor())
                    .shadow(true)
                    .pos(CONTENT_X, 12));

            // Close button
            panel.child(
                createButton("X", EnumColors.MAP_COLOR_BTN_ENABLED_DEFAULT.getColor(),
                    EnumColors.MAP_COLOR_BTN_BORDER_ENABLED.getColor(),
                    callbacks::closeTransferSimulator).pos(PANEL_WIDTH - 30, 8).size(20, 18));

            // System row
            panel.child(createInfoRow(
                "System:",
                callbacks.getCurrentSystemBody() != null ? callbacks.getCurrentSystemBody().displayName() : "None",
                36));

            // Origin row with pick button
            panel.child(
                createInfoRow("Origin:", formatBodyLabel(state.originBody(), "None"), 58));
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
                new TextWidget<>("Ship dV:").color(EnumColors.MAP_COLOR_TEXT_SECTION.getColor())
                    .shadow(true)
                    .pos(CONTENT_X, 116));

            // dV field background
            panel.child(
                new PassiveLayer().pos(80, 112).size(INPUT_FIELD_WIDTH, INPUT_FIELD_HEIGHT)
                    .background(drawable((ctx, x, y, w, h) -> {
                        Gui.drawRect(x, y, x + w, y + h, EnumColors.MAP_COLOR_BTN_ENABLED_DEFAULT.getColor());
                        Gui.drawRect(x, y, x + w, y + 1, EnumColors.MAP_COLOR_BTN_BORDER_ENABLED.getColor());
                        Gui.drawRect(x, y + h - 1, x + w, y + h, EnumColors.MAP_COLOR_BTN_BORDER_ENABLED.getColor());
                        Gui.drawRect(x, y, x + 1, y + h, EnumColors.MAP_COLOR_BTN_BORDER_ENABLED.getColor());
                        Gui.drawRect(x + w - 1, y, x + w, y + h, EnumColors.MAP_COLOR_BTN_BORDER_ENABLED.getColor());
                    })));

            panel.child(
                createButton("Set", EnumColors.MAP_COLOR_BTN_ENABLED_DEFAULT.getColor(),
                    EnumColors.MAP_COLOR_BTN_BORDER_ENABLED.getColor(), this::applyMaxDv).pos(168, 112).size(36, 18));

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
                new TextWidget<>(IKey.dynamic(() -> String.format("dV: %.3f", state.sliderDv())))
                    .color(EnumColors.MAP_COLOR_TEXT_BODY.getColor())
                    .shadow(true)
                    .pos(CONTENT_X, 158));

            // Separator before info
            panel.child(createSeparator(172));

            // Preview info rows
            panel.child(
                new TextWidget<>(IKey.dynamic(
                    () -> !state.previewPoints().isEmpty() ? String.format("TOF: %.1fs", state.previewTof()) : "TOF: --"))
                        .color(EnumColors.MAP_COLOR_TEXT_BODY.getColor())
                        .shadow(true)
                        .pos(CONTENT_X, 180));
            panel.child(
                new TextWidget<>(IKey.dynamic(
                    () -> !state.previewPoints().isEmpty() ? String.format("Dep dV: %.3f", state.previewDvDep())
                        : "Dep dV: --"))
                            .color(EnumColors.MAP_COLOR_TEXT_BODY.getColor())
                            .shadow(true)
                            .pos(CONTENT_X, 194));
            panel.child(
                new TextWidget<>(IKey.dynamic(
                    () -> !state.previewPoints().isEmpty() ? String.format("Cap dV: %.3f", state.previewDvCap())
                        : "Cap dV: --"))
                            .color(EnumColors.MAP_COLOR_TEXT_BODY.getColor())
                            .shadow(true)
                            .pos(CONTENT_X, 208));
            panel.child(
                new TextWidget<>(IKey.dynamic(
                    () -> !state.previewPoints().isEmpty() ? String.format("Total dV: %.3f", state.previewTotalDv())
                        : "Total dV: --"))
                            .color(EnumColors.MAP_COLOR_TEXT_BODY.getColor())
                            .shadow(true)
                            .pos(CONTENT_X, 222));

            // Close button at bottom
            panel.child(
                createButton("Close", EnumColors.MAP_COLOR_BTN_ENABLED_DEFAULT.getColor(),
                    EnumColors.MAP_COLOR_BTN_BORDER_ENABLED.getColor(),
                    callbacks::closeTransferSimulator).pos(CONTENT_X, 240).size(PANEL_WIDTH - 32, 16));

            // Position maxDv text field
            maxDvField.setText(dvText);
            maxDvField.pos(panelLeft + 80 + 4, panelTop + 112 + 3).size(INPUT_FIELD_WIDTH - 8, INPUT_FIELD_HEIGHT - 6);

            child(panel);
            child(maxDvField);
            scheduleResize();

            // Trigger preview if both bodies are selected and dV is set
            if (state.originBody() != null && state.destinationBody() != null && state.sliderDv() > 0.0) {
                callbacks.onPreviewNeeded();
            }
        }

        private void applyMaxDv() {
            String text = maxDvField.getText().trim().replace(',', '.');
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

        private TextFieldWidget createInputField(String hintText) {
            return new TextFieldWidget().setMaxLength(12)
                .setTextColor(EnumColors.MAP_COLOR_TEXT_BODY.getColor())
                .hintText(hintText)
                .hintColor(EnumColors.MAP_COLOR_TEXT_MUTED.getColor())
                .setFocusOnGuiOpen(false);
        }

        private ParentWidget<?> createInfoRow(String label, String value, int y) {
            ParentWidget<?> row = new ParentWidget<>().pos(CONTENT_X, y).size(PANEL_WIDTH - CONTENT_X * 2, 20);
            row.child(
                new TextWidget<>(label).color(EnumColors.MAP_COLOR_TEXT_SECTION.getColor())
                    .shadow(true)
                    .pos(0, 0));
            row.child(
                new TextWidget<>(value).color(EnumColors.MAP_COLOR_TEXT_BODY.getColor())
                    .shadow(true)
                    .pos(72, 0)
                    .width(PANEL_WIDTH - CONTENT_X * 2 - 72 - PICK_BUTTON_WIDTH - 12));
            return row;
        }

        private PassiveLayer createSeparator(int y) {
            return new PassiveLayer().pos(CONTENT_X, y)
                .size(PANEL_WIDTH - CONTENT_X * 2, 1)
                .background(drawable((ctx, x, yy, w, h) -> Gui.drawRect(
                    x,
                    yy,
                    x + w,
                    yy + 1,
                    EnumColors.MAP_COLOR_BTN_BORDER_DISABLED.getColor())));
        }

        private ButtonWidget<?> createButton(String label, int backgroundColor, int borderColor, Runnable onClick) {
            return new ButtonWidget<>()
                .background(drawable((ctx, x, y, w, h) -> {
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
                .overlay(
                    IKey.str(label)
                        .alignment(Alignment.Center)
                        .color(EnumColors.MAP_COLOR_TEXT_BODY.getColor())
                        .shadow(true))
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
        private String lastSignature = "";

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
                lastSignature = "";
                setEnabled(false);
                return;
            }
            setEnabled(true);
            String sig = buildSignature(transfer);
            if (!sig.equals(lastSignature)) {
                rebuildChildren(transfer);
                lastSignature = sig;
            }
        }

        private String buildSignature(InterplanetaryTransferJob transfer) {
            return transfer.transferId() + '|' + formatProgress(transfer) + '|' + callbacks.getTooltipMouseX() + '|'
                + callbacks.getTooltipMouseY();
        }

        private void rebuildChildren(InterplanetaryTransferJob transfer) {
            removeAll();
            int localMouseX = callbacks.getTooltipMouseX() - getArea().rx;
            int localMouseY = callbacks.getTooltipMouseY() - getArea().ry;
            int left = Math.max(8, localMouseX + 12);
            int top = Math.max(8, localMouseY - PANEL_HEIGHT / 2);
            if (left + PANEL_WIDTH > getArea().width - 8) left = Math.max(8, localMouseX - 12 - PANEL_WIDTH);
            if (top + PANEL_HEIGHT > getArea().height - 8) top = Math.max(8, getArea().height - 8 - PANEL_HEIGHT);

            ParentWidget<?> root = new ParentWidget<>().pos(left, top).size(PANEL_WIDTH, PANEL_HEIGHT);
            PassiveLayer backgroundLayer = new PassiveLayer().pos(0, 0)
                .widthRel(1f)
                .heightRel(1f)
                .background(drawable((ctx, x, y, w, h) -> Gui.drawRect(x, y, x + w, y + h, 0xEE162133)));
            root.child(backgroundLayer);
            root.child(WidgetOutline.create(backgroundLayer, 3, EnumColors.MAP_COLOR_MODAL_ACCENT.getColor()));
            root.child(
                new TextWidget<>(transfer.displayName()).color(EnumColors.MAP_COLOR_TEXT_TITLE.getColor())
                    .shadow(true)
                    .pos(PADDING, 8));
            root.child(
                new TextWidget<>("Progress: " + formatProgress(transfer))
                    .color(EnumColors.MAP_COLOR_TEXT_BODY.getColor())
                    .shadow(true)
                    .pos(PADDING, 24));
            root.child(
                new TextWidget<>("Remaining: " + formatRemaining(transfer))
                    .color(EnumColors.MAP_COLOR_TEXT_BODY.getColor())
                    .shadow(true)
                    .pos(PADDING, 38));
            child(root);
        }

        @Override
        public void drawBackground(ModularGuiContext context, WidgetThemeEntry widgetTheme) {
            if (!isEnabled()) return;
            super.drawBackground(context, widgetTheme);
        }

        private String formatProgress(InterplanetaryTransferJob transfer) {
            double pct = transfer.progress(callbacks.getCurrentTime()) * 100.0;
            return String.format("%.0f%%", pct);
        }

        private String formatRemaining(InterplanetaryTransferJob transfer) {
            double timeScale = Math.max(1e-6, callbacks.getTimeScale());
            double remaining = Math.max(0.0, transfer.arrivalTime() - callbacks.getCurrentTime()) / timeScale;
            return String.format("%.1fs", remaining);
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
