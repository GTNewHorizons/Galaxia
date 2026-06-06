package com.gtnewhorizons.galaxia.registry.orbital;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;

public final class LambertTransfer {

    private static final int MAX_ITERATIONS = 20;
    private static final double SOLVER_TOLERANCE = 1e-12;
    private static final double MIN_GEOMETRY = 1e-10;
    private static final double LOG_2 = Math.log(2.0);
    private static final double BATTIN_TOF_DISTANCE = 0.01;
    private static final double LAGRANGE_TOF_DISTANCE = 0.2;

    public record Solution(double dvx1, double dvy1, double dvx2, double dvy2, double depDv, double capDv,
        double totalDv, double periapsis, boolean valid) {

        private static final Solution INVALID = new Solution(0, 0, 0, 0, 0, 0, 0, 0, false);

        public static Solution invalid() {
            return INVALID;
        }
    }

    private double r1x, r1y;
    private double r2x, r2y;
    private double mu;
    private double tof;
    private boolean prograde = true;
    private double minPeriapsis = 0.0;

    private LambertTransfer(double r1x, double r1y, double r2x, double r2y) {
        this.r1x = r1x;
        this.r1y = r1y;
        this.r2x = r2x;
        this.r2y = r2y;
    }

    public static LambertTransfer between(double r1x, double r1y, double r2x, double r2y) {
        return new LambertTransfer(r1x, r1y, r2x, r2y);
    }

    public LambertTransfer mu(double mu) {
        this.mu = mu;
        return this;
    }

    public LambertTransfer timeOfFlight(double tof) {
        this.tof = tof;
        return this;
    }

    public LambertTransfer prograde(boolean prograde) {
        this.prograde = prograde;
        return this;
    }

    public LambertTransfer minPeriapsis(double minPeriapsis) {
        this.minPeriapsis = minPeriapsis;
        return this;
    }

    public LambertTransfer fromTo(double r1x, double r1y, double r2x, double r2y) {
        this.r1x = r1x;
        this.r1y = r1y;
        this.r2x = r2x;
        this.r2y = r2y;
        return this;
    }

    public Solution solve() {
        if (mu <= 0.0 || tof <= 0.0) return Solution.invalid();

        double r1 = Math.hypot(r1x, r1y);
        double r2 = Math.hypot(r2x, r2y);
        if (r1 < MIN_GEOMETRY || r2 < MIN_GEOMETRY) return Solution.invalid();

        double cdx = r2x - r1x, cdy = r2y - r1y;
        double c = Math.hypot(cdx, cdy);
        if (c < MIN_GEOMETRY) return Solution.invalid();

        double s = (r1 + r2 + c) * 0.5;
        if (s < MIN_GEOMETRY) return Solution.invalid();

        double dot = r1x * r2x + r1y * r2y;
        double crossZ = r1x * r2y - r1y * r2x;
        double dthCCW = Math.atan2(crossZ, dot);
        if (dthCCW < 0.0) dthCCW += 2.0 * Math.PI;
        double dth = prograde ? dthCCW : (2.0 * Math.PI - dthCCW);
        if (dth < MIN_GEOMETRY || dth > 2.0 * Math.PI - MIN_GEOMETRY) return Solution.invalid();

        double lambda = Math.sqrt(Math.max(0.0, 1.0 - c / s));
        if (dth > Math.PI) lambda = -lambda;

        double T = tof * Math.sqrt(2.0 * mu / (s * s * s));
        double x = initialGuess(T, lambda);
        if (!Double.isFinite(x) || x <= -1.0) return Solution.invalid();

        for (int i = 0; i < MAX_ITERATIONS; i++) {
            TimeOfFlightDerivatives derivatives = timeOfFlightDerivatives(x, lambda);
            if (derivatives == null || !derivatives.isFinite()) return Solution.invalid();

            double f = derivatives.tof() - T;
            if (Math.abs(f) <= SOLVER_TOLERANCE * Math.max(1.0, T)) break;

            double denominator = derivatives.d1() * (derivatives.d1() * derivatives.d1() - f * derivatives.d2())
                + derivatives.d3() * f * f / 6.0;
            if (Math.abs(denominator) < 1e-15) return Solution.invalid();

            double numerator = f * (derivatives.d1() * derivatives.d1() - 0.5 * f * derivatives.d2());
            double nextX = x - numerator / denominator;
            if (!Double.isFinite(nextX) || nextX <= -1.0) return Solution.invalid();
            if (Math.abs(nextX - x) <= SOLVER_TOLERANCE * Math.max(1.0, Math.abs(x))) {
                x = nextX;
                break;
            }
            x = nextX;
        }

        double solvedT = tofNormalized(x, lambda);
        if (!Double.isFinite(solvedT) || Math.abs(T - solvedT) > 1e-8 * Math.max(1.0, T)) {
            return Solution.invalid();
        }

        double y = Math.sqrt(1.0 - lambda * lambda + lambda * lambda * x * x);
        if (!Double.isFinite(y)) return Solution.invalid();
        double gamma = Math.sqrt(mu * s / 2.0);
        double rho = (r1 - r2) / c;
        double sigma = Math.sqrt(Math.max(0.0, 1.0 - rho * rho));

        double vr1 = gamma / r1 * ((lambda * y - x) - rho * (lambda * y + x));
        double vt1 = gamma / r1 * sigma * (y + lambda * x);
        double vr2 = -gamma / r2 * ((lambda * y - x) + rho * (lambda * y + x));
        double vt2 = gamma / r2 * sigma * (y + lambda * x);

        double urx1 = r1x / r1, ury1 = r1y / r1;
        double urx2 = r2x / r2, ury2 = r2y / r2;
        double sign = prograde ? 1.0 : -1.0;
        double utx1 = sign * (-ury1), uty1 = sign * urx1;
        double utx2 = sign * (-ury2), uty2 = sign * urx2;

        double dvx1 = vr1 * urx1 + vt1 * utx1;
        double dvy1 = vr1 * ury1 + vt1 * uty1;
        double dvx2 = vr2 * urx2 + vt2 * utx2;
        double dvy2 = vr2 * ury2 + vt2 * uty2;

        return new Solution(dvx1, dvy1, dvx2, dvy2, 0, 0, 0, 0, true);
    }

