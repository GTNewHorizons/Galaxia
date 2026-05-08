package com.gtnewhorizons.galaxia.registry.outpost.module;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import com.gtnewhorizons.galaxia.registry.interfaces.IModuleComponent;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalTransferPlanner;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.AllowShootingConfig;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleAssembler;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleBattery;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleCentrifuge;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleChemicalReactor;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleDistillery;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleElectrolyzer;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleHammer;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleMacerator;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleMaintenanceBay;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleMiner;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModulePower;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleStorage;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleTank;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

public class FacilityModuleRegistry {

    public record Definition(FacilityModuleKind kind, Map<ModuleTier, ModuleTierData> tierData,
        BiConsumer<ModuleInstance, AutomatedFacility> applyBehavior, Supplier<IModuleComponent> defaultFactory) {

        public Definition {
            if (tierData == null || tierData.isEmpty()) {
                throw new IllegalArgumentException("Definition: tierData must not be null or empty");
            }
            Map<ModuleTier, ModuleTierData> copiedTiers = new EnumMap<>(ModuleTier.class);
            copiedTiers.putAll(tierData);
            tierData = Collections.unmodifiableMap(copiedTiers);
        }

        public ModuleTierData getTierData(ModuleTier tier) {
            ModuleTierData data = tierData.get(tier);
            if (data == null) {
                throw new IllegalStateException("No tier data for kind=" + kind + ", tier=" + tier);
            }
            return data;
        }
    }

    private static final Map<FacilityModuleKind, Definition> DEFINITIONS = new EnumMap<>(FacilityModuleKind.class);

