package com.gtnewhorizons.galaxia.registry.orbital;

import com.github.bsideup.jabel.Desugar;

public final class LambertTransfer {

    @Desugar
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
        if (r1 < 1e-10 || r2 < 1e-10) return Solution.invalid();

        double cdx = r2x - r1x, cdy = r2y - r1y;
        double c = Math.hypot(cdx, cdy);
        if (c < 1e-10) return Solution.invalid();

        double s = (r1 + r2 + c) * 0.5;
        if (s < 1e-10) return Solution.invalid();

        double dot = r1x * r2x + r1y * r2y;
        double crossZ = r1x * r2y - r1y * r2x;
        double dthCCW = Math.atan2(crossZ, dot);
        if (dthCCW < 0.0) dthCCW += 2.0 * Math.PI;
        double dth = prograde ? dthCCW : (2.0 * Math.PI - dthCCW);
        if (dth < 1e-10 || dth > 2.0 * Math.PI - 1e-10) return Solution.invalid();

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

        if (Math.abs(T - tofNormalized(x, lambda)) > 0.01 * Math.max(1e-10, T)) return Solution.invalid();

        double y = Math.sqrt(1.0 - lambda * lambda + lambda * lambda * x * x);
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

        double periapsis = Hierarchy.OrbitalCelestialBody.computePeriapsis(r1x, r1y, sol.dvx1(), sol.dvy1(), mu);
        if (periapsis < minPeriapsis) return Solution.invalid();

        double depDv = Math.hypot(sol.dvx1() - vsrcX, sol.dvy1() - vsrcY);
        double capDv = Math.hypot(vdstX - sol.dvx2(), vdstY - sol.dvy2());
        double totalDv = depDv + capDv;

        return new Solution(sol.dvx1(), sol.dvy1(), sol.dvx2(), sol.dvy2(), depDv, capDv, totalDv, periapsis, true);
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
}
