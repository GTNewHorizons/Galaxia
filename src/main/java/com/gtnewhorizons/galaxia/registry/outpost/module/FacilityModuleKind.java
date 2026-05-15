package com.gtnewhorizons.galaxia.registry.outpost.module;

import java.util.EnumSet;

import net.minecraft.util.StatCollector;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationModuleCategory;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

public enum FacilityModuleKind {

    HAMMER,
    MINER,
    POWER,
    STORAGE,
    TANK,
    BATTERY,
    MAINTENANCE_BAY,
    MACERATOR,
    CENTRIFUGE,
    ELECTROLYZER,
    CHEMICAL_REACTOR,
    ASSEMBLER,
    DISTILLERY;

    private static final EnumSet<FacilityModuleKind> CAPACITY_KINDS = EnumSet.noneOf(FacilityModuleKind.class);

    static {
        CAPACITY_KINDS.add(STORAGE);
        CAPACITY_KINDS.add(TANK);
        CAPACITY_KINDS.add(BATTERY);
    }

    public String getDisplayName() {
        return StatCollector.translateToLocal(
            "galaxia.outpost.module." + this.name()
                .toLowerCase());
    }

    public StationModuleCategory getCategory() {
        return switch (this) {
            case HAMMER -> StationModuleCategory.LOGISTICS;
            case MINER -> StationModuleCategory.MINING_SUPPORT;
            case POWER -> StationModuleCategory.POWER;
            case STORAGE, TANK, BATTERY -> StationModuleCategory.INFRASTRUCTURE;
            case MAINTENANCE_BAY -> StationModuleCategory.SUPPORT;
            case MACERATOR, CENTRIFUGE, ELECTROLYZER, CHEMICAL_REACTOR, ASSEMBLER, DISTILLERY -> StationModuleCategory.PROCESSING;
        };
    }

    private static boolean gt5Available = true; // default for tests; overridden during mod init

    /** Called once during mod initialization. */
    public static void setGt5Available(boolean available) {
        gt5Available = available;
    }

    public boolean isAllowedOn(CelestialAsset.Kind assetKind) {
        if (assetKind != CelestialAsset.Kind.AUTOMATED_OUTPOST && assetKind != CelestialAsset.Kind.AUTOMATED_STATION)
            return false;
        if (!isAvailable()) return false;
        return this != MINER || assetKind == CelestialAsset.Kind.AUTOMATED_OUTPOST;
    }

    /** Production modules require GT5. Returns false for those kinds when GT5 is absent. */
    public boolean isAvailable() {
        return switch (this) {
            case MACERATOR, CENTRIFUGE, ELECTROLYZER, CHEMICAL_REACTOR, ASSEMBLER, DISTILLERY -> gt5Available;
            default -> true;
        };
    }

    public ModuleInstance create(StationTileCoord anchor, ModuleShape shape, ModuleTier tier) {
        FacilityModuleRegistry.Definition def = FacilityModuleRegistry.get(this);
        if (def == null) {
            throw new IllegalArgumentException("Unknown module kind: " + this);
        }
        if (shape == null) {
            throw new IllegalArgumentException("FacilityModuleKind.create: shape must not be null for kind " + this);
        }
        if (tier == null) {
            throw new IllegalArgumentException("FacilityModuleKind.create: tier must not be null for kind " + this);
        }
        ModuleInstance instance = new ModuleInstance(ModuleInstance.ID.create(), def, anchor, shape, tier);
        instance.setComponent(FacilityModuleRegistry.createComponent(this));
        return instance;
    }

    public EnumSet<ModuleTier> allowedTiers() {
        return switch (this) {
            case HAMMER -> EnumSet.of(ModuleTier.EV, ModuleTier.IV, ModuleTier.LuV, ModuleTier.ZPM, ModuleTier.UV);
            case MINER -> EnumSet.of(ModuleTier.EV, ModuleTier.IV, ModuleTier.LuV);
            case POWER -> EnumSet.of(ModuleTier.NONE);
            case STORAGE, TANK, BATTERY -> EnumSet.of(ModuleTier.HV, ModuleTier.EV, ModuleTier.IV);
            case MAINTENANCE_BAY -> EnumSet.of(ModuleTier.NONE);
            case MACERATOR, CENTRIFUGE, ELECTROLYZER, CHEMICAL_REACTOR, ASSEMBLER, DISTILLERY -> EnumSet
                .of(ModuleTier.HV, ModuleTier.EV, ModuleTier.IV);
        };
    }

    public ModuleTier defaultTier() {
        return switch (this) {
            case HAMMER, MINER -> ModuleTier.EV;
            case POWER -> ModuleTier.NONE;
            case STORAGE, TANK, BATTERY -> ModuleTier.HV;
            case MAINTENANCE_BAY -> ModuleTier.NONE;
            case MACERATOR, CENTRIFUGE, ELECTROLYZER, CHEMICAL_REACTOR, ASSEMBLER, DISTILLERY -> ModuleTier.HV;
        };
    }

    public ModuleShape defaultShape() {
        return switch (this) {
            case MINER -> ModuleShape.QUAD_2x2;
            default -> ModuleShape.SINGLE;
        };
    }

    public ModulePriority defaultPriority() {
        return switch (this) {
            case HAMMER, MINER -> ModulePriority.NORMAL;
            case POWER -> ModulePriority.HIGH;
            case STORAGE, TANK, BATTERY -> ModulePriority.NORMAL;
            case MAINTENANCE_BAY, MACERATOR, CENTRIFUGE, ELECTROLYZER, CHEMICAL_REACTOR, ASSEMBLER, DISTILLERY -> ModulePriority.NORMAL;
        };
    }

    public boolean isCapacityModule() {
        return CAPACITY_KINDS.contains(this);
    }

    public boolean isProductionModule() {
        return switch (this) {
            case MACERATOR, CENTRIFUGE, ELECTROLYZER, CHEMICAL_REACTOR, ASSEMBLER, DISTILLERY -> true;
            default -> false;
        };
    }

    public boolean isDirectlyConfigurable() {
        return this == HAMMER || this == MINER || this == POWER || isProductionModule();
    }
}
