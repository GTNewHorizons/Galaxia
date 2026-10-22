package com.gtnewhorizons.galaxia.core.config;

import com.gtnewhorizon.gtnhlib.config.Config;
import com.gtnewhorizons.galaxia.core.Galaxia;

@Config(modid = Galaxia.MODID, category = "Machines")
@Config.LangKey("galaxia.config.category.machines")
public class ConfigMachines {

    @Config.LangKey("galaxia.config.category.machines.energy")
    public static final Energy energy = new Energy();

    @Config.LangKey("galaxia.config.category.machines.collector")
    public static final OxygenCollector collector = new OxygenCollector();

    @Config.LangKey("galaxia.config.category.machines.pylon")
    public static final OxygenPylon pylon = new OxygenPylon();

    @Config.LangKey("galaxia.config.category.machines.filler")
    public static final OxygenFiller filler = new OxygenFiller();

    @Config.LangKey("galaxia.config.category.machines.android")
    public static final Android android = new Android();

    /** EV operating and charging costs. Each chassis supplies its own scaling. */
    public static class Android {

        @Config.LangKey("galaxia.config.machines.android.max_health")
        @Config.DefaultFloat(20)
        @Config.RangeFloat(min = 1f, max = 1000f)
        public float maxHealth;

        @Config.LangKey("galaxia.config.machines.android.required_bandwidth")
        @Config.DefaultInt(64)
        @Config.RangeInt(min = 1, max = 1000000)
        public int requiredBandwidthKbps;

        @Config.LangKey("galaxia.config.machines.android.base_energy")
        @Config.DefaultInt(128)
        @Config.RangeInt(min = 0, max = 1000000)
        public int baseEnergyPerTick;

        @Config.LangKey("galaxia.config.machines.android.walking_energy")
        @Config.DefaultInt(64)
        @Config.RangeInt(min = 0, max = 1000000)
        public int walkingEnergyPerTick;

        @Config.LangKey("galaxia.config.machines.android.sprinting_energy")
        @Config.DefaultInt(192)
        @Config.RangeInt(min = 0, max = 1000000)
        public int sprintingEnergyPerTick;

        @Config.LangKey("galaxia.config.machines.android.jump_energy")
        @Config.DefaultInt(640)
        @Config.RangeInt(min = 0, max = 1000000)
        public int jumpEnergy;

        @Config.LangKey("galaxia.config.machines.android.heating_energy")
        @Config.DefaultInt(128)
        @Config.RangeInt(min = 0, max = 1000000)
        public int heatingEnergyPerTick;

        @Config.LangKey("galaxia.config.machines.android.zero_g_energy")
        @Config.DefaultInt(128)
        @Config.RangeInt(min = 0, max = 1000000)
        public int zeroGEnergyPerTick;

        @Config.LangKey("galaxia.config.machines.android.capsule_energy")
        @Config.DefaultInt(128)
        @Config.RangeInt(min = 1, max = 1000000)
        public int capsuleEuPerTick;

        @Config.LangKey("galaxia.config.machines.android.dock_energy")
        @Config.DefaultInt(128)
        @Config.RangeInt(min = 1, max = 1000000)
        public int dockEuPerTick;

        @Config.LangKey("galaxia.config.machines.android.charge_energy")
        @Config.DefaultInt(1024)
        @Config.RangeInt(min = 1, max = 1000000)
        public int chargeEuPerTick;

        @Config.LangKey("galaxia.config.machines.android.repair_health")
        @Config.DefaultFloat(4)
        @Config.RangeFloat(min = 0.1f, max = 1000f)
        public float repairHealth;

        @Config.LangKey("galaxia.config.machines.android.countdown_ticks")
        @Config.DefaultInt(60)
        @Config.RangeInt(min = 1, max = 1200)
        public int countdownTicks;

        @Config.LangKey("galaxia.config.machines.android.view_ticks")
        @Config.DefaultInt(2)
        @Config.RangeInt(min = 0, max = 200)
        public int viewTicks;

        @Config.LangKey("galaxia.config.machines.android.low_battery_percent")
        @Config.DefaultInt(10)
        @Config.RangeInt(min = 1, max = 99)
        public int lowBatteryPercent;

        @Config.LangKey("galaxia.config.machines.android.critical_battery_percent")
        @Config.DefaultInt(5)
        @Config.RangeInt(min = 1, max = 99)
        public int criticalBatteryPercent;

        @Config.LangKey("galaxia.config.machines.android.checkpoint_ticks")
        @Config.DefaultInt(100)
        @Config.RangeInt(min = 1, max = 12000)
        public int checkpointIntervalTicks;

        @Config.LangKey("galaxia.config.machines.android.biological_metabolism_ticks")
        @Config.Comment("Ticks per saturation point, then food point, consumed by a biological body in remote control")
        @Config.DefaultInt(1200)
        @Config.RangeInt(min = 1, max = 72000)
        public int biologicalMetabolismPeriodTicks;

