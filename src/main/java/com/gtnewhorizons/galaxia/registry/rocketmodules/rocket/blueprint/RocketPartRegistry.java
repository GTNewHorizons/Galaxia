package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;

import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.fuel.FuelTankFrame;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.fuel.RocketFuel;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.fuel.RocketFuels;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.CapsulePartDef;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.DecouplerPartDef;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.EnginePartDef;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.FuelTankPartDef;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.IRocketPartDef;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.LanderPartDef;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.ModuleRecipe;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.RiderPartDef;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.RocketPartDef;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.StoragePartDef;

/**
 * Registers rocket modules and their properties from {@link IRocketPartDef} implementations.
 * <p>
 * Engines and fuel tanks are generated from {@link RocketFuels} and {@link FuelTankFrame}, so new fuels and frames
 * should be added there rather than here
 */
public class RocketPartRegistry {

    private static final RocketPartRegistry INSTANCE = new RocketPartRegistry();

    private final List<IRocketPartDef> parts = new ArrayList<>();
    private final Map<String, IRocketPartDef> byKey = new HashMap<>();
    private final Map<RocketPartCategory, List<IRocketPartDef>> categoryMap = new EnumMap<>(RocketPartCategory.class);

    public static CapsulePartDef CAPSULE;
    public static DecouplerPartDef DECOUPLER;
    public static LanderPartDef LANDER;
    public static RiderPartDef RIDER;
    public static StoragePartDef STORAGE;

    public static RocketPartRegistry instance() {
        return INSTANCE;
    }

    public <T extends RocketPartDef> T register(T def) {
        if (byKey.containsKey(def.key())) throw new IllegalStateException("Duplicate rocket part " + def.key());
        def.assignId(parts.size());
        parts.add(def);
        byKey.put(def.key(), def);
        categoryMap.computeIfAbsent(def.category(), k -> new ArrayList<>())
            .add(def);
        return def;
    }

    public IRocketPartDef get(int id) {
        return id >= 0 && id < parts.size() ? parts.get(id) : null;
    }

    public IRocketPartDef getByKey(String key) {
        return key == null ? null : byKey.get(key);
    }

    public <T extends IRocketPartDef> List<T> getByClass(Class<T> clazz) {
        List<T> result = new ArrayList<>();
        for (IRocketPartDef def : parts) {
            if (clazz.isInstance(def)) {
                result.add(clazz.cast(def));
            }
        }
        return result;
    }

    public List<IRocketPartDef> getByCategory(RocketPartCategory cat) {
        return categoryMap.getOrDefault(cat, new ArrayList<>());
    }

    public List<IRocketPartDef> getAll() {
        return new ArrayList<>(parts);
    }

    /**
     * Finds the tank module for a frame filled with a fuel
     *
     * @param frame The tank frame
     * @param fuel  The fuel of the core
     *
     * @return The module, or null if the frame cannot hold that fuel
     */
    public FuelTankPartDef getFuelTank(FuelTankFrame frame, RocketFuel fuel) {
        IRocketPartDef def = getByKey(FuelTankPartDef.keyOf(frame, fuel));
        return def instanceof FuelTankPartDef tank ? tank : null;
    }

    /** All fuels a frame can hold, in registration order */
    public List<RocketFuel> getFuelsFor(FuelTankFrame frame) {
        List<RocketFuel> result = new ArrayList<>();
        for (RocketFuel fuel : RocketFuels.all()) {
            if (frame.canHold(fuel)) result.add(fuel);
        }
        return result;
    }

    public void registerAll() {
        if (!parts.isEmpty()) return;

        // spotless:off
        CAPSULE = register(new CapsulePartDef("capsule", 3, 3, 450, "capsule_1"));
        CAPSULE.withRecipe(ModuleRecipe.builder()
            .ore("plateSteel", 24).ore("paneGlass", 4).ore("circuitBasic", 4).duration(400).build());

        for (RocketFuel fuel : RocketFuels.all()) {
            register(new EnginePartDef(fuel));
        }

        for (FuelTankFrame frame : FuelTankFrame.values()) {
            for (RocketFuel fuel : getFuelsFor(frame)) {
                register(new FuelTankPartDef(frame, fuel));
            }
        }

        DECOUPLER = register(new DecouplerPartDef("decoupler", 3, 1, 100, "decoupler_1"));
        DECOUPLER.withRecipe(ModuleRecipe.builder()
            .ore("plateSteel", 8).ore("screwSteel", 8).duration(120).build());

        LANDER = register(new LanderPartDef("lander", 3, 3, 250, null));
        LANDER.withRecipe(ModuleRecipe.builder()
            .ore("plateAluminium", 16).ore("circuitGood", 2).duration(300).build());

        RIDER = register(new RiderPartDef("rider", 3, 5, 250, 6, null));
        RIDER.withRecipe(ModuleRecipe.builder()
            .ore("plateSteel", 20).ore("plateRubber", 6).duration(300).build());

        STORAGE = register(new StoragePartDef("storage", 3, 4, 900, 27, "storage_unit_1"));
        STORAGE.withRecipe(ModuleRecipe.builder()
            .ore("plateSteel", 16).item(new ItemStack(Blocks.chest), 2).duration(200).build());
        // spotless:on
    }
}
