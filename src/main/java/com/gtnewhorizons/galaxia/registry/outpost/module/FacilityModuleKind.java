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
    POWER;

    private static final EnumSet<FacilityModuleKind> CAPACITY_KINDS = EnumSet.noneOf(FacilityModuleKind.class);

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
        };
    }

    public boolean isAllowedOn(CelestialAsset.Kind assetKind) {
        if (assetKind != CelestialAsset.Kind.AUTOMATED_OUTPOST && assetKind != CelestialAsset.Kind.AUTOMATED_STATION)
            return false;
        return this != MINER || assetKind == CelestialAsset.Kind.AUTOMATED_OUTPOST;
    }

    public ModuleInstance create(StationTileCoord anchor, ModuleShape shape, ModuleTier tier) {
        FacilityModuleRegistry.Definition def = FacilityModuleRegistry.get(this);
        if (def == null) {
            throw new IllegalArgumentException("Unknown module kind: " + this);
        }
        ModuleInstance instance = new ModuleInstance(ModuleInstance.ID.create(), def, anchor, shape, tier);
        instance.setComponent(FacilityModuleRegistry.createComponent(this));
        return instance;
    }

    public EnumSet<ModuleTier> allowedTiers() {
        return switch (this) {
            case HAMMER, MINER -> EnumSet.of(ModuleTier.EV, ModuleTier.IV, ModuleTier.LuV);
            case POWER -> EnumSet.of(ModuleTier.NONE);
        };
    }

    public ModuleTier defaultTier() {
        return switch (this) {
            case HAMMER, MINER -> ModuleTier.EV;
            case POWER -> ModuleTier.NONE;
        };
    }

    public ModulePriority defaultPriority() {
        return switch (this) {
            case HAMMER -> ModulePriority.NORMAL;
            case MINER -> ModulePriority.NORMAL;
            case POWER -> ModulePriority.HIGH;
        };
    }

    public boolean isCapacityModule() {
        return CAPACITY_KINDS.contains(this);
    }
}