    public static void init() {
        register(
            FacilityModuleKind.POWER,
            ModuleTierData.builder()
                .addedEnergyCapacity(1500L)
                .powerDraw(-ModulePower.EU_TICK)
                .cooldown(1)
                .cost(Map.of(new ItemStack(Items.redstone), 8L, new ItemStack(Items.gold_ingot), 64L))
                .build(),
            ModulePower::doNothing,
            ModulePower::new);
        register(
            FacilityModuleKind.MINER,
            new TierMapBuilder()
                .add(
                    ModuleTier.EV,
                    2000L,
                    128L,
                    20,
                    Map.of(new ItemStack(Items.diamond), 8L, new ItemStack(Items.gold_ingot), 64L))
                .add(
                    ModuleTier.IV,
                    8000L,
                    512L,
                    20,
                    Map.of(new ItemStack(Items.diamond), 32L, new ItemStack(Items.gold_ingot), 256L))
                .add(
                    ModuleTier.LuV,
                    32000L,
                    2048L,
                    20,
                    Map.of(new ItemStack(Items.diamond), 128L, new ItemStack(Items.gold_ingot), 1024L))
                .build(),
            ModuleMiner::generateOre,
            () -> new ModuleMiner(FacilityModuleKind.MINER));
        register(
            FacilityModuleKind.HAMMER,
            new TierMapBuilder()
                .add(
                    ModuleTier.EV,
                    1000L,
                    0L,
                    1200,
                    Map.of(HammerVariant.BASE.name(), 1200),
                    Map.of(new ItemStack(Items.iron_ingot), 8L, new ItemStack(Items.gold_ingot), 64L))
                .add(
                    ModuleTier.IV,
                    4000L,
                    0L,
                    900,
                    Map.of(HammerVariant.BASE.name(), 900),
                    Map.of(new ItemStack(Items.iron_ingot), 32L, new ItemStack(Items.gold_ingot), 256L))
                .add(
                    ModuleTier.LuV,
                    16000L,
                    0L,
                    600,
                    Map.of(HammerVariant.BASE.name(), 600, HammerVariant.BIG.name(), 1200),
                    Map.of(new ItemStack(Items.iron_ingot), 128L, new ItemStack(Items.gold_ingot), 1024L))
                .add(
                    ModuleTier.ZPM,
                    64000L,
                    0L,
                    900,
                    Map.of(HammerVariant.BIG.name(), 900),
                    Map.of(new ItemStack(Items.iron_ingot), 512L, new ItemStack(Items.gold_ingot), 4096L))
                .add(
                    ModuleTier.UV,
                    256000L,
                    0L,
                    600,
                    Map.of(HammerVariant.BIG.name(), 600),
                    Map.of(new ItemStack(Items.iron_ingot), 2048L, new ItemStack(Items.gold_ingot), 16384L))
                .build(),
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
            new TierMapBuilder()
                .add(
                    ModuleTier.HV,
                    500L,
                    0L,
                    1,
                    1024L,
                    Map.of(new ItemStack(Items.iron_ingot), 16L, new ItemStack(Items.gold_ingot), 32L))
                .add(
                    ModuleTier.EV,
                    2000L,
                    0L,
                    1,
                    4096L,
                    Map.of(new ItemStack(Items.iron_ingot), 64L, new ItemStack(Items.gold_ingot), 128L))
                .add(
                    ModuleTier.IV,
                    8000L,
                    0L,
                    1,
                    16384L,
                    Map.of(new ItemStack(Items.iron_ingot), 256L, new ItemStack(Items.gold_ingot), 512L))
                .build(),
            (instance, outpost) -> {},
            ModuleStorage::new);
        register(
            FacilityModuleKind.TANK,
            new TierMapBuilder()
                .add(
                    ModuleTier.HV,
                    500L,
                    0L,
                    1,
                    16_000L,
                    Map.of(new ItemStack(Items.iron_ingot), 16L, new ItemStack(Items.gold_ingot), 32L))
                .add(
                    ModuleTier.EV,
                    2000L,
                    0L,
                    1,
                    64_000L,
                    Map.of(new ItemStack(Items.iron_ingot), 64L, new ItemStack(Items.gold_ingot), 128L))
                .add(
                    ModuleTier.IV,
                    8000L,
                    0L,
                    1,
                    256_000L,
                    Map.of(new ItemStack(Items.iron_ingot), 256L, new ItemStack(Items.gold_ingot), 512L))
                .build(),
            (instance, outpost) -> {},
            ModuleTank::new);
        register(
            FacilityModuleKind.BATTERY,
            new TierMapBuilder()
                .add(
                    ModuleTier.HV,
                    500L,
                    0L,
                    1,
                    100_000L,
                    Map.of(new ItemStack(Items.redstone), 16L, new ItemStack(Items.gold_ingot), 32L))
                .add(
                    ModuleTier.EV,
                    2000L,
                    0L,
                    1,
                    400_000L,
                    Map.of(new ItemStack(Items.redstone), 64L, new ItemStack(Items.gold_ingot), 128L))
                .add(
                    ModuleTier.IV,
                    8000L,
                    0L,
                    1,
                    1_600_000L,
                    Map.of(new ItemStack(Items.redstone), 256L, new ItemStack(Items.gold_ingot), 512L))
                .build(),
            (instance, outpost) -> {},
            ModuleBattery::new);
        register(
            FacilityModuleKind.MAINTENANCE_BAY,
            ModuleTierData.builder()
                .addedEnergyCapacity(500L)
                .powerDraw(0L)
                .cooldown(100)
                .cost(Map.of(new ItemStack(Items.iron_ingot), 8L, new ItemStack(Items.gold_ingot), 16L))
                .build(),
            (instance, outpost) -> {},
            ModuleMaintenanceBay::new);

        if (FacilityModuleKind.MACERATOR.isAvailable()) {
            register(
                FacilityModuleKind.MACERATOR,
                new TierMapBuilder().add(ModuleTier.HV, 2000L, 32L, 20, Map.of(new ItemStack(Items.iron_ingot), 8L))
                    .add(ModuleTier.EV, 8000L, 128L, 20, Map.of(new ItemStack(Items.iron_ingot), 32L))
                    .add(ModuleTier.IV, 32000L, 512L, 20, Map.of(new ItemStack(Items.iron_ingot), 128L))
                    .build(),
                ModuleMacerator::processRecipe,
                ModuleMacerator::new);
            register(
                FacilityModuleKind.CENTRIFUGE,
                new TierMapBuilder().add(ModuleTier.HV, 2000L, 32L, 20, Map.of(new ItemStack(Items.iron_ingot), 8L))
                    .add(ModuleTier.EV, 8000L, 128L, 20, Map.of(new ItemStack(Items.iron_ingot), 32L))
                    .add(ModuleTier.IV, 32000L, 512L, 20, Map.of(new ItemStack(Items.iron_ingot), 128L))
                    .build(),
                ModuleCentrifuge::processRecipe,
                ModuleCentrifuge::new);
            register(
                FacilityModuleKind.ELECTROLYZER,
                new TierMapBuilder().add(ModuleTier.HV, 2000L, 32L, 20, Map.of(new ItemStack(Items.iron_ingot), 8L))
                    .add(ModuleTier.EV, 8000L, 128L, 20, Map.of(new ItemStack(Items.iron_ingot), 32L))
                    .add(ModuleTier.IV, 32000L, 512L, 20, Map.of(new ItemStack(Items.iron_ingot), 128L))
                    .build(),
                ModuleElectrolyzer::processRecipe,
                ModuleElectrolyzer::new);
            register(
                FacilityModuleKind.CHEMICAL_REACTOR,
                new TierMapBuilder().add(ModuleTier.HV, 2000L, 32L, 20, Map.of(new ItemStack(Items.iron_ingot), 8L))
                    .add(ModuleTier.EV, 8000L, 128L, 20, Map.of(new ItemStack(Items.iron_ingot), 32L))
                    .add(ModuleTier.IV, 32000L, 512L, 20, Map.of(new ItemStack(Items.iron_ingot), 128L))
                    .build(),
                ModuleChemicalReactor::processRecipe,
                ModuleChemicalReactor::new);
            register(
                FacilityModuleKind.ASSEMBLER,
                new TierMapBuilder().add(ModuleTier.HV, 2000L, 32L, 20, Map.of(new ItemStack(Items.iron_ingot), 8L))
                    .add(ModuleTier.EV, 8000L, 128L, 20, Map.of(new ItemStack(Items.iron_ingot), 32L))
                    .add(ModuleTier.IV, 32000L, 512L, 20, Map.of(new ItemStack(Items.iron_ingot), 128L))
                    .build(),
                ModuleAssembler::processRecipe,
                ModuleAssembler::new);
            register(
                FacilityModuleKind.DISTILLERY,
                new TierMapBuilder().add(ModuleTier.HV, 2000L, 32L, 20, Map.of(new ItemStack(Items.iron_ingot), 8L))
                    .add(ModuleTier.EV, 8000L, 128L, 20, Map.of(new ItemStack(Items.iron_ingot), 32L))
                    .add(ModuleTier.IV, 32000L, 512L, 20, Map.of(new ItemStack(Items.iron_ingot), 128L))
                    .build(),
                ModuleDistillery::processRecipe,
                ModuleDistillery::new);
        }
    }

