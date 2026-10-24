package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.gtnewhorizons.galaxia.core.config.ConfigRocket;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.fuel.FuelTankFrame;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.fuel.RocketFuel;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.fuel.RocketFuels;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.RocketPartDef;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.RocketPartDef.Engine;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.RocketPartDef.Kind;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.RocketPartDef.Storage;

/** Registered definitions. Post-initialization binds real resources and paid work bills. */
public class RocketPartRegistry {

    private static final double STANDARD_GRAVITY_MPS2 = 9.80665;

    private static final RocketPartRegistry INSTANCE = new RocketPartRegistry();
    private final Map<Integer, RocketPartDef> parts = new HashMap<>();
    private final Map<RocketPartCategory, List<RocketPartDef>> categoryMap = new HashMap<>();

    public static RocketPartDef CAPSULE_T1;
    public static RocketPartDef FUEL_TANK_T1;
    public static RocketPartDef ENGINE_T1;
    public static RocketPartDef DECOUPLER_T1;
    public static RocketPartDef LANDER_T1;
    public static RocketPartDef RIDER_T1;
    public static RocketPartDef STORAGE_T1;
    public static RocketPartDef FLUID_STORAGE_T1;

    public static RocketPartRegistry instance() {
        return INSTANCE;
    }

    public void register(RocketPartDef def, RocketPartCategory category) {
        if (parts.putIfAbsent(def.id(), def) != null)
            throw new IllegalArgumentException("Duplicate module definition ID");
        categoryMap.computeIfAbsent(category, ignored -> new ArrayList<>())
            .add(def);
    }

    public void replace(RocketPartDef replacement) {
        RocketPartDef previous = parts.get(replacement.id());
        if (previous == null) throw new IllegalArgumentException("Cannot replace an unregistered definition");
        if (previous.kind() != replacement.kind())
            throw new IllegalArgumentException("Definition replacement must keep its role");
        parts.put(replacement.id(), replacement);
        categoryMap.values()
            .forEach(definitions -> definitions.replaceAll(def -> def.id() == replacement.id() ? replacement : def));
        if (previous == CAPSULE_T1) CAPSULE_T1 = replacement;
        if (previous == FUEL_TANK_T1) FUEL_TANK_T1 = replacement;
        if (previous == ENGINE_T1) ENGINE_T1 = replacement;
        if (previous == DECOUPLER_T1) DECOUPLER_T1 = replacement;
        if (previous == LANDER_T1) LANDER_T1 = replacement;
        if (previous == RIDER_T1) RIDER_T1 = replacement;
        if (previous == STORAGE_T1) STORAGE_T1 = replacement;
        if (previous == FLUID_STORAGE_T1) FLUID_STORAGE_T1 = replacement;
    }

    @Nullable
    public RocketPartDef get(int id) {
        return parts.get(id);
    }

    @Nullable
    public RocketPartDef getByName(String nameKey) {
        return parts.values()
            .stream()
            .filter(
                def -> def.nameKey()
                    .equals(nameKey))
            .findFirst()
            .orElse(null);
    }

    public List<RocketPartDef> getByCategory(RocketPartCategory category) {
        return List.copyOf(categoryMap.getOrDefault(category, List.of()));
    }

    public List<RocketPartDef> getAll() {
        return List.copyOf(parts.values());
    }

    @Nullable
    public RocketPartDef getFuelTank(FuelTankFrame frame, RocketFuel fuel) {
        return getByName("galaxia.rocket.module.fuel_tank_" + frame.label + "_" + fuel.key());
    }

    public List<RocketFuel> getFuelsFor(FuelTankFrame frame) {
        return RocketFuels.all()
            .stream()
            .filter(frame::canHold)
            .toList();
    }

