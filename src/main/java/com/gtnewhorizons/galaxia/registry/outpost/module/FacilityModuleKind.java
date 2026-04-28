package com.gtnewhorizons.galaxia.registry.outpost.module;

import net.minecraft.util.StatCollector;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationModuleCategory;

public enum FacilityModuleKind {

    HAMMER,
    BIG_HAMMER,
    MINER,
    POWER;

    public String getDisplayName() {
        return StatCollector.translateToLocal(
            "galaxia.outpost.module." + this.name()
                .toLowerCase());
    }

    public StationModuleCategory getCategory() {
        return switch (this) {
            case HAMMER, BIG_HAMMER -> StationModuleCategory.LOGISTICS;
            case MINER -> StationModuleCategory.MINING_SUPPORT;
            case POWER -> StationModuleCategory.POWER;
        };
    }

    public boolean isAllowedOn(CelestialAsset.Kind assetKind) {
        if (assetKind != CelestialAsset.Kind.AUTOMATED_OUTPOST && assetKind != CelestialAsset.Kind.AUTOMATED_STATION)
            return false;
        return this != MINER || assetKind == CelestialAsset.Kind.AUTOMATED_OUTPOST;
    }

    public ModuleInstance createInstance() {
        return FacilityModuleRegistry.createInstance(this);
    }

    public ModuleInstance createInstance(ModuleInstance.ID id) {
        return FacilityModuleRegistry.createInstance(id, this);
    }

    public ModuleInstance createInstance(ModuleComponent component) {
        return FacilityModuleRegistry.createInstance(null, this, component);
    }
}
