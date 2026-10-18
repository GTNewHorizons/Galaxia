package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.flight;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialBodyProperties;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.analysis.RocketAssembly;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.analysis.RocketStage;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartInstance;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.fuel.RocketFuel;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.FuelTankPartDef;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.StoragePartDef;

/**
 * Rocket physics: how much delta-v a trip needs, how much a rocket has and which stages survive the burn.
 * <p>
 * Every balance knob for flights is a constant at the top of this class
 */
public final class RocketFlightPlanner {

    /** Standard gravity in m/s², converts specific impulse to exhaust velocity */
    public static final double G0 = 9.81;
    /** Real delta-v is multiplied by this so early rockets stay small */
    public static final double DELTA_V_SCALE = 0.25;
    /** Extra delta-v spent climbing through an atmosphere */
    public static final double ATMOSPHERE_LOSS_FACTOR = 1.25;
    /** Fraction of orbital speed needed to land when the atmosphere and parachutes do the rest */
    public static final double AEROBRAKE_FRACTION = 0.25;
    /** Fraction of orbital speed needed to hop between two silos on the same body */
    public static final double HOP_FRACTION = 0.4;
    /** Minimum thrust to weight ratio of the first stage */
    public static final double MIN_TWR = 1.2;
    /** Mass of a single cargo item in kg */
    public static final double CARGO_MASS_PER_ITEM = 0.5;
    /** Flight time is this plus one tick per {@link #DV_PER_TRAVEL_TICK} m/s of real delta-v */
    public static final int BASE_TRAVEL_TICKS = 200;
    public static final double DV_PER_TRAVEL_TICK = 50;
    /** Delta-v used when either end is not a known celestial body */
    public static final double UNKNOWN_ROUTE_DELTA_V = 12_000;

    /** Circular orbit speed at one Earth radius around one Earth mass, in m/s */
    private static final double EARTH_ORBITAL_SPEED = 7_905;
    /** Mass of a star without explicit properties, in Earth masses */
    private static final double DEFAULT_STAR_MASS = 333_000;
    /** Pressure above which a body counts as having an atmosphere, in Pa */
    private static final double ATMOSPHERE_PRESSURE = 1_000;

    private RocketFlightPlanner() {}

    /**
     * Outcome of burning a rocket towards a target delta-v
     *
     * @param reachable            True when the rocket has enough delta-v
     * @param requiredDeltaV       Delta-v needed in m/s
     * @param availableDeltaV      Delta-v the rocket has in m/s
     * @param lowestSurvivingStage Number of the lowest stage still attached on arrival
     * @param remaining            Fuel and cargo left after the burn
     */
    public record FlightPlan(boolean reachable, double requiredDeltaV, double availableDeltaV, int lowestSurvivingStage,
        RocketLoadout remaining) {

        /**
         * Lists the modules that arrive at the destination
         *
         * @param assembly The analysed rocket
         *
         * @return Modules of every surviving stage
         */
        public List<RocketPartInstance> survivingParts(RocketAssembly assembly) {
            List<RocketPartInstance> parts = new ArrayList<>();
            for (RocketStage stage : assembly.stages()) {
                if (stage.getNumber() <= lowestSurvivingStage) parts.addAll(stage.getParts());
            }
            return parts;
        }
    }

    /**
     * Simulates the flight stage by stage, bottom first. A stage that has to burn all of its fuel is dropped, the
     * stage that finishes the burn arrives together with everything above it
     *
     * @param assembly       The analysed rocket
     * @param loadout        Fuel and cargo on board
     * @param requiredDeltaV Delta-v needed in m/s
     *
     * @return The plan, with the remaining fuel already deducted
     */
    public static FlightPlan plan(RocketAssembly assembly, RocketLoadout loadout, double requiredDeltaV) {
        List<RocketStage> stages = assembly.stages();
        RocketLoadout state = loadout.copy();
        double remaining = requiredDeltaV;

        for (int i = stages.size() - 1; i >= 0; i--) {
            RocketStage stage = stages.get(i);
            if (remaining <= 0) {
                return new FlightPlan(true, requiredDeltaV, availableDeltaV(stages, loadout), stage.getNumber(), state);
            }

            double totalMass = massAbove(stages, i, state);
            Set<RocketFuel> fuels = fuelsWithContent(stage, state);
            double isp = stage.getEffectiveIsp(fuels);
            double usable = burnableFuelMass(stage, state, fuels);
            double stageDeltaV = deltaV(isp, totalMass, usable);

            if (stageDeltaV > 0 && stageDeltaV >= remaining) {
                double burned = totalMass * (1 - Math.exp(-remaining / (isp * G0)));
                drain(stage, state, fuels, usable <= 0 ? 0 : burned / usable);
                return new FlightPlan(true, requiredDeltaV, availableDeltaV(stages, loadout), stage.getNumber(), state);
            }

            drain(stage, state, fuels, 1);
            remaining -= stageDeltaV;
        }

        return new FlightPlan(false, requiredDeltaV, availableDeltaV(stages, loadout), -1, state);
    }