    public Solution evaluateAgainst(double vsrcX, double vsrcY, double vdstX, double vdstY) {
        Solution sol = solve();
        if (!sol.valid()) return Solution.invalid();

        double periapsis = CelestialObject.computePeriapsis(r1x, r1y, sol.dvx1(), sol.dvy1(), mu);
        if (periapsis < minPeriapsis) return Solution.invalid();

        double depDv = Math.hypot(sol.dvx1() - vsrcX, sol.dvy1() - vsrcY);
        double capDv = Math.hypot(vdstX - sol.dvx2(), vdstY - sol.dvy2());
        double totalDv = depDv + capDv;

        return new Solution(sol.dvx1(), sol.dvy1(), sol.dvx2(), sol.dvy2(), depDv, capDv, totalDv, periapsis, true);
    }

    private static double initialGuess(double T, double lambda) {
        double lambda2 = lambda * lambda;
        double lambda3 = lambda2 * lambda;
        double lambda5 = lambda3 * lambda2;
        double T0 = Math.acos(lambda) + lambda * Math.sqrt(1.0 - lambda * lambda);
        double T1 = 2.0 / 3.0 * (1.0 - lambda3);
        if (T >= T0) {
            return Math.pow(T0 / T, 2.0 / 3.0) - 1.0;
        }
        if (T < T1) {
            return 2.5 * T1 * (T1 - T) / (T * (1.0 - lambda5)) + 1.0;
        }
        return Math.exp(LOG_2 * Math.log(T / T0) / Math.log(T1 / T0)) - 1.0;
    }

