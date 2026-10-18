package com.gtnewhorizons.galaxia.core.config;

import com.gtnewhorizon.gtnhlib.config.Config;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalMechanics.CostModel;

@Config(modid = Galaxia.MODID, category = "Rocket")
@Config.LangKey("galaxia.config.category.rocket")
public class ConfigRocket {

    @Config.LangKey("galaxia.config.rocket.flight")
    public static final Flight flight = new Flight();

    @Config.LangKey("galaxia.config.rocket.wear")
    public static final Wear wear = new Wear();

    public static class Wear {

        @Config.LangKey("galaxia.config.rocket.wear.distance")
        @Config.DefaultDouble(10000000)
        @Config.RangeDouble(min = 1)
        public double kilometresPerPercent = 10000000;

        @Config.LangKey("galaxia.config.rocket.wear.asteroid_belt")
        @Config.DefaultDouble(3)
        @Config.RangeDouble(min = 1)
        public double asteroidBeltMultiplier = 3;

        @Config.LangKey("galaxia.config.rocket.wear.capsule")
        @Config.DefaultDouble(1)
        @Config.RangeDouble(min = 0)
        public double capsuleMultiplier = 1;

        @Config.LangKey("galaxia.config.rocket.wear.tank")
        @Config.DefaultDouble(1)
        @Config.RangeDouble(min = 0)
        public double tankMultiplier = 1;

        @Config.LangKey("galaxia.config.rocket.wear.engine")
        @Config.DefaultDouble(1)
        @Config.RangeDouble(min = 0)
        public double engineMultiplier = 1;

        @Config.LangKey("galaxia.config.rocket.wear.cargo")
        @Config.DefaultDouble(1)
        @Config.RangeDouble(min = 0)
        public double cargoMultiplier = 1;

        @Config.LangKey("galaxia.config.rocket.wear.cabin")
        @Config.DefaultDouble(1)
        @Config.RangeDouble(min = 0)
        public double cabinMultiplier = 1;

        @Config.LangKey("galaxia.config.rocket.wear.structure")
        @Config.DefaultDouble(1)
        @Config.RangeDouble(min = 0)
        public double structureMultiplier = 1;
    }

    public static class Flight {

        public CostModel costModel() {
            return new CostModel(
                gravityLossAtOneGMps,
                atmosphereLossAtOneAtmMps,
                atmosphericLandingCostFraction,
                maxOrbitalBurnTimeScaleFraction,
                localManeuverTimeScale,
                dockingDeltaVMps,
                minimumSurfaceManeuverSeconds);
        }

        @Config.LangKey("galaxia.config.rocket.flight.docking_delta_v")
        @Config.DefaultDouble(20)
        @Config.RangeDouble(min = 0)
        public double dockingDeltaVMps = 20;

        @Config.LangKey("galaxia.config.rocket.flight.gravity_loss")
        @Config.DefaultDouble(1000)
        @Config.RangeDouble(min = 0)
        public double gravityLossAtOneGMps = 1000;

        @Config.LangKey("galaxia.config.rocket.flight.atmosphere_loss")
        @Config.DefaultDouble(100)
        @Config.RangeDouble(min = 0)
        public double atmosphereLossAtOneAtmMps = 100;

        @Config.LangKey("galaxia.config.rocket.flight.atmospheric_landing_fraction")
        @Config.DefaultDouble(0.35)
        @Config.RangeDouble(min = 0, max = 1)
        public double atmosphericLandingCostFraction = 0.35;

        @Config.LangKey("galaxia.config.rocket.flight.orbital_burn_fraction")
        @Config.DefaultDouble(0.05)
        @Config.RangeDouble(min = 0.000001, max = 1)
        public double maxOrbitalBurnTimeScaleFraction = 0.05;

        @Config.LangKey("galaxia.config.rocket.flight.maximum_transfer_seconds")
        @Config.DefaultDouble(180)
        @Config.RangeDouble(min = 1)
        public double maximumTransferSeconds = 180;

        @Config.LangKey("galaxia.config.rocket.flight.parking_altitude")
        @Config.DefaultDouble(200000)
        @Config.RangeDouble(min = 1)
        public double localParkingAltitudeM = 200000;

        @Config.LangKey("galaxia.config.rocket.flight.maneuver_time_scale")
        @Config.DefaultDouble(20)
        @Config.RangeDouble(min = 0.001)
        public double localManeuverTimeScale = 20;

        @Config.LangKey("galaxia.config.rocket.flight.minimum_surface_maneuver_seconds")
        @Config.DefaultDouble(10)
        @Config.RangeDouble(min = 0)
        public double minimumSurfaceManeuverSeconds = 10;

        @Config.LangKey("galaxia.config.rocket.flight.durability_reserve")
        @Config.DefaultDouble(10)
        @Config.RangeDouble(min = 0, max = 100)
        public double minimumDurabilityReservePercent = 10;
    }

    @Config.LangKey("galaxia.config.rocket.opening")
    public static final Opening opening = new Opening();

    public static class Opening {

        @Config.LangKey("galaxia.config.rocket.opening.luna_survey_ticks")
        @Config.DefaultInt(18000)
        @Config.RangeInt(min = 1)
        public int lunaSurveyTicks = 18000;
    }

