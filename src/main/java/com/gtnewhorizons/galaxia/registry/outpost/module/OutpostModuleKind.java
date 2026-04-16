package com.gtnewhorizons.galaxia.registry.outpost.module;

import net.minecraft.util.StatCollector;

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

    public static AutomatedOutpostModule forKind(OutpostModuleKind kind) {
        return switch (kind) {
            case HAMMER -> ModuleHammer.getDefault();
            case BIG_HAMMER -> ModuleBigHammer.getDefault();
            case MINER -> ModuleMiner.getDefault();
            case POWER -> ModulePower.getDefault();
        };
    }
}