    private static double tofNormalized(double x, double lambda) {
        double lambda2 = lambda * lambda;
        double distanceFromParabolic = Math.abs(x - 1.0);
        if (distanceFromParabolic < LAGRANGE_TOF_DISTANCE && distanceFromParabolic > BATTIN_TOF_DISTANCE) {
            double a = 1.0 / (1.0 - x * x);
            if (a > 0.0) {
                double alpha = 2.0 * Math.acos(x);
                double beta = 2.0 * Math.asin(clamp(Math.sqrt(lambda2 / a), -1.0, 1.0));
                if (lambda < 0.0) beta = -beta;
                return a * Math.sqrt(a) * ((alpha - Math.sin(alpha)) - (beta - Math.sin(beta))) * 0.5;
            }
            double alpha = 2.0 * acosh(x);
            double beta = 2.0 * asinh(Math.sqrt(-lambda2 / a));
            if (lambda < 0.0) beta = -beta;
            return -a * Math.sqrt(-a) * ((beta - Math.sinh(beta)) - (alpha - Math.sinh(alpha))) * 0.5;
        }

        double e = x * x - 1.0;
        double rho = Math.abs(e);
        double z = Math.sqrt(1.0 + lambda2 * e);
        if (distanceFromParabolic < BATTIN_TOF_DISTANCE) {
            return tofBattin(x, z, lambda);
        }

        double y = Math.sqrt(rho);
        double g = x * z - lambda * e;
        double d = e < 0.0 ? Math.acos(clamp(g, -1.0, 1.0)) : Math.log(y * (z - lambda * x) + g);
        return (x - lambda * z - d / y) / e;
    }

    private static double tofBattin(double x, double z, double lambda) {
        double eta = z - lambda * x;
        double s1 = 0.5 * (1.0 - lambda - x * eta);
        double q = 4.0 / 3.0 * hypergeometricF(s1);
        return 0.5 * (eta * eta * eta * q + 4.0 * lambda * eta);
    }

    private static double hypergeometricF(double z) {
        double result = 1.0;
        double term = 1.0;
        for (int i = 0; i < 10_000; i++) {
            term *= (3.0 + i) * (1.0 + i) / (2.5 + i) * z / (i + 1.0);
            double next = result + term;
            if (next == result || Math.abs(term) <= SOLVER_TOLERANCE * Math.max(1.0, Math.abs(next))) return next;
            result = next;
        }
        return result;
    }

    private static TimeOfFlightDerivatives timeOfFlightDerivatives(double x, double lambda) {
        double tof = tofNormalized(x, lambda);
        if (!Double.isFinite(tof)) return null;
        double omx2 = 1.0 - x * x;
        if (Math.abs(omx2) < 1e-8) {
            double d1 = 2.0 / 5.0 * (Math.pow(lambda, 5.0) - 1.0);
            double d2 = numericalDerivative(x, lambda, 2);
            double d3 = numericalDerivative(x, lambda, 3);
            return new TimeOfFlightDerivatives(tof, d1, d2, d3);
        }
        double y = Math.sqrt(1.0 - lambda * lambda + lambda * lambda * x * x);
        double lambda2 = lambda * lambda;
        double lambda3 = lambda2 * lambda;
        double lambda5 = lambda3 * lambda2;
        double d1 = (3.0 * x * tof - 2.0 + 2.0 * lambda3 * x / y) / omx2;
        double d2 = (3.0 * tof + 5.0 * x * d1 + 2.0 * (1.0 - lambda2) * lambda3 / (y * y * y)) / omx2;
        double d3 = (7.0 * x * d2 + 8.0 * d1 - 6.0 * (1.0 - lambda2) * lambda5 * x / Math.pow(y, 5.0)) / omx2;
        return new TimeOfFlightDerivatives(tof, d1, d2, d3);
    }

    private static double numericalDerivative(double x, double lambda, int order) {
        double h = 1e-5;
        if (order == 2) {
            return (tofNormalized(x + h, lambda) - 2.0 * tofNormalized(x, lambda) + tofNormalized(x - h, lambda))
                / (h * h);
        }
        return (tofNormalized(x + 2.0 * h, lambda) - 2.0 * tofNormalized(x + h, lambda)
            + 2.0 * tofNormalized(x - h, lambda)
            - tofNormalized(x - 2.0 * h, lambda)) / (2.0 * h * h * h);
    }

    private static double asinh(double value) {
        return Math.log(value + Math.sqrt(value * value + 1.0));
    }

    private static double acosh(double value) {
        return Math.log(value + Math.sqrt(value * value - 1.0));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private record TimeOfFlightDerivatives(double tof, double d1, double d2, double d3) {

        private boolean isFinite() {
            return Double.isFinite(tof) && Double.isFinite(d1) && Double.isFinite(d2) && Double.isFinite(d3);
        }
    }
}