    @Config.LangKey("galaxia.config.category.rocket_global")
    public static final ConfigRocketGlobal ConfigRocketGlobal = new ConfigRocketGlobal();

    @Config.LangKey("galaxia.config.rocket.basic")
    public static final Basic basic = new Basic();

    public static class Basic {

        @Config.LangKey("galaxia.config.rocket.basic.capsule_dry_mass")
        @Config.DefaultDouble(450)
        public double capsuleDryMassKg = 450;

        @Config.LangKey("galaxia.config.rocket.basic.tank_dry_mass")
        @Config.DefaultDouble(300)
        public double tankDryMassKg = 300;

        @Config.LangKey("galaxia.config.rocket.basic.engine_dry_mass")
        @Config.DefaultDouble(250)
        public double engineDryMassKg = 250;

        @Config.LangKey("galaxia.config.rocket.basic.cargo_dry_mass")
        @Config.DefaultDouble(400)
        public double cargoDryMassKg = 400;

        @Config.LangKey("galaxia.config.rocket.basic.item_cargo_slots")
        @Config.DefaultInt(9)
        @Config.RangeInt(min = 0)
        public int itemCargoSlots = 9;

        @Config.LangKey("galaxia.config.rocket.basic.fluid_cargo_capacity")
        @Config.DefaultInt(512000)
        @Config.RangeInt(min = 1)
        public int fluidCargoCapacity = 512000;

        @Config.LangKey("galaxia.config.rocket.basic.fluid_cargo_dry_mass")
        @Config.DefaultDouble(400)
        public double fluidCargoDryMassKg = 400;

        @Config.LangKey("galaxia.config.rocket.basic.fuel_capacity")
        @Config.DefaultInt(10000)
        @Config.RangeInt(min = 1)
        public int fuelCapacity = 10000;

        @Config.LangKey("galaxia.config.rocket.basic.oxidizer_capacity")
        @Config.DefaultInt(20000)
        @Config.RangeInt(min = 1)
        public int oxidizerCapacity = 20000;

        @Config.LangKey("galaxia.config.rocket.basic.thrust")
        @Config.DefaultDouble(800000)
        public double thrustNewtons = 800000;

        @Config.LangKey("galaxia.config.rocket.basic.exhaust_velocity")
        @Config.DefaultDouble(5200)
        public double exhaustVelocityMps = 5200;

        @Config.LangKey("galaxia.config.rocket.basic.fuel_volume_parts")
        @Config.DefaultDouble(1)
        public double fuelVolumeParts = 1;

        @Config.LangKey("galaxia.config.rocket.basic.oxidizer_volume_parts")
        @Config.DefaultDouble(2)
        public double oxidizerVolumeParts = 2;

        @Config.LangKey("galaxia.config.rocket.basic.fuel_density")
        @Config.DefaultDouble(0.8)
        public double fuelKilogramsPerUnit = 0.8;

        @Config.LangKey("galaxia.config.rocket.basic.oxidizer_density")
        @Config.DefaultDouble(1.14)
        public double oxidizerKilogramsPerUnit = 1.14;

        @Config.LangKey("galaxia.config.rocket.basic.manufacture_power")
        @Config.DefaultInt(480)
        @Config.RangeInt(min = 1)
        public int manufactureEuPerTick = 480;

        @Config.LangKey("galaxia.config.rocket.basic.capsule_manufacture_ticks")
        @Config.DefaultInt(1200)
        @Config.RangeInt(min = 1)
        public int capsuleManufactureTicks = 1200;

        @Config.LangKey("galaxia.config.rocket.basic.tank_manufacture_ticks")
        @Config.DefaultInt(1200)
        @Config.RangeInt(min = 1)
        public int tankManufactureTicks = 1200;

        @Config.LangKey("galaxia.config.rocket.basic.engine_manufacture_ticks")
        @Config.DefaultInt(1200)
        @Config.RangeInt(min = 1)
        public int engineManufactureTicks = 1200;

        @Config.LangKey("galaxia.config.rocket.basic.cargo_manufacture_ticks")
        @Config.DefaultInt(600)
        @Config.RangeInt(min = 1)
        public int cargoManufactureTicks = 600;

        @Config.LangKey("galaxia.config.rocket.basic.repair_material_amount")
        @Config.DefaultInt(1)
        @Config.RangeInt(min = 1)
        public int repairMaterialAmount = 1;

        @Config.LangKey("galaxia.config.rocket.basic.repair_power")
        @Config.DefaultInt(32)
        @Config.RangeInt(min = 1)
        public int repairEuPerTick = 32;

        @Config.LangKey("galaxia.config.rocket.basic.repair_ticks")
        @Config.DefaultInt(100)
        @Config.RangeInt(min = 1)
        public int repairTicks = 100;
    }

    @Config.LangKey("galaxia.config.category.rocket_global")
    public static class ConfigRocketGlobal {

        @Config.LangKey("galaxia.config.rocket.camera_distance")
        @Config.DefaultInt(20)
        @Config.RangeInt(min = 1, max = 100)
        public int rocketCameraDistance;
    }
}