    public static void register(FacilityModuleKind kind, ModuleTierData data,
        BiConsumer<ModuleInstance, AutomatedFacility> tickFunction, Supplier<IModuleComponent> defaultFactory) {
        DEFINITIONS.put(kind, new Definition(kind, Map.of(ModuleTier.NONE, data), tickFunction, defaultFactory));
    }

    public static void register(FacilityModuleKind kind, Map<ModuleTier, ModuleTierData> tierData,
        BiConsumer<ModuleInstance, AutomatedFacility> tickFunction, Supplier<IModuleComponent> defaultFactory) {
        DEFINITIONS.put(kind, new Definition(kind, tierData, tickFunction, defaultFactory));
    }

    public static Map<ItemStackWrapper, Long> operationCost(Map<ItemStack, Long> constructionCost) {
        Map<ItemStackWrapper, Long> wrapped = new java.util.LinkedHashMap<>();
        for (Map.Entry<ItemStack, Long> entry : constructionCost.entrySet()) {
            ItemStackWrapper item = ItemStackWrapper.of(entry.getKey());
            if (item == null) continue;
            wrapped.merge(item, entry.getValue(), Long::sum);
        }
        return wrapped;
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

    static IModuleComponent createComponent(FacilityModuleKind kind) {
        Definition def = get(kind);
        if (def == null) {
            throw new IllegalStateException("FacilityModuleRegistry: no definition registered for kind " + kind);
        }
        IModuleComponent component = def.defaultFactory.get();
        if (component == null) {
            throw new IllegalStateException("FacilityModuleRegistry: defaultFactory returned null for kind " + kind);
        }
        return component;
    }

    public static boolean isRegistered(FacilityModuleKind kind) {
        return DEFINITIONS.containsKey(kind);
    }

    public static class TierMapBuilder {

        private final EnumMap<ModuleTier, ModuleTierData> map = new EnumMap<>(ModuleTier.class);

        public TierMapBuilder add(ModuleTier tier, long energy, long power, int cooldown, Map<ItemStack, Long> cost) {
            if (map.put(
                tier,
                ModuleTierData.builder()
                    .addedEnergyCapacity(energy)
                    .powerDraw(power)
                    .cooldown(cooldown)
                    .cost(cost)
                    .build())
                != null) {
                throw new IllegalArgumentException("Duplicate tier entry: " + tier);
            }
            return this;
        }

        public TierMapBuilder add(ModuleTier tier, long energy, long power, int cooldown, long capacity,
            Map<ItemStack, Long> cost) {
            if (map.put(
                tier,
                ModuleTierData.builder()
                    .addedEnergyCapacity(energy)
                    .powerDraw(power)
                    .cooldown(cooldown)
                    .capacity(capacity)
                    .cost(cost)
                    .build())
                != null) {
                throw new IllegalArgumentException("Duplicate tier entry: " + tier);
            }
            return this;
        }

        public TierMapBuilder add(ModuleTier tier, long energy, long power, int cooldown,
            Map<String, Integer> variantCooldowns, Map<ItemStack, Long> cost) {
            if (map.put(
                tier,
                ModuleTierData.builder()
                    .addedEnergyCapacity(energy)
                    .powerDraw(power)
                    .cooldown(cooldown)
                    .variantCooldowns(variantCooldowns)
                    .cost(cost)
                    .build())
                != null) {
                throw new IllegalArgumentException("Duplicate tier entry: " + tier);
            }
            return this;
        }

        public Map<ModuleTier, ModuleTierData> build() {
            if (map.isEmpty()) {
                throw new IllegalStateException("No tiers added to builder");
            }
            return Collections.unmodifiableMap(new EnumMap<>(map));
        }
    }
}
