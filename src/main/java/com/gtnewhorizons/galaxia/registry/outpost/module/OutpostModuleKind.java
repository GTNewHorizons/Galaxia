package com.gtnewhorizons.galaxia.registry.outpost.module;

import net.minecraft.util.StatCollector;

import com.gtnewhorizons.galaxia.registry.outpost.station.StationModuleCategory;

public enum OutpostModuleKind {

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
            case HAMMER, BIG_HAMMER, MINER -> StationModuleCategory.MINING_SUPPORT;
            case POWER -> StationModuleCategory.POWER;
        };
    }

    public boolean isMiningCapability() {
        return this == HAMMER || this == BIG_HAMMER || this == MINER;
    }

    public static AutomatedOutpostModule forKind(OutpostModuleKind kind) {
        return switch (kind) {
            case HAMMER -> ModuleHammer.getDefault();
            case BIG_HAMMER -> ModuleBigHammer.getDefault();
            case MINER -> ModuleMiner.getDefault();
            case POWER -> ModulePower.getDefault();
        };
    }
}