    /**
     * Total delta-v when every stage burns all of its fuel
     *
     * @param stages  Stages ordered top (0) to bottom
     * @param loadout Fuel and cargo on board
     *
     * @return Delta-v in m/s
     */
    public static double availableDeltaV(List<RocketStage> stages, RocketLoadout loadout) {
        RocketLoadout state = loadout.copy();
        double total = 0;
        for (int i = stages.size() - 1; i >= 0; i--) {
            RocketStage stage = stages.get(i);
            Set<RocketFuel> fuels = fuelsWithContent(stage, state);
            total += deltaV(
                stage.getEffectiveIsp(fuels),
                massAbove(stages, i, state),
                burnableFuelMass(stage, state, fuels));
            drain(stage, state, fuels, 1);
        }
        return total;
    }

    /**
     * Checks that the first stage can lift the whole rocket
     *
     * @param stages   Stages ordered top (0) to bottom
     * @param loadout  Fuel and cargo on board
     * @param gravityG Surface gravity of the launch site in g
     *
     * @return Boolean : True => thrust to weight ratio is high enough
     */
    public static boolean canLiftOff(List<RocketStage> stages, RocketLoadout loadout, double gravityG) {
        if (stages.isEmpty()) return false;
        RocketStage first = stages.getLast();
        double weight = massAbove(stages, stages.size() - 1, loadout) * gravityG;
        return first.getTotalThrust() >= weight * MIN_TWR;
    }

    /**
     * Delta-v needed to fly between the surfaces of two dimensions
     *
     * @param fromDim Launch dimension
     * @param toDim   Landing dimension
     *
     * @return Delta-v in m/s, already scaled by {@link #DELTA_V_SCALE}
     */
    public static double requiredDeltaV(int fromDim, int toDim) {
        return rawDeltaV(fromDim, toDim) * DELTA_V_SCALE;
    }

    /** Flight time in ticks between two dimensions */
    public static int travelTicks(int fromDim, int toDim) {
        return BASE_TRAVEL_TICKS + (int) (rawDeltaV(fromDim, toDim) / DV_PER_TRAVEL_TICK);
    }

    /** Surface gravity of a dimension in g, 1 when unknown */
    public static double surfaceGravity(int dim) {
        return GalaxiaCelestialAPI.findByDimension(dim)
            .map(RocketFlightPlanner::gravity)
            .orElse(1.0);
    }

    private static double rawDeltaV(int fromDim, int toDim) {
        Optional<CelestialObject> from = GalaxiaCelestialAPI.findByDimension(fromDim);
        Optional<CelestialObject> to = GalaxiaCelestialAPI.findByDimension(toDim);
        if (from.isEmpty() || to.isEmpty()) return UNKNOWN_ROUTE_DELTA_V;

        CelestialObject a = from.get();
        CelestialObject b = to.get();
        if (a.key()
            .equals(b.key())) return surfaceOrbitSpeed(a) * HOP_FRACTION;
        return ascent(a) + transfer(a, b) + descent(b);
    }

    private static double ascent(CelestialObject body) {
        return surfaceOrbitSpeed(body) * (hasAtmosphere(body) ? ATMOSPHERE_LOSS_FACTOR : 1.0);
    }

    private static double descent(CelestialObject body) {
        return surfaceOrbitSpeed(body) * (hasAtmosphere(body) ? AEROBRAKE_FRACTION : 1.0);
    }

    /**
     * Hohmann transfer around the closest common parent, with escape and capture burns at both ends
     */
    private static double transfer(CelestialObject a, CelestialObject b) {
        List<CelestialObject> chainA = ancestry(a);
        List<CelestialObject> chainB = ancestry(b);

        CelestialObject common = null;
        int indexA = -1;
        int indexB = -1;
        for (int i = 0; i < chainA.size() && common == null; i++) {
            for (int j = 0; j < chainB.size(); j++) {
                if (chainA.get(i)
                    .key()
                    .equals(
                        chainB.get(j)
                            .key())) {
                    common = chainA.get(i);
                    indexA = i;
                    indexB = j;
                    break;
                }
            }
        }
        if (common == null) return UNKNOWN_ROUTE_DELTA_V;

        double mu = mu(common);
        double r1 = indexA == 0 ? parkingRadius(common)
            : chainA.get(indexA - 1)
                .orbitalParams()
                .semiMajorAxis();
        double r2 = indexB == 0 ? parkingRadius(common)
            : chainB.get(indexB - 1)
                .orbitalParams()
                .semiMajorAxis();
        if (r1 <= 0 || r2 <= 0) return UNKNOWN_ROUTE_DELTA_V;

        double v1 = orbitSpeed(mu, r1);
        double v2 = orbitSpeed(mu, r2);
        double vInfDeparture = Math.abs(v1 * (Math.sqrt(2 * r2 / (r1 + r2)) - 1));
        double vInfArrival = Math.abs(v2 * (1 - Math.sqrt(2 * r1 / (r1 + r2))));

        double departure = indexA == 0 ? vInfDeparture : hyperbolicBurn(a, vInfDeparture);
        double arrival = indexB == 0 ? vInfArrival : hyperbolicBurn(b, vInfArrival);
        return departure + arrival;
    }