        public long energyPerTick(boolean moving, boolean sprinting, boolean heating, boolean zeroGravity) {
            long load = baseEnergyPerTick;
            if (moving) load += sprinting ? sprintingEnergyPerTick : walkingEnergyPerTick;
            if (heating) load += heatingEnergyPerTick;
            if (zeroGravity) load += zeroGEnergyPerTick;
            return load;
        }
    }

    public static class Energy {

        /**
         * Force all Galaxia machines to use RF instead of EU.
         * By default, EU is used
         */
        @Config.LangKey("galaxia.config.machines.energy.force_rf")
        @Config.DefaultBoolean(false)
        public boolean useRF;

        /**
         * How many RF equal one EU. Used when converting RF input to internal EU storage.
         * Standard ratio is 4 RF = 1 EU.
         */
        @Config.LangKey("galaxia.config.machines.energy.rf_per_eu")
        @Config.DefaultInt(4)
        @Config.RangeInt(min = 1, max = 100)
        public int rfPerEU;
    }

    public static class OxygenCollector {

        /** EU (or RF, converted) consumed per generation cycle. */
        @Config.LangKey("galaxia.config.machines.collector.eu_per_op")
        @Config.DefaultDouble(32)
        public double euPerOperation;

        /** Ticks between each generation cycle. Default 200 = 10 seconds. */
        @Config.LangKey("galaxia.config.machines.collector.ticks_per_op")
        @Config.DefaultInt(200)
        @Config.RangeInt(min = 20, max = 6000)
        public int ticksPerOperation;

        /** Radius (in blocks) around the collector that is scanned for leaves and saplings. */
        @Config.LangKey("galaxia.config.machines.collector.scan_radius")
        @Config.DefaultInt(3)
        @Config.RangeInt(min = 1, max = 8)
        public int scanRadius;

        /** Oxygen units added to the buffer per leaf/sapling found per cycle. */
        @Config.LangKey("galaxia.config.machines.collector.oxygen_per_leaf")
        @Config.DefaultInt(10)
        @Config.RangeInt(min = 1, max = 1000)
        public int oxygenPerLeaf;

        /** Maximum oxygen units stored internally. */
        @Config.LangKey("galaxia.config.machines.collector.max_oxygen")
        @Config.DefaultInt(20000)
        @Config.RangeInt(min = 100, max = 1000000)
        public int maxOxygenBuffer;

        /** Maximum EU stored internally. */
        @Config.LangKey("galaxia.config.machines.collector.max_energy")
        @Config.DefaultDouble(2000)
        public double maxEnergyBuffer;
    }

    public static class OxygenPylon {

        /** EU (or RF) consumed per distribution cycle. */
        @Config.LangKey("galaxia.config.machines.pylon.eu_per_op")
        @Config.DefaultDouble(64)
        public double euPerOperation;

        /** Ticks between each distribution cycle. Default 20 = 1 second. */
        @Config.LangKey("galaxia.config.machines.pylon.ticks_per_op")
        @Config.DefaultInt(20)
        @Config.RangeInt(min = 1, max = 200)
        public int ticksPerOperation;

        /** Oxygen units pushed to each player per cycle. */
        @Config.LangKey("galaxia.config.machines.pylon.oxygen_per_player")
        @Config.DefaultInt(100)
        @Config.RangeInt(min = 1, max = 500)
        public int oxygenPerPlayerPerCycle;

        /** Maximum oxygen units stored internally. */
        @Config.LangKey("galaxia.config.machines.pylon.max_oxygen")
        @Config.DefaultInt(20000)
        @Config.RangeInt(min = 100, max = 1000000)
        public int maxOxygenBuffer;

        /** Maximum EU stored internally. */
        @Config.LangKey("galaxia.config.machines.pylon.max_energy")
        @Config.DefaultDouble(4000)
        public double maxEnergyBuffer;
    }

    public static class OxygenFiller {

        /** EU (or RF) consumed per fill cycle. */
        @Config.LangKey("galaxia.config.machines.filler.eu_per_op")
        @Config.DefaultDouble(32)
        public double euPerOperation;

        /** Ticks between each fill cycle. Default 40 = 2 seconds. */
        @Config.LangKey("galaxia.config.machines.filler.ticks_per_op")
        @Config.DefaultInt(40)
        @Config.RangeInt(min = 1, max = 600)
        public int ticksPerOperation;

        /** Oxygen units transferred from the buffer into tanks per cycle. */
        @Config.LangKey("galaxia.config.machines.filler.oxygen_per_op")
        @Config.DefaultInt(200)
        @Config.RangeInt(min = 1, max = 10000)
        public int oxygenPerOperation;

        /** Maximum oxygen units stored internally. */
        @Config.LangKey("galaxia.config.machines.filler.max_oxygen")
        @Config.DefaultInt(10000)
        @Config.RangeInt(min = 100, max = 1000000)
        public int maxOxygenBuffer;

        /** Maximum EU stored internally. */
        @Config.LangKey("galaxia.config.machines.filler.max_energy")
        @Config.DefaultDouble(2000)
        public double maxEnergyBuffer;
    }
}
