package com.gtnewhorizons.galaxia.registry.outpost.module;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import com.gtnewhorizons.galaxia.registry.orbital.OrbitalTransferPlanner;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.AllowShootingConfig;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

public class FacilityModuleRegistry {

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
            () -> new ModuleMiner(FacilityModuleKind.MINER));
        register(
            FacilityModuleKind.HAMMER,
            1000L,
            0L,
            20,
            Map.of(new ItemStack(Items.iron_ingot), 8L, new ItemStack(Items.gold_ingot), 64L),
            ModuleHammer::prepareToFire,
            () -> new ModuleHammer(
                FacilityModuleKind.HAMMER,
                AllowShootingConfig.ALWAYS,
                OrbitalTransferPlanner.RoutePriority.PRIORITIZE_TOF,
                false,
                HammerVariant.BASE,
                64));
        register(
            FacilityModuleKind.STORAGE,
            500L,
            0L,
            1,
            Map.of(new ItemStack(Items.iron_ingot), 16L, new ItemStack(Items.gold_ingot), 32L),
            (instance, outpost) -> {},
            ModuleStorage::new);
        register(
            FacilityModuleKind.TANK,
            500L,
            0L,
            1,
            Map.of(new ItemStack(Items.iron_ingot), 16L, new ItemStack(Items.gold_ingot), 32L),
            (instance, outpost) -> {},
            ModuleTank::new);
        register(
            FacilityModuleKind.BATTERY,
            500L,
            0L,
            1,
            Map.of(new ItemStack(Items.redstone), 16L, new ItemStack(Items.gold_ingot), 32L),
            (instance, outpost) -> {},
            ModuleBattery::new);
        register(
            FacilityModuleKind.MAINTENANCE_BAY,
            500L,
            0L,
            100,
            Map.of(new ItemStack(Items.iron_ingot), 8L, new ItemStack(Items.gold_ingot), 16L),
            (instance, outpost) -> {},
            ModuleMaintenanceBay::new);

        if (FacilityModuleKind.MACERATOR.isAvailable()) {
            register(
                FacilityModuleKind.MACERATOR,
                2000L,
                32L,
                20,
                Map.of(new ItemStack(Items.iron_ingot), 8L),
                ModuleMacerator::processRecipe,
                ModuleMacerator::new);
            register(
                FacilityModuleKind.CENTRIFUGE,
                2000L,
                32L,
                20,
                Map.of(new ItemStack(Items.iron_ingot), 8L),
                ModuleCentrifuge::processRecipe,
                ModuleCentrifuge::new);
            register(
                FacilityModuleKind.ELECTROLYZER,
                2000L,
                32L,
                20,
                Map.of(new ItemStack(Items.iron_ingot), 8L),
                ModuleElectrolyzer::processRecipe,
                ModuleElectrolyzer::new);
            register(
                FacilityModuleKind.CHEMICAL_REACTOR,
                2000L,
                32L,
                20,
                Map.of(new ItemStack(Items.iron_ingot), 8L),
                ModuleChemicalReactor::processRecipe,
                ModuleChemicalReactor::new);
            register(
                FacilityModuleKind.ASSEMBLER,
                2000L,
                32L,
                20,
                Map.of(new ItemStack(Items.iron_ingot), 8L),
                ModuleAssembler::processRecipe,
                ModuleAssembler::new);
            register(
                FacilityModuleKind.DISTILLERY,
                2000L,
                32L,
                20,
                Map.of(new ItemStack(Items.iron_ingot), 8L),
                ModuleDistillery::processRecipe,
                ModuleDistillery::new);
        }
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

    public static ModuleInstance create(ModuleInstance.ID moduleId, FacilityModuleKind kind, StationTileCoord anchor,
        ModuleShape shape, ModuleTier tier) {
        Definition def = get(kind);
        if (def == null) {
            throw new IllegalStateException(
                "FacilityModuleRegistry: no definition registered for kind " + kind
                    + " — FacilityModuleRegistry.init() must be called before module creation");
        }
        if (shape == null) {
            throw new IllegalArgumentException("FacilityModuleRegistry: shape must not be null for kind " + kind);
        }
        if (tier == null) {
            throw new IllegalArgumentException("FacilityModuleRegistry: tier must not be null for kind " + kind);
        }
        ModuleInstance instance = new ModuleInstance(moduleId, def, anchor, shape, tier);
        instance.setComponent(createComponent(kind));
        return instance;
    }

    static ModuleComponent createComponent(FacilityModuleKind kind) {
        Definition def = get(kind);
        if (def == null) {
            throw new IllegalStateException("FacilityModuleRegistry: no definition registered for kind " + kind);
        }
        ModuleComponent component = def.defaultFactory.get();
        if (component == null) {
            throw new IllegalStateException("FacilityModuleRegistry: defaultFactory returned null for kind " + kind);
        }
        return component;
    }

    public static boolean isRegistered(FacilityModuleKind kind) {
        return DEFINITIONS.containsKey(kind);
    }
}
