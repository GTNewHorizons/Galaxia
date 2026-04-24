package com.gtnewhorizons.galaxia.registry.outpost.module;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import com.gtnewhorizons.galaxia.registry.orbital.OrbitalTransferPlanner;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.AllowShootingConfig;

public class OutpostModuleRegistry {

    public record Definition(FacilityModuleKind kind, long baseEnergyCapacity, long powerDrawEuPerTick,
        int cooldownTicks, Map<ItemStack, Long> constructionCost,
        BiConsumer<ModuleInstance, AutomatedFacility> applyBehavior, Supplier<ModuleComponent> defaultFactory) {}

    private static final Map<FacilityModuleKind, Definition> DEFINITIONS = new EnumMap<>(FacilityModuleKind.class);

    public static void init() {
        register(
            FacilityModuleKind.POWER,
            1500L,
            -ModulePower.EU_TICK,
            1,
            Map.of(new ItemStack(Items.redstone), 8L, new ItemStack(Items.gold_ingot), 64L),
            ModulePower::doNothing,
            ModulePower::new);
        register(
            FacilityModuleKind.MINER,
            2000L,
            128L,
            20,
            Map.of(new ItemStack(Items.diamond), 8L, new ItemStack(Items.gold_ingot), 64L),
            ModuleMiner::generateOre,
            () -> new ModuleMiner(FacilityModuleKind.MINER, new ArrayList<>(), false));
        register(
            FacilityModuleKind.HAMMER,
            1000L,
            10L,
            20,
            Map.of(new ItemStack(Items.iron_ingot), 8L, new ItemStack(Items.gold_ingot), 64L),
            ModuleHammer::prepareToFire,
            () -> new ModuleHammer(
                FacilityModuleKind.HAMMER,
                AllowShootingConfig.ALWAYS,
                OrbitalTransferPlanner.RoutePriority.PRIORITIZE_TOF,
                false,
                true,
                false,
                64));
        register(
            FacilityModuleKind.BIG_HAMMER,
            5000L,
            25L,
            20,
            Map.of(new ItemStack(Items.diamond), 8L, new ItemStack(Items.gold_ingot), 64L),
            ModuleHammer::prepareToFire,
            () -> new ModuleHammer(
                FacilityModuleKind.BIG_HAMMER,
                AllowShootingConfig.ALWAYS,
                OrbitalTransferPlanner.RoutePriority.PRIORITIZE_TOF,
                false,
                false,
                true,
                128));
    }

    public static void register(FacilityModuleKind kind, long baseEnergyCapacity, long powerDrawPerClick,
        int cooldownTicks, Map<ItemStack, Long> constructionCost,
        BiConsumer<ModuleInstance, AutomatedFacility> tickFunction, Supplier<ModuleComponent> defaultFactory) {
        DEFINITIONS.put(
            kind,
            new Definition(
                kind,
                baseEnergyCapacity,
                powerDrawPerClick,
                cooldownTicks,
                constructionCost,
                tickFunction,
                defaultFactory));
    }

    public static Definition get(FacilityModuleKind kind) {
        return DEFINITIONS.get(kind);
    }

    public static ModuleInstance createInstance(FacilityModuleKind kind) {
        return createInstance(null, kind, null);
    }

    public static ModuleInstance createInstance(ModuleInstance.ID moduleId, FacilityModuleKind kind) {
        return createInstance(moduleId, kind, null);
    }

    public static ModuleInstance createInstance(ModuleInstance.ID moduleId, FacilityModuleKind kind,
        ModuleComponent component) {
        Definition def = get(kind);
        if (def == null) {
            throw new IllegalArgumentException("Unknown module kind: " + kind);
        }
        ModuleInstance instance;
        if (moduleId == null) {
            instance = new ModuleInstance(def);
        } else {
            instance = new ModuleInstance(moduleId, def);
        }

        if (component != null) {
            instance.setComponent(component);
        } else {
            instance.setComponent(createDefaultComponent(kind));
        }
        return instance;
    }

    private static ModuleComponent createDefaultComponent(FacilityModuleKind kind) {
        return get(kind).defaultFactory.get();
    }

    public static boolean isRegistered(FacilityModuleKind kind) {
        return DEFINITIONS.containsKey(kind);
    }
}
