package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.orbital.LambertTransfer;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalMechanics;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalTransferPlanner;

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

    public static final class ScanProfiler {

        private long hohmannNanos;
        private long departureResolveNanos;
        private long arrivalResolveNanos;
        private long geometryNanos;
        private long lambertNanos;
        private long acceptNanos;
        private int candidateCount;
        private int lambertPairCount;

        public long hohmannNanos() {
            return hohmannNanos;
        }

        public long departureResolveNanos() {
            return departureResolveNanos;
        }

        public long arrivalResolveNanos() {
            return arrivalResolveNanos;
        }

        public long geometryNanos() {
            return geometryNanos;
        }

        public long lambertNanos() {
            return lambertNanos;
        }

        public long acceptNanos() {
            return acceptNanos;
        }

        public int candidateCount() {
            return candidateCount;
        }

        public int lambertPairCount() {
            return lambertPairCount;
        }

        private void addHohmannNanos(long nanos) {
            hohmannNanos += Math.max(0L, nanos);
        }

        private void addDepartureResolveNanos(long nanos) {
            departureResolveNanos += Math.max(0L, nanos);
        }

        private void addArrivalResolveNanos(long nanos) {
            arrivalResolveNanos += Math.max(0L, nanos);
        }

        private void addGeometryNanos(long nanos) {
            geometryNanos += Math.max(0L, nanos);
        }

        private void addLambertNanos(long nanos) {
            lambertNanos += Math.max(0L, nanos);
        }

        private void addAcceptNanos(long nanos) {
            acceptNanos += Math.max(0L, nanos);
        }

        private void incrementCandidateCount() {
            candidateCount++;
        }

        private void incrementLambertPairCount() {
            lambertPairCount++;
        }
    }

    public static final int DEFAULT_SCAN_COUNT = 64;

    public static ScanResult scan(CelestialObject root, CelestialObject origin, CelestialObject dest,
        CelestialObject attractor, double departureTime, double minPeriapsis, ScanAcceptor acceptor) {
        return scan(root, origin, dest, attractor, departureTime, minPeriapsis, acceptor, DEFAULT_SCAN_COUNT);
    }

    public static ScanResult scan(CelestialObject root, CelestialObject origin, CelestialObject dest,
        CelestialObject attractor, double departureTime, double minPeriapsis, ScanAcceptor acceptor, int scanCount) {
        return scan(root, origin, dest, attractor, departureTime, minPeriapsis, acceptor, scanCount, null);
    }

    public static ScanResult scan(CelestialObject root, CelestialObject origin, CelestialObject dest,
        CelestialObject attractor, double departureTime, double minPeriapsis, ScanAcceptor acceptor, int scanCount,
        ScanProfiler profiler) {
        if (root == null || origin == null || dest == null || attractor == null) {
            return ScanResult.invalid();
        }

        double mu = Math.max(1e-6, attractor.mu());
        long hohmannStartNanos = System.nanoTime();
        double hohmannTof = attractor.getHohmannTof(origin, dest, root, departureTime);
        if (profiler != null) profiler.addHohmannNanos(System.nanoTime() - hohmannStartNanos);
        if (hohmannTof <= 0.0) {
            return ScanResult.invalid();
        }

        long departureResolveStartNanos = System.nanoTime();
        OrbitalMechanics.OrbitalState srcStateDep = OrbitalMechanics.resolveWorldState(root, origin, departureTime);
        OrbitalMechanics.OrbitalState attractorAtDep = OrbitalMechanics
            .resolveWorldState(root, attractor, departureTime);
        if (profiler != null) profiler.addDepartureResolveNanos(System.nanoTime() - departureResolveStartNanos);
        if (srcStateDep == null || attractorAtDep == null) {
            return ScanResult.invalid();
        }

        double r1x0 = srcStateDep.x() - attractorAtDep.x();
        double r1y0 = srcStateDep.y() - attractorAtDep.y();
        double vsrcX0 = srcStateDep.vx() - attractorAtDep.vx();
        double vsrcY0 = srcStateDep.vy() - attractorAtDep.vy();

        ScanResult best = ScanResult.invalid();

        for (int i = 0; i < scanCount; i++) {
            if (profiler != null) profiler.incrementCandidateCount();
            double frac = 0.1 + (3.0 - 0.1) * i / (scanCount - 1);
            double tof = hohmannTof * frac;
            if (tof <= 0.0) continue;

            long arrivalResolveStartNanos = System.nanoTime();
            OrbitalMechanics.OrbitalState dstState = OrbitalMechanics
                .resolveWorldState(root, dest, departureTime + tof);
            OrbitalMechanics.OrbitalState attractorAtArr = OrbitalMechanics
                .resolveWorldState(root, attractor, departureTime + tof);
            if (profiler != null) profiler.addArrivalResolveNanos(System.nanoTime() - arrivalResolveStartNanos);
            if (dstState == null || attractorAtArr == null) continue;

            long geometryStartNanos = System.nanoTime();
            double r2x = dstState.x() - attractorAtArr.x();
            double r2y = dstState.y() - attractorAtArr.y();
            double vdstX = dstState.vx() - attractorAtArr.vx();
            double vdstY = dstState.vy() - attractorAtArr.vy();

            if (profiler != null) profiler.addGeometryNanos(System.nanoTime() - geometryStartNanos);
            if (OrbitalTransferPlanner.isDegenerateTransferGeometry(r1x0, r1y0, r2x, r2y)) continue;

            long lambertStartNanos = System.nanoTime();
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
            if (profiler != null) {
                profiler.addLambertNanos(System.nanoTime() - lambertStartNanos);
                profiler.incrementLambertPairCount();
            }

            boolean preferPrograde = OrbitalTransferPlanner
                .preferredProgradeForTransfer(r1x0, r1y0, vsrcX0, vsrcY0, r2x, r2y);
            LambertTransfer.Solution bestSol = OrbitalTransferPlanner
                .chooseLowerDvSolution(progSol, retSol, preferPrograde);
            if (bestSol == null || !bestSol.valid()) continue;

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

            long acceptStartNanos = System.nanoTime();
            if (acceptor.accept(current, best)) {
                best = current;
            }
            if (profiler != null) profiler.addAcceptNanos(System.nanoTime() - acceptStartNanos);
        }

        return best;
    }
}
