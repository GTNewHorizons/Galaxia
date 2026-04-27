package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.orbital.LambertTransfer;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalMechanics;

public final class TransferScanner {

    public record ScanResult(double tof, double totalDv, double depDv, double r1x, double r1y, double r2x, double r2y,
        double anchorX, double anchorY, LambertTransfer.Solution solution, OrbitalMechanics.OrbitalState dstState,
        OrbitalMechanics.OrbitalState attractorAtArr) {

        private static final ScanResult INVALID = new ScanResult(
            -1,
            Double.MAX_VALUE,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            LambertTransfer.Solution.invalid(),
            null,
            null);

        public static ScanResult invalid() {
            return INVALID;
        }

        public boolean isValid() {
            return solution != null && solution.valid() && tof > 0;
        }
    }

    @FunctionalInterface
    public interface ScanAcceptor {

        boolean accept(ScanResult current, ScanResult best);
    }

    public static final int DEFAULT_SCAN_COUNT = 64;

    public static ScanResult scan(CelestialObject root, CelestialObject origin, CelestialObject dest,
        CelestialObject attractor, double departureTime, double minPeriapsis, ScanAcceptor acceptor) {
        return scan(root, origin, dest, attractor, departureTime, minPeriapsis, acceptor, DEFAULT_SCAN_COUNT);
    }

    public static ScanResult scan(CelestialObject root, CelestialObject origin, CelestialObject dest,
        CelestialObject attractor, double departureTime, double minPeriapsis, ScanAcceptor acceptor, int scanCount) {
        if (root == null || origin == null || dest == null || attractor == null) {
            return ScanResult.invalid();
        }

        double mu = Math.max(1e-6, attractor.mu());
        double hohmannTof = attractor.getHohmannTof(origin, dest, root, departureTime);
        if (hohmannTof <= 0.0) {
            return ScanResult.invalid();
        }

        OrbitalMechanics.OrbitalState srcStateDep = OrbitalMechanics.resolveWorldState(root, origin, departureTime);
        OrbitalMechanics.OrbitalState attractorAtDep = OrbitalMechanics
            .resolveWorldState(root, attractor, departureTime);
        if (srcStateDep == null || attractorAtDep == null) {
            return ScanResult.invalid();
        }

        double r1x0 = srcStateDep.x() - attractorAtDep.x();
        double r1y0 = srcStateDep.y() - attractorAtDep.y();
        double vsrcX0 = srcStateDep.vx() - attractorAtDep.vx();
        double vsrcY0 = srcStateDep.vy() - attractorAtDep.vy();

        ScanResult best = ScanResult.invalid();

        for (int i = 0; i < scanCount; i++) {
            double frac = 0.1 + (3.0 - 0.1) * i / (scanCount - 1);
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

            LambertTransfer.Solution bestSol;
            if (progSol.valid() && (!retSol.valid() || progSol.totalDv() <= retSol.totalDv())) {
                bestSol = progSol;
            } else if (retSol.valid()) {
                bestSol = retSol;
            } else {
                continue;
            }

            ScanResult current = new ScanResult(
                tof,
                bestSol.totalDv(),
                bestSol.depDv(),
                r1x0,
                r1y0,
                r2x,
                r2y,
                attractorAtDep.x(),
                attractorAtDep.y(),
                bestSol,
                dstState,
                attractorAtArr);

            if (acceptor.accept(current, best)) {
                best = current;
            }
        }

        return best;
    }
}
