package com.gtnewhorizons.galaxia.registry.outpost.module;

import net.minecraft.util.StatCollector;

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

    public ModuleInstance createInstance() {
        return OutpostModuleRegistry.createInstance(this);
    }

    public ModuleInstance createInstance(ModuleInstance.ID id) {
        return OutpostModuleRegistry.createInstance(id, this);
    }

    public ModuleInstance createInstance(ModuleComponent component) {
        return OutpostModuleRegistry.createInstance(null, this, component);
    }
}