    /** Burn from a parking orbit to a hyperbolic excess speed, or the reverse for capture */
    private static double hyperbolicBurn(CelestialObject body, double vInf) {
        double vCircular = orbitSpeed(mu(body), parkingRadius(body));
        double vEscape = vCircular * Math.sqrt(2);
        return Math.sqrt(vInf * vInf + vEscape * vEscape) - vCircular;
    }

    private static List<CelestialObject> ancestry(CelestialObject body) {
        List<CelestialObject> chain = new ArrayList<>();
        CelestialObject current = body;
        while (current != null && chain.size() < 16) {
            chain.add(current);
            if (current.parentKey() == null) break;
            current = GalaxiaCelestialAPI.findBodyByKey(current.parentKey())
                .orElse(null);
        }
        return chain;
    }

    private static double surfaceOrbitSpeed(CelestialObject body) {
        return orbitSpeed(mu(body), radius(body));
    }

    /** Orbital speed in m/s around a body of mass mu (Earth masses) at radius r (Earth radii) */
    private static double orbitSpeed(double mu, double r) {
        return EARTH_ORBITAL_SPEED * Math.sqrt(mu / r);
    }

    private static double gravity(CelestialObject body) {
        double g = body.properties()
            .localGravityG();
        if (g > 0) return g;
        return body.objectClass() == CelestialObject.Class.STAR ? 28 : 0.1;
    }

    private static double radius(CelestialObject body) {
        double r = body.properties()
            .radiusEarthRelative();
        if (r > 0) return r;
        // Same density as Earth means radius scales with surface gravity
        return body.objectClass() == CelestialObject.Class.STAR ? 109 : Math.max(0.05, gravity(body));
    }

    private static double mu(CelestialObject body) {
        double mass = body.properties()
            .massEarthRelative();
        if (mass > 0) return mass;
        if (body.objectClass() == CelestialObject.Class.STAR) return DEFAULT_STAR_MASS;
        double r = radius(body);
        return gravity(body) * r * r;
    }

    private static double parkingRadius(CelestialObject body) {
        return radius(body) * 1.1;
    }

    private static boolean hasAtmosphere(CelestialObject body) {
        CelestialBodyProperties properties = body.properties();
        return properties.surfacePressurePa() > ATMOSPHERE_PRESSURE;
    }

    private static double deltaV(double isp, double totalMass, double fuelMass) {
        if (isp <= 0 || fuelMass <= 0 || totalMass <= fuelMass) return 0;
        return isp * G0 * Math.log(totalMass / (totalMass - fuelMass));
    }

    /** Wet mass of a stage and every stage above it, cargo included */
    private static double massAbove(List<RocketStage> stages, int lowestStage, RocketLoadout loadout) {
        double mass = 0;
        for (int i = 0; i <= lowestStage; i++) {
            RocketStage stage = stages.get(i);
            mass += stage.getDryMass();
            for (RocketPartInstance part : stage.getParts()) {
                if (part.def() instanceof FuelTankPartDef) mass += loadout.getFuelMass(part);
                if (part.def() instanceof StoragePartDef) mass += loadout.getCargoMass(part);
            }
        }
        return mass;
    }

    private static Set<RocketFuel> fuelsWithContent(RocketStage stage, RocketLoadout loadout) {
        Set<RocketFuel> fuels = new HashSet<>();
        for (RocketPartInstance tank : stage.getTanks()) {
            if (loadout.getFuel(tank) > 0 && stage.canBurn(tank)) fuels.add(((FuelTankPartDef) tank.def()).fuel());
        }
        return fuels;
    }

    private static double burnableFuelMass(RocketStage stage, RocketLoadout loadout, Set<RocketFuel> fuels) {
        double mass = 0;
        for (RocketPartInstance tank : stage.getTanks()) {
            if (fuels.contains(((FuelTankPartDef) tank.def()).fuel())) mass += loadout.getFuelMass(tank);
        }
        return mass;
    }

    /** Removes a fraction of the burnable fuel from every tank of a stage */
    private static void drain(RocketStage stage, RocketLoadout loadout, Set<RocketFuel> fuels, double fraction) {
        double clamped = Math.max(0, Math.min(1, fraction));
        for (RocketPartInstance tank : stage.getTanks()) {
            if (!fuels.contains(((FuelTankPartDef) tank.def()).fuel())) continue;
            int amount = loadout.getFuel(tank);
            loadout.setFuel(tank, amount - (int) Math.ceil(amount * clamped));
        }
    }
}