    public void registerAll() {
        parts.clear();
        categoryMap.clear();
        var config = ConfigRocket.basic;
        CAPSULE_T1 = definition(
            0,
            "basic_capsule",
            3,
            3,
            config.capsuleDryMassKg,
            "capsule_1",
            Kind.CAPSULE,
            0,
            0,
            new Storage(0, 0, Map.of()),
            null);
        register(CAPSULE_T1, RocketPartCategory.CAPSULES);
        FUEL_TANK_T1 = definition(
            1,
            "basic_fuel_tank",
            3,
            5,
            config.tankDryMassKg,
            "fuel_tank_3x5",
            Kind.FUEL_TANK,
            0,
            0,
            new Storage(0, 0, Map.of()),
            null);
        register(FUEL_TANK_T1, RocketPartCategory.FUEL_TANKS);
        ENGINE_T1 = definition(
            2,
            "basic_engine",
            3,
            4,
            config.engineDryMassKg,
            "engine_1",
            Kind.ENGINE,
            0,
            0,
            new Storage(0, 0, Map.of()),
            new Engine(config.thrustNewtons, config.exhaustVelocityMps, Map.of()));
        register(ENGINE_T1, RocketPartCategory.LIQUID_ENGINES);
        DECOUPLER_T1 = definition(
            3,
            "basic_decoupler",
            3,
            1,
            100,
            "decoupler_1",
            Kind.DECOUPLER,
            0,
            1,
            new Storage(0, 0, Map.of()),
            null);
        register(DECOUPLER_T1, RocketPartCategory.DECOUPLERS);
        LANDER_T1 = definition(
            4,
            "basic_lander",
            3,
            3,
            250,
            null,
            Kind.LANDER,
            0,
            0,
            new Storage(0, 0, Map.of()),
            null);
        register(LANDER_T1, RocketPartCategory.CABINS);
        RIDER_T1 = definition(5, "basic_rider", 3, 5, 250, null, Kind.RIDER, 6, 0, new Storage(0, 0, Map.of()), null);
        register(RIDER_T1, RocketPartCategory.CABINS);
        STORAGE_T1 = definition(
            6,
            "basic_storage",
            3,
            4,
            config.cargoDryMassKg,
            "storage_unit_1",
            Kind.CARGO,
            0,
            0,
            new Storage(config.itemCargoSlots, 0, Map.of()),
            null);
        register(STORAGE_T1, RocketPartCategory.STRUCTURAL);
        FLUID_STORAGE_T1 = definition(
            7,
            "basic_fluid_storage",
            3,
            4,
            config.fluidCargoDryMassKg,
            "storage_unit_1",
            Kind.CARGO,
            0,
            0,
            new Storage(0, config.fluidCargoCapacity, Map.of()),
            null);
        register(FLUID_STORAGE_T1, RocketPartCategory.STRUCTURAL);
        int id = 8;
        for (RocketFuel fuel : RocketFuels.all()) {
            var engine = fuel.engine();
            register(
                definition(
                    id++,
                    "engine_" + fuel.key(),
                    engine.width(),
                    engine.height(),
                    engine.weight(),
                    engine.assetFolder(),
                    Kind.ENGINE,
                    0,
                    0,
                    new Storage(0, 0, Map.of()),
                    new Engine(engine.thrust() * STANDARD_GRAVITY_MPS2, engine.isp() * STANDARD_GRAVITY_MPS2, Map.of()))
                        .withFuel(fuel, null),
                RocketPartCategory.LIQUID_ENGINES);
        }
        for (FuelTankFrame frame : FuelTankFrame.values()) {
            for (RocketFuel fuel : getFuelsFor(frame)) {
                register(
                    definition(
                        id++,
                        "fuel_tank_" + frame.label + "_" + fuel.key(),
                        frame.width,
                        frame.height,
                        frame.weight,
                        frame.assetFolder,
                        Kind.FUEL_TANK,
                        0,
                        0,
                        new Storage(0, 0, Map.of()),
                        null).withFuel(fuel, frame),
                    RocketPartCategory.FUEL_TANKS);
            }
        }
    }

    private static RocketPartDef definition(int id, String name, int width, int height, double massKg,
        @Nullable String assets, Kind kind, int seats, int stage, Storage storage, @Nullable Engine engine) {
        return new RocketPartDef(
            id,
            "galaxia.rocket.module." + name,
            width,
            height,
            massKg,
            assets,
            kind,
            seats,
            stage,
            storage,
            engine,
            null,
            null);
    }
}
