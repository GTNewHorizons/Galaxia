package com.gtnewhorizons.galaxia.utility;

import com.gtnewhorizons.galaxia.dimension.planets.BasePlanet;

public class OrbitalCalculatorHelper {

    static final double gravitationalConstant = 6.674 * Math.pow(10, -11);

    /**
     * Calculates the Effective Exhaust Velocity (v_e) based on the rocket and body to launch from
     *
     * @param launchBody     The Gravitational Body from the surface of which to launch
     * @param rocket         The rocket being used to calculate
     * @param launchAltitude The altitude above the body from which to launch
     * @return Effective Exhaust Velocity (v_e)
     */
    public static double calculateEffectiveExhaustVelocity(BasePlanet launchBody, Rocket rocket, int launchAltitude) {
        return rocket.getSpecificImpulse() * (launchBody.getDef().mass * gravitationalConstant)
            / Math.pow((launchAltitude + launchBody.getDef().orbitalRadius), 2);
    }

    /**
     * Calculates the Maximum DeltaV of the rocket provided launching from a given body
     *
     * @param launchBody     The Gravitational Body from the surface of which to launch
     * @param rocket         The rocket being used to calculate
     * @param launchAltitude The altitude above the body from which to launch
     * @return Maximum Delta V achievable
     */
    public static double calculateMaxDeltaVelocity(BasePlanet launchBody, Rocket rocket, int launchAltitude) {
        final double effectiveExhaustVelocity = calculateEffectiveExhaustVelocity(launchBody, rocket, launchAltitude);
        final double totalMass = rocket.getTotalMass();
        final double dryMass = rocket.getDryMass();

        return effectiveExhaustVelocity * Math.log(totalMass / dryMass);
    }

    /**
     * Calculates the DeltaV required to enter elliptical orbit (stage 1)
     *
     * @param launchBody  The Gravitational Body for starting orbit
     * @param centerBody  The Gravitational Body from which main source of Gravity in system
     * @param targetBody  The Gravitational Body for arrival orbit
     * @param startRadius The starting orbital radius (surface height for base launch)
     * @return DeltaV required for stage 1 of orbit transfer
     */
    public static double calculateHohmannV1(BasePlanet launchBody, BasePlanet targetBody, SystemCenter centerBody,
        double startRadius) {

        // Most of this is renaming readable variables to fit standard notation for calculation, and legibility

        // GM of the center body
        final double mu_s = centerBody.getMass() * gravitationalConstant;

        // GM of departure body
        final double mu_1 = launchBody.getDef().mass * gravitationalConstant;

        // Distance from center body to launch body
        final double r_1 = launchBody.getDef().orbitalRadius;

        // Distance from center body to arrival body
        final double r_2 = targetBody.getDef().orbitalRadius;

        // Radius of original orbit
        final double a_1 = startRadius;

        return Math.sqrt(
            Math.pow((Math.sqrt((2 * mu_s * r_2) / (r_1 * (r_1 + r_2))) - Math.sqrt(mu_s / r_1)), 2) + (2 * mu_1) / a_1)
            - Math.sqrt(mu_1 / a_1);
    }

    /**
     * Calculates the DeltaV required to correct elliptical orbit into circular (stage 2)
     *
     * @param centerBody The Gravitational Body from which gravity is mainly felt
     * @param launchBody The Gravitational Body for starting orbit
     * @param targetBody The Gravitational Body for arrival orbit
     * @param endRadius  The final orbital radius
     * @return DeltaV required for stage 2 of orbit transfer
     */
    public static double calculateHohmannV2(BasePlanet launchBody, BasePlanet targetBody, SystemCenter centerBody,
        double endRadius) {

        /*
         * Most of this is renaming readable variables to fit standard notation for calculation, and legibility
         * mu_s
         * mu_2
         * r_1
         * r_2
         * a_2
         */

        // GM of center body
        final double mu_s = centerBody.getMass() * gravitationalConstant;

        // GM of target body
        final double mu_2 = targetBody.getDef().mass * gravitationalConstant;

        // Distance from center body to launch body
        final double r_1 = launchBody.getDef().orbitalRadius;

        // Distance from center body to target body
        final double r_2 = targetBody.getDef().orbitalRadius;

        // orbital height for target body
        final double a_2 = endRadius;

        return Math.sqrt(
            (Math.pow((Math.sqrt((2 * mu_s * r_1) / (r_2 * (r_1 + r_2))) - Math.sqrt(mu_s / r_2)), 2)
                + (2 * mu_2) / a_2))
            - Math.sqrt(mu_2 / a_2);

    }

    /**
     * Combines the two stages of Hohmann Transfer
     *
     * @param launchBody  The Gravitational Body from first orbit
     * @param targetBody  The Gravitational Body for final orbit
     * @param centerBody  The Gravitational Body providing main gravitational pull in system (i.e. star etc.)
     * @param startRadius The starting circular radius of orbit
     * @param endRadius   The final circular radius target
     * @return Total Delta V required to alter orbital radius
     */
    public static double calculateHohmannVelocity(BasePlanet launchBody, BasePlanet targetBody, SystemCenter centerBody,
        double startRadius, double endRadius) {
        final double v_1 = calculateHohmannV1(launchBody, targetBody, centerBody, startRadius);
        final double v_2 = calculateHohmannV2(launchBody, targetBody, centerBody, endRadius);
        return Math.round((v_1 + v_2) * 100) / 100.0;
    }

    /**
     *
     * @param launchBody     The body from which to launch
     * @param launchAltitude The altitude above the body from which to launch
     * @return The escape velocity of the planet from that altitude
     */
    public static double calculateEscapeVelocity(BasePlanet launchBody, int launchAltitude) {
        return Math.sqrt(
            (2 * gravitationalConstant * launchBody.getDef().mass)
                / (launchBody.getDef().orbitalRadius + launchAltitude));
    }

    public static double calculateDirectDeltaV(BasePlanet launchBody, BasePlanet targetBody, int launchAltitude,
        int targetAltitude) {
        return calculateEscapeVelocity(launchBody, launchAltitude)
            + calculateEscapeVelocity(targetBody, targetAltitude);
    }

}
