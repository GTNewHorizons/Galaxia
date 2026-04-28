package com.gtnewhorizons.galaxia.registry.outpost.module;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import net.minecraft.util.StatCollector;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationModuleCategory;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

public enum FacilityModuleKind {

    HAMMER,
    MINER,
    POWER;

    private static final Set<FacilityModuleKind> CAPACITY_KINDS = Collections
        .unmodifiableSet(EnumSet.noneOf(FacilityModuleKind.class));

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
        ModuleInstance instance = FacilityModuleRegistry.create(this);
        instance.setAnchor(anchor);
        instance.setShape(shape);
        instance.setTier(tier);
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
        return false;
    }

    public static Set<FacilityModuleKind> capacityKinds() {
        return CAPACITY_KINDS;
    }
}
